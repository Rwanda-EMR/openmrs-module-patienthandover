package org.openmrs.module.patienthandover.page.controller;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandoverItem;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class EmergencyTrolleyReportPageController {
	
	private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd");
	
	public void get(@RequestParam(value = "fromDate", required = false) String fromDate,
	        @RequestParam(value = "toDate", required = false) String toDate,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status,
	        @RequestParam(value = "page", defaultValue = "1") int page,
	        @RequestParam(value = "export", defaultValue = "false") boolean export, PageModel model,
	        UiSessionContext context, HttpServletRequest request, HttpServletResponse response,
	        @SpringBean PatienthandoverService service) throws IOException {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_REPORTS);
		Location location = context.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("openmrsContextPath", request.getContextPath());
		model.addAttribute("fromDate", fromDate);
		model.addAttribute("toDate", toDate);
		model.addAttribute("shift", shift);
		model.addAttribute("status", status);
		List<EmergencyTrolleyHandover> filtered = new ArrayList<EmergencyTrolleyHandover>();
		if (location != null) {
			Date from = parse(fromDate, false);
			Date to = parse(toDate, true);
			for (EmergencyTrolleyHandover h : service.getTrolleyHandovers(location, 500)) {
				if (from != null && h.getDateSubmitted().before(from))
					continue;
				if (to != null && !h.getDateSubmitted().before(to))
					continue;
				if (!"ALL".equals(shift) && !shift.equals(h.getShift()))
					continue;
				if (!"ALL".equals(status) && !status.equals(h.getStatus()))
					continue;
				filtered.add(h);
			}
		}
		if (export) {
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS);
			writeCsv(response, filtered);
			return;
		}
		model.addAttribute("handovers", PaginationSupport.list(filtered, page, model, "trolley"));
	}
	
	private Date parse(String value, boolean nextDay) {
		if (value == null || value.trim().isEmpty())
			return null;
		try {
			Date d = DATE.parse(value);
			if (nextDay) {
				Calendar c = Calendar.getInstance();
				c.setTime(d);
				c.add(Calendar.DAY_OF_MONTH, 1);
				return c.getTime();
			}
			return d;
		}
		catch (ParseException e) {
			return null;
		}
	}
	
	private void writeCsv(HttpServletResponse response, List<EmergencyTrolleyHandover> rows) throws IOException {
		response.setContentType("text/csv");
		response.setHeader("Content-Disposition", "attachment; filename=emergency-trolley-handover-report.csv");
		response.getWriter()
		        .println(
		            "Transaction,Submitted,Shift,Outgoing Staff,Receiving Provider,Status,Received or Cancelled Time,Cancelled By,Cancellation Reason,Item,Previous Quantity,Handed Quantity,Note");
		for (EmergencyTrolleyHandover h : rows)
			for (EmergencyTrolleyHandoverItem i : h.getItems())
				response.getWriter().println(
				    csv(h.getTransactionReference())
				            + ","
				            + csv(h.getDateSubmitted())
				            + ","
				            + csv(h.getShift())
				            + ","
				            + csv(h.getCreator() == null ? null : h.getCreator().getPerson().getPersonName().getFullName())
				            + ","
				            + csv(h.getReceivingProvider() == null ? null : h.getReceivingProvider().getName())
				            + ","
				            + csv(h.getStatus())
				            + ","
				            + csv("CANCELLED".equals(h.getStatus()) ? h.getDateCancelled() : h.getDateReceived())
				            + ","
				            + csv(h.getCancelledBy() == null ? null : h.getCancelledBy().getPerson().getPersonName()
				                    .getFullName()) + "," + csv(h.getCancellationReason()) + ","
				            + csv(i.getItemNameSnapshot()) + "," + csv(i.getPreviousQuantity()) + ","
				            + csv(i.getHandedQuantity()) + "," + csv(i.getNote()));
		response.getWriter().flush();
	}
	
	private String csv(Object value) {
		String text = value == null ? "" : String.valueOf(value);
		return "\"" + text.replace("\"", "\"\"") + "\"";
	}
}
