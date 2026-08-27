package org.openmrs.module.patienthandover.web.controller;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandoverItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("module/patienthandover/emergencyTrolleyReport.form")
public class EmergencyTrolleyReportController {
	
	@Autowired
	private PatienthandoverService service;
	
	@RequestMapping(method = RequestMethod.GET)
	public void csv(@RequestParam(value = "fromDate", defaultValue = "") String fromDate,
	        @RequestParam(value = "toDate", defaultValue = "") String toDate,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status, HttpServletResponse response)
	        throws IOException {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS);
		Location location = Context.getUserContext().getLocation();
		if (location == null || !valid(shift, status)) {
			response.sendError(400, "Invalid trolley report criteria.");
			return;
		}
		Date from = parse(fromDate, false);
		Date to = parse(toDate, true);
		if ((!fromDate.trim().isEmpty() && from == null) || (!toDate.trim().isEmpty() && to == null)
		        || (from != null && to != null && !from.before(to))) {
			response.sendError(400, "Invalid trolley report date range.");
			return;
		}
		List<EmergencyTrolleyHandover> rows = new ArrayList<EmergencyTrolleyHandover>();
		for (EmergencyTrolleyHandover h : service.getTrolleyHandovers(location, 500)) {
			if (from != null && h.getDateSubmitted().before(from))
				continue;
			if (to != null && !h.getDateSubmitted().before(to))
				continue;
			if (!"ALL".equals(shift) && !shift.equals(h.getShift()))
				continue;
			if (!"ALL".equals(status) && !status.equals(h.getStatus()))
				continue;
			rows.add(h);
		}
		response.setContentType("text/csv");
		response.setCharacterEncoding("UTF-8");
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
	
	private boolean valid(String shift, String status) {
		return ("ALL".equals(shift) || "DAY".equals(shift) || "NIGHT".equals(shift))
		        && ("ALL".equals(status) || "PENDING".equals(status) || "RECEIVED".equals(status) || "CANCELLED"
		                .equals(status));
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
	
	private String csv(Object value) {
		String text = value == null ? "" : String.valueOf(value);
		return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
	}
}
