package org.openmrs.module.patienthandover.web.controller;

import java.io.IOException;
import java.io.PrintWriter;
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
import org.openmrs.module.patienthandover.domain.PharmacyHandover;
import org.openmrs.module.patienthandover.domain.PharmacyHandoverItem;
import org.openmrs.module.patienthandover.domain.PharmacyHandoverParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("module/patienthandover/pharmacyHandoverReport.form")
public class PharmacyHandoverReportController {
	
	@Autowired
	private PatienthandoverService service;
	
	@RequestMapping(method = RequestMethod.GET)
	public void csv(@RequestParam(value = "fromDate", defaultValue = "") String fromDate,
	        @RequestParam(value = "toDate", defaultValue = "") String toDate,
	        @RequestParam(value = "pharmacy", defaultValue = "ALL") String pharmacy,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status, HttpServletResponse response)
	        throws IOException {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS);
		Location location = Context.getUserContext().getLocation();
		Date from = parse(fromDate, false);
		Date to = parse(toDate, true);
		if (location == null || (!fromDate.trim().isEmpty() && from == null) || (!toDate.trim().isEmpty() && to == null)
		        || (from != null && to != null && !from.before(to))) {
			response.sendError(400, "Invalid pharmacy report criteria.");
			return;
		}
		List<PharmacyHandover> rows = new ArrayList<PharmacyHandover>();
		for (PharmacyHandover h : service.getPharmacyHandovers(location, 2000)) {
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
			rows.add(h);
		}
		response.setContentType("text/csv");
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=pharmacy-handover-report.csv");
		PrintWriter out = response.getWriter();
		out.write("\ufeff");
		SimpleDateFormat dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SimpleDateFormat dateOnly = new SimpleDateFormat("yyyy-MM-dd");
		for (PharmacyHandover h : rows) {
			String creator = h.getCreator() == null || h.getCreator().getPerson() == null ? "" : h.getCreator().getPerson()
			        .getPersonName().getFullName();
			out.println(q("--------------------------------------------------------------------------"));
			out.println(q("Handover done by " + creator + " on " + dateTime.format(h.getDateSubmitted())));
			out.println("Pharmacy,Shift,Status,Received time");
			out.println(q(h.getPharmacyName()) + "," + q(h.getShift()) + "," + q(h.getStatus()) + ","
			        + q(h.getDateAcknowledged() == null ? "" : dateTime.format(h.getDateAcknowledged())));
			out.println();
			out.println(q("SENDING TEAM"));
			out.println("Member,Signature status,Signed by,Signed time");
			boolean hasParticipants = h.getParticipants() != null && !h.getParticipants().isEmpty();
			if (hasParticipants) {
				for (PharmacyHandoverParticipant p : h.getParticipants())
					if ("OUTGOING".equals(p.getRole()))
						writeMember(out, p, dateTime);
			} else {
				out.println(q(creator) + "," + q("SIGNED") + "," + q(creator) + ","
				        + q(dateTime.format(h.getDateSubmitted())));
			}
			out.println();
			out.println(q("RECEIVING TEAM"));
			out.println("Member,Signature status,Signed by,Signed time");
			if (hasParticipants) {
				for (PharmacyHandoverParticipant p : h.getParticipants())
					if ("RECEIVING".equals(p.getRole()))
						writeMember(out, p, dateTime);
			} else {
				String receiver = h.getReceivingProvider() == null ? "" : h.getReceivingProvider().getName();
				String signedBy = h.getAcknowledgedBy() == null || h.getAcknowledgedBy().getPerson() == null ? "" : h
				        .getAcknowledgedBy().getPerson().getPersonName().getFullName();
				out.println(q(receiver) + "," + q(h.isAcknowledged() ? "SIGNED" : "REMAINING") + "," + q(signedBy) + ","
				        + q(h.getDateAcknowledged() == null ? "" : dateTime.format(h.getDateAcknowledged())));
			}
			out.println();
			out.println(q("HANDED ITEMS"));
			out.println("Item,Code,Batch,Expiry date,Handed quantity");
			for (PharmacyHandoverItem item : h.getItems())
				out.println(q(item.getItemName()) + "," + q(item.getItemCode()) + "," + q(item.getBatchNumber()) + ","
				        + q(item.getExpiryDate() == null ? "" : dateOnly.format(item.getExpiryDate())) + ","
				        + q(item.getAvailableQuantity()));
			out.println();
		}
		out.flush();
	}
	
	private void writeMember(PrintWriter out, PharmacyHandoverParticipant p, SimpleDateFormat format) {
		String member = p.getPerson() == null ? "" : p.getPerson().getPersonName().getFullName();
		String signer = p.getSignedBy() == null || p.getSignedBy().getPerson() == null ? "" : p.getSignedBy().getPerson()
		        .getPersonName().getFullName();
		out.println(q(member) + "," + q(p.isSigned() ? "SIGNED" : "REMAINING") + "," + q(signer) + ","
		        + q(p.getDateSigned() == null ? "" : format.format(p.getDateSigned())));
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
	
	private String q(Object value) {
		String text = value == null ? "" : String.valueOf(value);
		return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
	}
}
