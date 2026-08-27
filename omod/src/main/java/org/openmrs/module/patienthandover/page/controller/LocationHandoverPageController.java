package org.openmrs.module.patienthandover.page.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.page.controller.PatientHandoverAuditSupport;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.openmrs.module.patienthandover.domain.PatientHandoverTask;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

public class LocationHandoverPageController {
	
	/**
	 * Handles GET requests: displays the worklist.
	 */
	public void get(@RequestParam(value = "careSetting", defaultValue = "IPD") String careSetting,
	        @RequestParam(value = "query", required = false) String query,
	        @RequestParam(value = "worklistQuery", required = false) String worklistQuery,
	        @RequestParam(value = "manualPatientId", required = false) Integer manualPatientId,
	        @RequestParam(value = "removePatientId", required = false) Integer removePatientId,
	        @RequestParam(value = "clearManual", defaultValue = "false") boolean clearManual,
	        @RequestParam(value = "showSaved", defaultValue = "false") boolean showSaved,
	        @RequestParam(value = "page", defaultValue = "1") int page, PageModel model, UiSessionContext sessionContext,
	        Session session) {
		
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		
		// Get services from Context
		PatientService patientService = Context.getPatientService();
		ProviderService providerService = Context.getProviderService();
		PatienthandoverService patienthandoverService = Context.getService(PatienthandoverService.class);
		
		Location location = sessionContext.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("careSetting", careSetting);
		model.addAttribute("providers", providerService.getAllProviders(false));
		
		// Move any flash-style session messages into the model
		String locationHandoverMessage = session.getAttribute("locationHandoverMessage", String.class);
		if (locationHandoverMessage != null) {
			model.addAttribute("locationHandoverMessage", locationHandoverMessage);
			session.setAttribute("locationHandoverMessage", null);
		}
		String locationHandoverError = session.getAttribute("locationHandoverError", String.class);
		if (locationHandoverError != null) {
			model.addAttribute("locationHandoverError", locationHandoverError);
			session.setAttribute("locationHandoverError", null);
		}
		
		if (location != null && isValidCareSetting(careSetting)) {
			model.addAttribute("draftStorageKey", getDraftStorageKey(location, careSetting));
		}
		
		if (location == null) {
			model.addAttribute("worklistError", "Please select a login location before opening the handover worklist.");
			return;
		}
		
		if (!isValidCareSetting(careSetting)) {
			model.addAttribute("worklistError", "Please select OPD or IPD.");
			return;
		}
		
		String sessionKey = getManualSessionKey(location, careSetting);
		String excludedSessionKey = getExcludedSessionKey(location, careSetting);
		if (clearManual) {
			session.setAttribute(sessionKey, null);
		}
		
		if (removePatientId != null) {
			getExcludedPatientIds(session, excludedSessionKey).add(removePatientId);
			getManualPatientIds(session, sessionKey).remove(removePatientId);
			session.setAttribute("locationHandoverMessage", "Patient removed from this worklist.");
		}
		
		if (manualPatientId != null) {
			Patient manualPatient = patientService.getPatient(manualPatientId);
			if (manualPatient != null && !Boolean.TRUE.equals(manualPatient.getVoided())) {
				getExcludedPatientIds(session, excludedSessionKey).remove(manualPatientId);
				getManualPatientIds(session, sessionKey).add(manualPatientId);
				session.setAttribute("locationHandoverMessage", manualPatient.getPersonName().getFullName()
				        + " added manually to this worklist.");
			}
		}
		
		if (query != null && !query.trim().isEmpty()) {
			query = query.trim();
			List<Patient> searchResults = new ArrayList<Patient>();
			for (Integer patientId : patienthandoverService.searchActivePatientIds(query, 50)) {
				Patient patient = patientService.getPatient(patientId);
				if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
					searchResults.add(patient);
				}
			}
			model.addAttribute("searchQuery", query);
			model.addAttribute("searchResults", searchResults);
		}
		
