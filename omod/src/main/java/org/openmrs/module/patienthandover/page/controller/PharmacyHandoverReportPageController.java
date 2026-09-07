package org.openmrs.module.patienthandover.page.controller;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.openmrs.module.patienthandover.domain.PatientHandoverAuditEvent;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PharmacyHandover;
import org.openmrs.module.patienthandover.domain.PharmacyHandoverItem;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class PharmacyHandoverReportPageController {
	
	public void get(@RequestParam(value = "fromDate", required = false) String fromDate,
	        @RequestParam(value = "toDate", required = false) String toDate,
	        @RequestParam(value = "pharmacy", defaultValue = "ALL") String pharmacy,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status,
	        @RequestParam(value = "page", defaultValue = "1") int page,
	        @RequestParam(value = "export", defaultValue = "false") boolean export, HttpServletRequest request,
	        PageModel model, UiSessionContext context, HttpServletResponse response,
	        @SpringBean PatienthandoverService service) throws IOException {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_REPORTS);
		Location location = context.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("openmrsContextPath", request.getContextPath());
		model.addAttribute("currentReportUser", Context.getAuthenticatedUser());
		model.addAttribute("canManagePharmacy", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute("fromDate", fromDate);
		model.addAttribute("toDate", toDate);
		model.addAttribute("pharmacy", pharmacy);
		model.addAttribute("shift", shift);
		model.addAttribute("status", status);
		model.addAttribute("canExportReports", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS));
		List<PharmacyHandover> all = location == null ? new ArrayList<PharmacyHandover>() : service.getPharmacyHandovers(
		    location, 2000);
		List<String> pharmacies = new ArrayList<String>();
		List<PharmacyHandover> filtered = new ArrayList<PharmacyHandover>();
		Date from = parse(fromDate, false);
		Date to = parse(toDate, true);
		for (PharmacyHandover h : all) {
			if (!pharmacies.contains(h.getPharmacyName()))
				pharmacies.add(h.getPharmacyName());
			if (from != null && h.getDateSubmitted().before(from))
				continue;
			if (to != null && !h.getDateSubmitted().before(to))
				continue;
			if (!"ALL".equals(pharmacy) && !pharmacy.equals(h.getPharmacyName()))
				continue;
			if (!"ALL".equals(shift) && !shift.equals(h.getShift()))
				continue;
			if (!"ALL".equals(status) && !status.equals(h.getStatus()))
				continue;
			filtered.add(h);
		}
		Map<String, List<PatientHandoverAuditEvent>> auditByHandover = new LinkedHashMap<String, List<PatientHandoverAuditEvent>>();
		Date auditFrom = from == null ? new Date(0) : from;
		Date auditTo = to == null ? new Date(System.currentTimeMillis() + 86400000L) : to;
		for (PatientHandoverAuditEvent event : service.getAuditEventsByLocationAndDateRange(location, auditFrom, auditTo)) {
			if (event.getHandoverUuid() == null || !event.getEventType().startsWith("PHARMACY_"))
				continue;
			List<PatientHandoverAuditEvent> events = auditByHandover.get(event.getHandoverUuid());
			if (events == null) {
				events = new ArrayList<PatientHandoverAuditEvent>();
				auditByHandover.put(event.getHandoverUuid(), events);
			}
			events.add(event);
		}
		model.addAttribute("pharmacyAuditEvents", auditByHandover);
		model.addAttribute("pharmacies", pharmacies);
		if (export) {
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS);
			writeCsv(response, filtered);
			return;
		}
		model.addAttribute("handovers", PaginationSupport.list(filtered, page, model, "pharmacyReport"));
	}
	
	private Date parse(String value, boolean nextDay) {
		if (value == null || value.trim().isEmpty())
			return null;
		try {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			format.setLenient(false);
			Date date = format.parse(value);
			if (!nextDay)
				return date;
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			calendar.add(Calendar.DAY_OF_MONTH, 1);
			return calendar.getTime();
		}
		catch (ParseException e) {
			return null;
		}
	}
	
	private void writeCsv(HttpServletResponse response, List<PharmacyHandover> handovers) throws IOException {
		response.setContentType("text/csv");
		response.setHeader("Content-Disposition", "attachment; filename=pharmacy-handover-report.csv");
		response.getWriter()
		        .println(
		            "Handover UUID,Submitted,Pharmacy,Shift,Outgoing Staff,Receiving Provider,Status,Received Time,Item,Code,Batch,Expiry Date,Handed Quantity");
		for (PharmacyHandover h : handovers)
			for (PharmacyHandoverItem item : h.getItems())
				response.getWriter().println(
				    csv(h.getUuid()) + "," + csv(h.getDateSubmitted()) + "," + csv(h.getPharmacyName()) + ","
				            + csv(h.getShift()) + ","
				            + csv(h.getCreator() == null ? null : h.getCreator().getPerson().getPersonName().getFullName())
				            + "," + csv(h.getReceivingProvider() == null ? null : h.getReceivingProvider().getName()) + ","
				            + csv(h.getStatus()) + "," + csv(h.getDateAcknowledged()) + "," + csv(item.getItemName()) + ","
				            + csv(item.getItemCode()) + "," + csv(item.getBatchNumber()) + "," + csv(item.getExpiryDate())
				            + "," + csv(item.getAvailableQuantity()));
		response.getWriter().flush();
	}
	
	private String csv(Object value) {
		String text = value == null ? "" : String.valueOf(value);
		return "\"" + text.replace("\"", "\"\"") + "\"";
	}
}
