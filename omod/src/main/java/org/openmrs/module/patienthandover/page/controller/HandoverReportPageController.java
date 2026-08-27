package org.openmrs.module.patienthandover.page.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class HandoverReportPageController {
	
	public void controller(@RequestParam(value = "fromDate", defaultValue = "") String from,
	        @RequestParam(value = "toDate", defaultValue = "") String to,
	        @RequestParam(value = "careSetting", defaultValue = "ALL") String care,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status,
	        @RequestParam(value = "page", defaultValue = "1") int page,
	        @RequestParam(value = "auditPage", defaultValue = "1") int auditPage, HttpServletRequest request,
	        PageModel model, UiSessionContext sessionContext, @SpringBean PatienthandoverService service) {
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_REPORTS)) {
			PatientHandoverAuditSupport.record(service, "UNAUTHORIZED_ATTEMPT", null, null, null, false,
			    "Attempted to view audit reports without View Reports privilege.");
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_REPORTS);
		model.addAttribute("canExportReports", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS));
		model.addAttribute("openmrsContextPath", request.getContextPath());
		Criteria criteria = criteria(from, to, care, shift, status);
		Location location = sessionContext.getSessionLocation();
		addModel(model, criteria, location);
		if (location == null) {
			model.addAttribute("reportError", "Please select a login location.");
			return;
		}
		if (!criteria.valid) {
			model.addAttribute("reportError", criteria.error);
			return;
		}
		PatientHandoverAuditSupport.record(service, "REPORT_VIEWED", null, null, null, true,
		    "Viewed the handover audit report.");
		model.addAttribute("auditEvents", PaginationSupport.list(
		    service.getAuditEventsByLocationAndDateRange(location, criteria.from, criteria.to), auditPage, model, "audit"));
		List<PatientHandover> rows = load(location, criteria, service);
		Map<String, List<PatientHandover>> batches = group(rows);
		int pending = 0;
		int received = 0;
		int critical = 0;
		for (PatientHandover handover : rows) {
			if (handover.isAcknowledged())
				received++;
			else if (!handover.isCancelled())
				pending++;
			if ("CRITICAL".equals(handover.getPriority()))
				critical++;
		}
		model.addAttribute("batches", PaginationSupport.map(batches, page, model, "batch"));
		model.addAttribute("patientCount", rows.size());
		model.addAttribute("batchCount", batches.size());
		model.addAttribute("pendingCount", pending);
		model.addAttribute("receivedCount", received);
		model.addAttribute("criticalCount", critical);
	}
	
	private List<PatientHandover> load(Location location, Criteria criteria, PatienthandoverService service) {
		List<PatientHandover> result = new ArrayList<PatientHandover>();
		for (PatientHandover handover : service.getHandoversByLocationAndDateRange(location, criteria.from, criteria.to)) {
			if (!"ALL".equals(criteria.care) && !criteria.care.equals(handover.getCareSetting()))
				continue;
			if (!"ALL".equals(criteria.shift) && !criteria.shift.equals(handover.getShift()))
				continue;
			if ("PENDING".equals(criteria.status) && !"PENDING".equals(handover.getStatus()))
				continue;
			if ("RECEIVED".equals(criteria.status) && !"RECEIVED".equals(handover.getStatus()))
				continue;
			if ("CANCELLED".equals(criteria.status) && !"CANCELLED".equals(handover.getStatus()))
				continue;
			result.add(handover);
		}
		return result;
	}
	
	private Criteria criteria(String from, String to, String care, String shift, String status) {
		Criteria criteria = new Criteria();
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		format.setLenient(false);
		Calendar now = Calendar.getInstance();
		if (to == null || to.isEmpty())
			to = format.format(now.getTime());
		if (from == null || from.isEmpty()) {
			now.add(Calendar.DAY_OF_MONTH, -7);
			from = format.format(now.getTime());
		}
		criteria.fromText = from;
		criteria.toText = to;
		criteria.care = care;
		criteria.shift = shift;
		criteria.status = status;
		try {
			criteria.from = format.parse(from);
			Date inclusive = format.parse(to);
			Calendar end = Calendar.getInstance();
			end.setTime(inclusive);
			end.add(Calendar.DAY_OF_MONTH, 1);
			criteria.to = end.getTime();
			criteria.valid = !criteria.from.after(inclusive) && valid(care, shift, status);
			if (!criteria.valid)
				criteria.error = "Please select a valid date range and filters.";
		}
		catch (ParseException e) {
			criteria.error = "Dates must use YYYY-MM-DD.";
		}
		return criteria;
	}
	
	private boolean valid(String care, String shift, String status) {
		return ("ALL".equals(care) || "IPD".equals(care) || "OPD".equals(care))
		        && ("ALL".equals(shift) || "DAY".equals(shift) || "NIGHT".equals(shift))
		        && ("ALL".equals(status) || "PENDING".equals(status) || "RECEIVED".equals(status) || "CANCELLED"
		                .equals(status));
	}
	
	private Map<String, List<PatientHandover>> group(List<PatientHandover> rows) {
		Map<String, List<PatientHandover>> result = new LinkedHashMap<String, List<PatientHandover>>();
		for (PatientHandover handover : rows) {
			String key = handover.getBatchUuid() == null || handover.getBatchUuid().isEmpty() ? "single:"
			        + handover.getUuid() : handover.getBatchUuid();
			if (!result.containsKey(key))
				result.put(key, new ArrayList<PatientHandover>());
			result.get(key).add(handover);
		}
		return result;
	}
	
	private void addModel(PageModel model, Criteria criteria, Location location) {
		model.addAttribute("currentLocation", location);
		model.addAttribute("fromDate", criteria.fromText);
		model.addAttribute("toDate", criteria.toText);
		model.addAttribute("careSetting", criteria.care);
		model.addAttribute("shift", criteria.shift);
		model.addAttribute("status", criteria.status);
	}
	
	private static class Criteria {
		
		boolean valid;
		
		String error;
		
		String fromText;
		
		String toText;
		
		String care;
		
		String shift;
		
		String status;
		
		Date from;
		
		Date to;
	}
}