		List<Patient> worklistPatients = getPatientsForWorklist(location, careSetting, session, patientService,
		    patienthandoverService);
		if (worklistQuery != null && !worklistQuery.trim().isEmpty()) {
			worklistQuery = worklistQuery.trim();
			worklistPatients = filterPatients(worklistPatients, worklistQuery);
		}
		model.addAttribute("worklistQuery", worklistQuery);
		model.addAttribute("patients", PaginationSupport.list(worklistPatients, page, model, "patient"));
		model.addAttribute("showSaved", showSaved);
		if (showSaved) {
			List<PatientHandover> recentHandovers = patienthandoverService.getRecentHandoversByLocation(location,
			    careSetting, 500);
			model.addAttribute("recentHandoverBatches", groupHandoversByBatch(recentHandovers));
		}
	}
	
	/**
	 * Handles POST requests: saves the batch.
	 */
	public String post(HttpServletRequest request, UiSessionContext sessionContext, Session session) {
		
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		
		// Get services from Context
		PatientService patientService = Context.getPatientService();
		ProviderService providerService = Context.getProviderService();
		PatienthandoverService patienthandoverService = Context.getService(PatienthandoverService.class);
		
		Location location = sessionContext.getSessionLocation();
		
		String careSetting = request.getParameter("careSetting");
		String shift = request.getParameter("shift");
		String receivingProviderIdParam = request.getParameter("receivingProviderId");
		
		if (location == null) {
			session.setAttribute("locationHandoverError", "Please select a login location before saving handovers.");
			return "redirect:patienthandover/locationHandover.page";
		}
		
		if (!isValidCareSetting(careSetting) || (!"DAY".equals(shift) && !"NIGHT".equals(shift))) {
			session.setAttribute("locationHandoverError", "Please select a valid care setting and shift.");
			return "redirect:patienthandover/handoverDashboard.page";
		}
		
		Integer receivingProviderId;
		try {
			receivingProviderId = Integer.valueOf(receivingProviderIdParam);
		}
		catch (NumberFormatException e) {
			session.setAttribute("locationHandoverError", "Please select a valid receiving provider.");
			return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
		}
		
		Provider receivingProvider = providerService.getProvider(receivingProviderId);
		if (receivingProvider == null || Boolean.TRUE.equals(receivingProvider.getRetired())) {
			session.setAttribute("locationHandoverError", "Please select a valid receiving provider.");
			return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
		}
		
		String[] patientIdParams = request.getParameterValues("patientIds");
		if (patientIdParams == null || patientIdParams.length == 0) {
			session.setAttribute("locationHandoverError", "Select at least one patient.");
			return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
		}
		
		Map<Integer, Patient> allowedPatients = new LinkedHashMap<Integer, Patient>();
		for (Patient patient : getPatientsForWorklist(location, careSetting, session, patientService, patienthandoverService)) {
			allowedPatients.put(patient.getPatientId(), patient);
		}
		
		String batchUuid = UUID.randomUUID().toString();
		List<PatientHandover> handovers = new ArrayList<PatientHandover>();
		for (String patientIdParam : patientIdParams) {
			Integer patientId;
			try {
				patientId = Integer.valueOf(patientIdParam);
			}
			catch (NumberFormatException e) {
				continue;
			}
			Patient patient = allowedPatients.get(patientId);
			if (patient == null) {
				continue;
			}
			
			String priority = request.getParameter("priority_" + patientId);
			if (!isValidPriority(priority)) {
				session.setAttribute("locationHandoverError", "Each selected patient must have a valid priority.");
				return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
			}
			
			if (isBlank(request.getParameter("situation_" + patientId))
			        || isBlank(request.getParameter("background_" + patientId))
			        || isBlank(request.getParameter("assessment_" + patientId))
			        || isBlank(request.getParameter("recommendation_" + patientId))) {
				session.setAttribute("locationHandoverError",
				    "Complete all SBAR fields for every selected patient before submitting the handover.");
				return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
			}
			
			String[] taskDescriptions = request.getParameterValues("taskDescription_" + patientId);
			String[] taskDueDates = request.getParameterValues("taskDueDate_" + patientId);
			List<PatientHandoverTask> structuredTasks = new ArrayList<PatientHandoverTask>();
			if (taskDescriptions != null) {
				for (int taskIndex = 0; taskIndex < taskDescriptions.length; taskIndex++) {
					String description = taskDescriptions[taskIndex] == null ? "" : taskDescriptions[taskIndex].trim();
					if (description.isEmpty()) {
						continue;
					}
					if (taskDueDates == null || taskIndex >= taskDueDates.length || isBlank(taskDueDates[taskIndex])) {
						session.setAttribute("locationHandoverError", "Every entered task must have its own due time.");
						return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
					}
					Date dueDate;
					try {
						dueDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(taskDueDates[taskIndex]);
					}
					catch (ParseException e) {
						session.setAttribute("locationHandoverError", "A task due date is invalid.");
						return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
					}
					PatientHandoverTask task = new PatientHandoverTask();
					task.setUuid(UUID.randomUUID().toString());
					task.setDescription(description);
					task.setDueDate(dueDate);
					task.setAssignee(receivingProvider);
					task.setCreator(Context.getAuthenticatedUser());
					task.setDateCreated(new Date());
					structuredTasks.add(task);
				}
			}
			
			PatientHandover handover = new PatientHandover();
			handover.setPatient(patient);
			handover.setBatchUuid(batchUuid);
			handover.setLocation(location);
			handover.setCareSetting(careSetting);
			handover.setShift(shift);
			handover.setReceivingProvider(receivingProvider);
			handover.setPriority(priority);
			handover.setSituation(request.getParameter("situation_" + patientId));
			handover.setBackground(request.getParameter("background_" + patientId));
			handover.setAssessment(request.getParameter("assessment_" + patientId));
			handover.setRecommendation(request.getParameter("recommendation_" + patientId));
			for (PatientHandoverTask task : structuredTasks) {
				handover.addTask(task);
			}
			handovers.add(handover);
		}
		int saved = handovers.size();
		if (saved > 0) {
			patienthandoverService.savePatientHandovers(handovers);
			PatientHandoverAuditSupport.record(patienthandoverService, "SUBMITTED", batchUuid, handovers.get(0).getUuid(),
			    null, true, saved + " patient handover(s) submitted atomically.");
		}
		
		if (saved == 0) {
			session.setAttribute("locationHandoverError",
			    "No handovers were saved because the selected patients are no longer active at this location.");
			return "redirect:patienthandover/locationHandover.page?careSetting=" + careSetting;
		}
		
		session.setAttribute("completedDraftStorageKey", getDraftStorageKey(location, careSetting));
		session.setAttribute("dashboardMessage", saved + " patient handover(s) submitted successfully.");
		return "redirect:patienthandover/handoverDashboard.page";
	}
	
	private List<Patient> getPatientsAtLocation(Location location, String careSetting, PatientService patientService,
	        PatienthandoverService patienthandoverService) {
		List<Integer> locationIds = new ArrayList<Integer>();
		locationIds.add(location.getLocationId());
		for (Location descendant : location.getDescendantLocations(false)) {
			locationIds.add(descendant.getLocationId());
		}
		
		Date minimumEncounterDate = "OPD".equals(careSetting) ? getStartOfToday() : null;
		List<Integer> patientIds = patienthandoverService.getActivePatientIdsByEncounterLocations(locationIds,
		    minimumEncounterDate);
		
		List<Patient> patients = new ArrayList<Patient>();
		for (Integer patientId : patientIds) {
			Patient patient = patientService.getPatient(patientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
				patients.add(patient);
			}
		}
		return patients;
	}
	
	private List<Patient> getPatientsForWorklist(Location location, String careSetting, Session session,
	        PatientService patientService, PatienthandoverService patienthandoverService) {
		Map<Integer, Patient> patients = new LinkedHashMap<Integer, Patient>();
		for (Patient patient : getPatientsAtLocation(location, careSetting, patientService, patienthandoverService)) {
			patients.put(patient.getPatientId(), patient);
		}
		
		String sessionKey = getManualSessionKey(location, careSetting);
		for (Integer patientId : getManualPatientIds(session, sessionKey)) {
			Patient patient = patientService.getPatient(patientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
				patients.put(patientId, patient);
			}
		}
		for (Integer excludedPatientId : getExcludedPatientIds(session, getExcludedSessionKey(location, careSetting))) {
			patients.remove(excludedPatientId);
		}
		return new ArrayList<Patient>(patients.values());
	}
	
	private List<Patient> filterPatients(List<Patient> patients, String query) {
		String normalized = query.toLowerCase();
		List<Patient> matches = new ArrayList<Patient>();
		for (Patient patient : patients) {
			String name = patient.getPersonName() == null ? "" : patient.getPersonName().getFullName();
			boolean matched = name != null && name.toLowerCase().contains(normalized);
			if (!matched) {
				for (org.openmrs.PatientIdentifier identifier : patient.getIdentifiers()) {
					if (identifier.getIdentifier() != null && identifier.getIdentifier().toLowerCase().contains(normalized)) {
						matched = true;
						break;
					}
				}
			}
			if (matched) {
				matches.add(patient);
			}
		}
		return matches;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Integer> getManualPatientIds(Session session, String sessionKey) {
		Set<Integer> patientIds = session.getAttribute(sessionKey, Set.class);
		if (patientIds == null) {
			patientIds = new LinkedHashSet<Integer>();
			session.setAttribute(sessionKey, patientIds);
		}
		return patientIds;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Integer> getExcludedPatientIds(Session session, String sessionKey) {
		Set<Integer> patientIds = session.getAttribute(sessionKey, Set.class);
		if (patientIds == null) {
			patientIds = new LinkedHashSet<Integer>();
			session.setAttribute(sessionKey, patientIds);
		}
		return patientIds;
	}
	
	private String getExcludedSessionKey(Location location, String careSetting) {
		return "patienthandoverExcludedPatients_" + location.getLocationId() + "_" + careSetting;
	}
	
	private Map<String, List<PatientHandover>> groupHandoversByBatch(List<PatientHandover> handovers) {
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
	
	private String getDraftStorageKey(Location location, String careSetting) {
		Integer userId = Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId();
		return "patienthandoverDraft_" + (userId == null ? "anonymous" : userId) + "_" + location.getLocationId() + "_"
		        + careSetting;
	}
	
	private String getManualSessionKey(Location location, String careSetting) {
		return "patienthandoverManualPatients_" + location.getLocationId() + "_" + careSetting;
	}
	
	private Date getStartOfToday() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}
	
	private boolean isValidCareSetting(String careSetting) {
		return "OPD".equals(careSetting) || "IPD".equals(careSetting);
	}
	
	private boolean isValidPriority(String priority) {
		return "STABLE".equals(priority) || "URGENT".equals(priority) || "CRITICAL".equals(priority);
	}
	
	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
