package org.openmrs.module.patienthandover.web.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("module/patienthandover/savedHandovers.form")
public class SavedHandoversController {
	
	private static final String VIEW = "/module/patienthandover/savedHandovers";
	
	@Autowired
	private PatienthandoverService service;
	
	@RequestMapping(method = RequestMethod.GET)
	public String show(@RequestParam(value = "careSetting", defaultValue = "ALL") String careSetting,
	        @RequestParam(value = "status", defaultValue = "ALL") String status,
	        @RequestParam(value = "fromDate", defaultValue = "") String fromDate,
	        @RequestParam(value = "toDate", defaultValue = "") String toDate, Model model) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		Location location = Context.getUserContext().getLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("careSetting", careSetting);
		model.addAttribute("status", status);
		model.addAttribute("fromDate", fromDate);
		model.addAttribute("toDate", toDate);
		if (location == null) {
			model.addAttribute("savedError", "Please select a login location.");
			return VIEW;
		}
		if (!valid(careSetting, status)) {
			model.addAttribute("savedError", "Invalid saved-handover filter.");
			return VIEW;
		}
		
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
			model.addAttribute("savedError", "Select a valid date range.");
			return VIEW;
		}
		
		List<PatientHandover> rows = new ArrayList<PatientHandover>();
		if ("ALL".equals(careSetting) || "IPD".equals(careSetting)) {
			rows.addAll(service.getRecentHandoversByLocation(location, "IPD", 500));
		}
		if ("ALL".equals(careSetting) || "OPD".equals(careSetting)) {
			rows.addAll(service.getRecentHandoversByLocation(location, "OPD", 500));
		}
		User authenticatedUser = Context.getAuthenticatedUser();
		boolean canViewAllProviders = Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_ALL_PROVIDERS);
		List<PatientHandover> filtered = new ArrayList<PatientHandover>();
		for (PatientHandover handover : rows) {
			if ("PENDING".equals(status) && handover.isAcknowledged())
				continue;
			if ("RECEIVED".equals(status) && !handover.isAcknowledged())
				continue;
			filtered.add(handover);
		}
		Collections.sort(filtered, new Comparator<PatientHandover>() {
			
			public int compare(PatientHandover left, PatientHandover right) {
				return right.getDateCreated().compareTo(left.getDateCreated());
			}
		});
		model.addAttribute("batches", group(filtered));
		return VIEW;
	}
	
	private boolean valid(String care, String status) {
		return ("ALL".equals(care) || "IPD".equals(care) || "OPD".equals(care))
		        && ("ALL".equals(status) || "PENDING".equals(status) || "RECEIVED".equals(status));
	}
	
	private Map<String, List<PatientHandover>> group(List<PatientHandover> rows) {
		Map<String, List<PatientHandover>> result = new LinkedHashMap<String, List<PatientHandover>>();
		for (PatientHandover handover : rows) {
			String key = handover.getBatchUuid() == null || handover.getBatchUuid().trim().isEmpty() ? "single:"
			        + handover.getUuid() : handover.getBatchUuid();
			if (!result.containsKey(key))
				result.put(key, new ArrayList<PatientHandover>());
			result.get(key).add(handover);
		}
		return result;
	}
	
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
	
	private boolean isVisibleToUser(PatientHandover handover, User user) {
		if (user == null) {
			return false;
		}
		boolean createdByUser = handover.getCreator() != null && user.getUserId().equals(handover.getCreator().getUserId());
		boolean assignedToUser = handover.getReceivingProvider() != null
		        && handover.getReceivingProvider().getPerson() != null && user.getPerson() != null
		        && handover.getReceivingProvider().getPerson().getPersonId().equals(user.getPerson().getPersonId());
		return createdByUser || assignedToUser;
	}
}
