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
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class SavedHandoversPageController {
	
	public void controller(@RequestParam(value = "careSetting", defaultValue = "ALL") String careSetting,
	        @RequestParam(value = "status", defaultValue = "ALL") String status,
	        @RequestParam(value = "fromDate", defaultValue = "") String fromDate,
	        @RequestParam(value = "toDate", defaultValue = "") String toDate,
	        @RequestParam(value = "page", defaultValue = "1") int page, PageModel model, UiSessionContext sessionContext,
	        @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		Location location = sessionContext.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("careSetting", careSetting);
		model.addAttribute("status", status);
		model.addAttribute("fromDate", fromDate);
		model.addAttribute("toDate", toDate);
		if (location == null) {
			model.addAttribute("savedError", "Please select a login location.");
			return;
		}
		if (!valid(careSetting, status)) {
			model.addAttribute("savedError", "Invalid saved-handover filter.");
			return;
		}
		Date start;
		Date end;
		try {
			start = parseDate(fromDate, false);
			end = parseDate(toDate, true);
			if (start != null && end != null && !start.before(end))
				throw new ParseException("Invalid range", 0);
		}
		catch (ParseException e) {
			model.addAttribute("savedError", "Select a valid date range.");
			return;
		}
		List<PatientHandover> rows = new ArrayList<PatientHandover>();
		if ("ALL".equals(careSetting) || "IPD".equals(careSetting))
			rows.addAll(service.getRecentHandoversByLocation(location, "IPD", 500));
		if ("ALL".equals(careSetting) || "OPD".equals(careSetting))
			rows.addAll(service.getRecentHandoversByLocation(location, "OPD", 500));
		List<PatientHandover> filtered = new ArrayList<PatientHandover>();
		for (PatientHandover handover : rows) {
			if (start != null && handover.getDateCreated().before(start))
				continue;
			if (end != null && !handover.getDateCreated().before(end))
				continue;
			if ("PENDING".equals(status) && !"PENDING".equals(handover.getStatus()))
				continue;
			if ("RECEIVED".equals(status) && !"RECEIVED".equals(handover.getStatus()))
				continue;
			if ("CANCELLED".equals(status) && !"CANCELLED".equals(handover.getStatus()))
				continue;
			filtered.add(handover);
		}
		Collections.sort(filtered, new Comparator<PatientHandover>() {
			
			public int compare(PatientHandover left, PatientHandover right) {
				return right.getDateCreated().compareTo(left.getDateCreated());
			}
		});
		model.addAttribute("batches", PaginationSupport.map(group(filtered), page, model, "batch"));
	}
	
	private boolean valid(String care, String status) {
		return ("ALL".equals(care) || "IPD".equals(care) || "OPD".equals(care))
		        && ("ALL".equals(status) || "PENDING".equals(status) || "RECEIVED".equals(status) || "CANCELLED"
		                .equals(status));
	}
	
	private Date parseDate(String value, boolean endExclusive) throws ParseException {
		if (value == null || value.trim().isEmpty())
			return null;
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		format.setLenient(false);
		Date parsed = format.parse(value);
		if (!endExclusive)
			return parsed;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(parsed);
		calendar.add(Calendar.DAY_OF_MONTH, 1);
		return calendar.getTime();
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
}
