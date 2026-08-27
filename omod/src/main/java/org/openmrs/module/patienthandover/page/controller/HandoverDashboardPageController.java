package org.openmrs.module.patienthandover.page.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.PatientHandoverTask;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class HandoverDashboardPageController {
	
	private static final String OVERDUE_HOURS_PROPERTY = "patienthandover.overdueHours";
	
	public void controller(@RequestParam(value = "careSetting", defaultValue = "ALL") String careSetting,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "PENDING") String status,
	        @RequestParam(value = "assignedToMe", defaultValue = "false") boolean assignedToMe,
	        @RequestParam(value = "fromDate", defaultValue = "") String fromDate,
	        @RequestParam(value = "toDate", defaultValue = "") String toDate,
	        @RequestParam(value = "page", defaultValue = "1") int page, PageModel model, UiSessionContext sessionContext,
	        @SpringBean PatienthandoverService patienthandoverService) {
		
		// Require view privilege
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		
		// Get current location
		Location location = sessionContext.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("careSetting", careSetting);
		model.addAttribute("shift", shift);
		model.addAttribute("status", status);
		model.addAttribute("assignedToMe", assignedToMe);
		model.addAttribute("fromDate", fromDate);
		model.addAttribute("toDate", toDate);
		
		// Validate location
		if (location == null) {
			model.addAttribute("dashboardError", "Please select a login location before opening the dashboard.");
			return;
		}
		
		// Validate filters
		if (!isValidCareSetting(careSetting) || !isValidShift(shift) || !isValidStatus(status)) {
			model.addAttribute("dashboardError", "Invalid dashboard filter.");
			return;
		}
		
		// Parse date range
		Date rangeStart;
		Date rangeEnd;
		try {
			rangeStart = parseDate(fromDate, false);
			rangeEnd = parseDate(toDate, true);
			if (rangeStart != null && rangeEnd != null && !rangeStart.before(rangeEnd)) {
				throw new ParseException("Invalid range", 0);
			}
		}
		catch (ParseException e) {
			model.addAttribute("dashboardError", "Select a valid date range.");
			return;
		}
		
		// Get overdue hours from global property
		int overdueHours = getOverdueHours();
		long now = System.currentTimeMillis();
		
		// Fetch handovers by care setting
		List<PatientHandover> handovers = new ArrayList<PatientHandover>();
		if ("ALL".equals(careSetting) || "IPD".equals(careSetting)) {
			handovers.addAll(patienthandoverService.getRecentHandoversByLocation(location, "IPD", 500));
		}
		if ("ALL".equals(careSetting) || "OPD".equals(careSetting)) {
			handovers.addAll(patienthandoverService.getRecentHandoversByLocation(location, "OPD", 500));
		}
		
		// Filter by date range
		List<PatientHandover> dateFiltered = new ArrayList<PatientHandover>();
		for (PatientHandover handover : handovers) {
			Date created = handover.getDateCreated();
			if (rangeStart != null && created.before(rangeStart)) {
				continue;
			}
			if (rangeEnd != null && !created.before(rangeEnd)) {
				continue;
			}
			dateFiltered.add(handover);
		}
		handovers = dateFiltered;
		
		// Sort by date (newest first)
		Collections.sort(handovers, new Comparator<PatientHandover>() {
			
			@Override
			public int compare(PatientHandover left, PatientHandover right) {
				return right.getDateCreated().compareTo(left.getDateCreated());
			}
		});
		
		// Get authenticated user
		User authenticatedUser = Context.getAuthenticatedUser();
		
		// Count my incoming batches
		int myIncomingBatchCount = 0;
		for (List<PatientHandover> batch : groupByBatch(handovers).values()) {
			if (!batch.isEmpty() && !batch.get(0).isAcknowledged() && !batch.get(0).isCancelled()
			        && isAssignedToUser(batch.get(0), authenticatedUser)) {
				myIncomingBatchCount++;
			}
		}
		model.addAttribute("myIncomingBatchCount", myIncomingBatchCount);
		model.addAttribute("hasProviderIdentity", authenticatedUser != null && authenticatedUser.getPerson() != null);
		
		// Filter by provider permissions
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_ALL_PROVIDERS)) {
			List<PatientHandover> permitted = new ArrayList<PatientHandover>();
			for (PatientHandover handover : handovers) {
				boolean createdByUser = authenticatedUser != null && handover.getCreator() != null
				        && authenticatedUser.getUserId().equals(handover.getCreator().getUserId());
				if (createdByUser || isAssignedToUser(handover, authenticatedUser)) {
					permitted.add(handover);
				}
			}
			handovers = permitted;
		}
		
		// Filter by "assigned to me"
		if (assignedToMe) {
			List<PatientHandover> mine = new ArrayList<PatientHandover>();
			for (PatientHandover handover : handovers) {
				if (isAssignedToUser(handover, authenticatedUser)) {
					mine.add(handover);
				}
			}
			handovers = mine;
		}
		
		// Apply remaining filters and calculate statistics
		List<PatientHandover> filtered = new ArrayList<PatientHandover>();
		int pendingPatients = 0;
		int criticalPatients = 0;
		int overdueTaskCount = 0;
		
		for (PatientHandover handover : handovers) {
			// Filter by shift
			if (!"ALL".equals(shift) && !shift.equals(handover.getShift())) {
				continue;
			}
			
			// Count overdue tasks
			for (PatientHandoverTask task : handover.getTasks()) {
				if (!task.isCompleted() && task.getDueDate() != null && task.getDueDate().getTime() < now) {
					overdueTaskCount++;
				}
			}
			
			// Check if overdue
			boolean overdue = isOverdue(handover, overdueHours, now);
			
			// Count pending and critical patients
			if (!handover.isAcknowledged() && !handover.isCancelled()) {
				pendingPatients++;
				if ("CRITICAL".equals(handover.getPriority())) {
					criticalPatients++;
				}
			}
			
			// Filter by status
			if ("PENDING".equals(status) && !"PENDING".equals(handover.getStatus())) {
				continue;
			}
			if ("RECEIVED".equals(status) && !"RECEIVED".equals(handover.getStatus())) {
				continue;
			}
			if ("CANCELLED".equals(status) && !"CANCELLED".equals(handover.getStatus())) {
				continue;
			}
			if ("OVERDUE".equals(status) && !overdue) {
				continue;
			}
			
			filtered.add(handover);
		}
		
		// Group by batch and calculate batch statistics
		Map<String, List<PatientHandover>> allBatches = groupByBatch(handovers);
		int pendingBatches = 0;
		int overdueBatches = 0;
		
		for (List<PatientHandover> batch : allBatches.values()) {
			if (batch.isEmpty() || (!"ALL".equals(shift) && !shift.equals(batch.get(0).getShift()))) {
				continue;
			}
			PatientHandover first = batch.get(0);
			if (!first.isAcknowledged() && !first.isCancelled()) {
				pendingBatches++;
			}
			if (!first.isCancelled() && isOverdue(first, overdueHours, now)) {
				overdueBatches++;
			}
		}
		
		// Prepare displayed batches
		Map<String, List<PatientHandover>> displayedBatches = groupByBatch(filtered);
		Map<String, Long> batchAgeHours = new LinkedHashMap<String, Long>();
		Map<String, Boolean> overdueByBatch = new LinkedHashMap<String, Boolean>();
		
		for (Map.Entry<String, List<PatientHandover>> entry : displayedBatches.entrySet()) {
			PatientHandover first = entry.getValue().get(0);
			long age = Math.max(0L, (now - first.getDateCreated().getTime()) / (60L * 60L * 1000L));
			batchAgeHours.put(entry.getKey(), age);
			overdueByBatch.put(entry.getKey(), isOverdue(first, overdueHours, now));
		}
		
		// Add all attributes to model
		model.addAttribute("batches", PaginationSupport.map(displayedBatches, page, model, "batch"));
		model.addAttribute("batchAgeHours", batchAgeHours);
		model.addAttribute("overdueByBatch", overdueByBatch);
		model.addAttribute("overdueHours", overdueHours);
		model.addAttribute("pendingBatchCount", pendingBatches);
		model.addAttribute("overdueBatchCount", overdueBatches);
		model.addAttribute("pendingPatientCount", pendingPatients);
		model.addAttribute("criticalPatientCount", criticalPatients);
		model.addAttribute("overdueTaskCount", overdueTaskCount);
	}
	
	/**
	 * Check if a handover is overdue
	 */
	private boolean isOverdue(PatientHandover handover, int overdueHours, long now) {
		return !handover.isAcknowledged() && !handover.isCancelled() && handover.getDateCreated() != null
		        && now - handover.getDateCreated().getTime() >= overdueHours * 60L * 60L * 1000L;
	}
	
	/**
	 * Get overdue hours from global property
	 */
	private int getOverdueHours() {
		String configured = Context.getAdministrationService().getGlobalProperty(OVERDUE_HOURS_PROPERTY, "2");
		try {
			int hours = Integer.parseInt(configured);
			return hours > 0 ? hours : 2;
		}
		catch (NumberFormatException ex) {
			return 2;
		}
	}
	
	/**
	 * Group handovers by batch UUID
	 */
	private Map<String, List<PatientHandover>> groupByBatch(List<PatientHandover> handovers) {
		Map<String, List<PatientHandover>> batches = new LinkedHashMap<String, List<PatientHandover>>();
		for (PatientHandover handover : handovers) {
			String key = handover.getBatchUuid() == null || handover.getBatchUuid().trim().isEmpty() ? "single:"
			        + handover.getUuid() : handover.getBatchUuid();
			List<PatientHandover> batch = batches.get(key);
			if (batch == null) {
				batch = new ArrayList<PatientHandover>();
				batches.put(key, batch);
			}
			batch.add(handover);
		}
		return batches;
	}
	
	/**
	 * Validate care setting filter
	 */
	private boolean isValidCareSetting(String value) {
		return "ALL".equals(value) || "IPD".equals(value) || "OPD".equals(value);
	}
	
	/**
	 * Validate shift filter
	 */
	private boolean isValidShift(String value) {
		return "ALL".equals(value) || "DAY".equals(value) || "NIGHT".equals(value);
	}
	
	/**
	 * Validate status filter
	 */
	private boolean isValidStatus(String value) {
		return "ALL".equals(value) || "PENDING".equals(value) || "RECEIVED".equals(value) || "CANCELLED".equals(value)
		        || "OVERDUE".equals(value);
	}
	
	/**
	 * Check if handover is assigned to a specific user
	 */
	private boolean isAssignedToUser(PatientHandover handover, User user) {
		return handover.getReceivingProvider() != null && handover.getReceivingProvider().getPerson() != null
		        && user != null && user.getPerson() != null
		        && handover.getReceivingProvider().getPerson().getPersonId().equals(user.getPerson().getPersonId());
	}
	
	/**
	 * Parse date string
	 */
	private Date parseDate(String value, boolean endExclusive) throws ParseException {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		format.setLenient(false);
		Date parsed = format.parse(value);
		if (endExclusive) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(parsed);
			calendar.add(Calendar.DAY_OF_MONTH, 1);
			return calendar.getTime();
		}
		return parsed;
	}
}
