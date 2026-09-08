package org.openmrs.module.patienthandover.web.controller;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

@Controller
@RequestMapping("module/patienthandover/handoverPdf.form")
public class HandoverPdfController {
	
	private static final BaseColor TEAL = new BaseColor(0, 91, 87), PALE = new BaseColor(232, 244, 241);
	
	private static final Font TITLE = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, TEAL);
	
	private static final Font SECTION = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, TEAL);
	
	private static final Font NORMAL = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.BLACK);
	
	private static final Font HEADER = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD, BaseColor.WHITE);
	
	private static final SimpleDateFormat DT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), D = new SimpleDateFormat(
	        "yyyy-MM-dd");
	
	@Autowired
	private PatienthandoverService service;
	
	@RequestMapping(method = RequestMethod.GET)
	public void download(@RequestParam("type") String type, @RequestParam(value = "key", defaultValue = "") String key,
	        @RequestParam(value = "fromDate", defaultValue = "") String fromDate,
	        @RequestParam(value = "toDate", defaultValue = "") String toDate,
	        @RequestParam(value = "careSetting", defaultValue = "ALL") String careSetting,
	        @RequestParam(value = "pharmacy", defaultValue = "ALL") String pharmacyName,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status,
	        @RequestParam(value = "report", defaultValue = "false") boolean report, HttpServletResponse response)
	        throws IOException {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS);
		Location location = Context.getUserContext().getLocation();
		if (location == null) {
			response.sendError(400, "Login location is required.");
			return;
		}
		boolean fullReport = report || key == null || key.trim().isEmpty();
		response.setContentType("application/pdf");
		String fileName = fullReport ? type.toLowerCase() + "-handover-report" : "handover-"
		        + key.replaceAll("[^A-Za-z0-9._-]", "-");
		response.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".pdf");
		Document doc = new Document(PageSize.A4.rotate(), 28, 28, 30, 28);
		try {
			PdfWriter.getInstance(doc, response.getOutputStream());
			doc.open();
			if (fullReport)
				fullReport(doc, type, location, fromDate, toDate, careSetting, pharmacyName, shift, status);
			else if ("PATIENT".equals(type))
				patient(doc, key, location);
			else if ("PHARMACY".equals(type))
				pharmacy(doc, key, location);
			else if ("TROLLEY".equals(type))
				trolley(doc, key, location);
			else if ("EQUIPMENT".equals(type))
				equipment(doc, key, location);
			else
				throw new IllegalArgumentException("Unknown handover type.");
		}
		catch (DocumentException e) {
			throw new IOException("Unable to generate PDF.", e);
		}
		catch (IllegalArgumentException e) {
			response.reset();
			response.sendError(404, e.getMessage());
		}
		finally {
			if (doc.isOpen())
				doc.close();
		}
	}
	
	private void fullReport(Document doc, String type, Location location, String fromText, String toText, String care,
	        String pharmacyName, String shift, String status) throws DocumentException {
		Date from = parse(fromText, false), to = parse(toText, true);
		heading(doc, type.replace('_', ' ') + " HANDOVER REPORT", "Filtered report");
		meta(doc, new String[][] { { "Location", n(location) }, { "From", fromText }, { "To", toText }, { "Shift", shift },
		        { "Status", status } });
		int count = 0;
		if ("PATIENT".equals(type)) {
			Set<String> keys = new LinkedHashSet<String>();
			for (PatientHandover h : service.getHandoversByLocationAndDateRange(location, from, to)) {
				if (!"ALL".equals(care) && !care.equals(h.getCareSetting()))
					continue;
				if (!"ALL".equals(shift) && !shift.equals(h.getShift()))
					continue;
				if (!"ALL".equals(status) && !status.equals(h.getStatus()))
					continue;
				keys.add(h.getBatchUuid() == null || h.getBatchUuid().isEmpty() ? "single:" + h.getUuid() : h.getBatchUuid());
			}
			for (String reportKey : keys) {
				if (count > 0)
					doc.newPage();
				patient(doc, reportKey, location);
				count++;
			}
		} else if ("PHARMACY".equals(type)) {
			for (PharmacyHandover h : service.getPharmacyHandovers(location, 2000)) {
				if (!within(h.getDateSubmitted(), from, to)
				        || (!"ALL".equals(pharmacyName) && !pharmacyName.equals(h.getPharmacyName()))
				        || (!"ALL".equals(shift) && !shift.equals(h.getShift()))
				        || (!"ALL".equals(status) && !status.equals(h.getStatus())))
					continue;
				if (count > 0)
					doc.newPage();
				pharmacy(doc, h.getUuid(), location);
				count++;
			}
		} else if ("TROLLEY".equals(type)) {
			for (EmergencyTrolleyHandover h : service.getTrolleyHandovers(location, 2000)) {
				if (!within(h.getDateSubmitted(), from, to) || (!"ALL".equals(shift) && !shift.equals(h.getShift()))
				        || (!"ALL".equals(status) && !status.equals(h.getStatus())))
					continue;
				if (count > 0)
					doc.newPage();
				trolley(doc, h.getUuid(), location);
				count++;
			}
		} else if ("EQUIPMENT".equals(type)) {
			for (EquipmentHandover h : service.getEquipmentHandovers(location, 2000)) {
				if (!within(h.getDateSubmitted(), from, to) || (!"ALL".equals(shift) && !shift.equals(h.getShift()))
				        || (!"ALL".equals(status) && !status.equals(h.getStatus())))
					continue;
				if (count > 0)
					doc.newPage();
				equipment(doc, h.getUuid(), location);
				count++;
			}
		} else
			throw new IllegalArgumentException("Unknown handover type.");
		if (count == 0)
			doc.add(new Paragraph("No handovers match the selected filters.", NORMAL));
	}
	
	private boolean within(Date value, Date from, Date to) {
		return value != null && (from == null || !value.before(from)) && (to == null || value.before(to));
	}
	
	private Date parse(String value, boolean nextDay) {
		if (value == null || value.trim().isEmpty())
			return null;
		try {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			format.setLenient(false);
			Date date = format.parse(value);
			if (nextDay) {
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(date);
				calendar.add(Calendar.DAY_OF_MONTH, 1);
				return calendar.getTime();
			}
			return date;
		}
		catch (ParseException e) {
			throw new IllegalArgumentException("Dates must use YYYY-MM-DD.");
		}
	}
	
	private void patient(Document doc, String key, Location location) throws DocumentException {
		List<PatientHandover> list = new ArrayList<PatientHandover>();
		String teamKey = key;
		if (key.startsWith("single:")) {
			PatientHandover h = service.getPatientHandoverByUuid(key.substring(7));
			if (h != null)
				list.add(h);
			teamKey = key.substring(7);
		} else
			list = service.getHandoversByBatchUuid(key);
		if (list.isEmpty() || !same(list.get(0).getLocation(), location))
			throw new IllegalArgumentException("Patient handover not found.");
		PatientHandover first = list.get(0);
		heading(doc, "PATIENT HANDOVER REPORT", first.getTransactionReference());
		meta(
		    doc,
		    new String[][] { { "Location", n(first.getLocation()) },
		            { "Setting / Shift", v(first.getCareSetting()) + " / " + v(first.getShift()) },
		            { "Status", first.getStatus() }, { "Submitted", date(first.getDateCreated()) },
		            { "Received", date(first.getDateAcknowledged()) }, { "Patients", v(list.size()) } });
		List<HandoverTeamMember> patientTeam = service.getHandoverTeam("PATIENT", teamKey);
		if (patientTeam.isEmpty() && first.getUuid() != null && !first.getUuid().equals(teamKey))
			patientTeam = service.getHandoverTeam("PATIENT", first.getUuid());
		teams(doc, patientTeam, first);
		section(doc, "PATIENTS, SBAR AND TASKS");
		for (PatientHandover h : list) {
			String pn = h.getPatient() == null || h.getPatient().getPersonName() == null ? "" : h.getPatient()
			        .getPersonName().getFullName();
			String id = h.getPatient() == null || h.getPatient().getPatientIdentifier() == null ? "" : h.getPatient()
			        .getPatientIdentifier().getIdentifier();
			meta(doc, new String[][] { { "Patient", pn }, { "Identifier", id }, { "Priority", h.getPriority() } });
			table(doc, new String[] { "Situation", "Background", "Assessment", "Recommendation" },
			    new String[][] { { h.getSituation(), h.getBackground(), h.getAssessment(), h.getRecommendation() } });
			List<String[]> ts = new ArrayList<String[]>();
			for (PatientHandoverTask t : h.getTasks())
				ts.add(new String[] {
				        t.getDescription(),
				        date(t.getDueDate()),
				        t.getAssignee() == null ? "" : t.getAssignee().getName(),
				        t.isCompleted() ? "COMPLETED" : "PENDING",
				        t.getCompletedBy() == null || t.getCompletedBy().getPerson() == null ? "" : t.getCompletedBy()
				                .getPerson().getPersonName().getFullName(), date(t.getDateCompleted()) });
			if (!ts.isEmpty())
				table(doc, new String[] { "Task", "Due", "Assignee", "Status", "Completed by", "Completed at" },
				    ts.toArray(new String[ts.size()][]));
			doc.add(new Paragraph(" "));
		}
	}
	
	private void pharmacy(Document doc, String key, Location location) throws DocumentException {
		PharmacyHandover h = service.getPharmacyHandoverByUuid(key);
		if (h == null || !same(h.getLocation(), location))
			throw new IllegalArgumentException("Pharmacy handover not found.");
		heading(doc, "PHARMACY HANDOVER REPORT", h.getUuid());
		meta(
		    doc,
		    new String[][] { { "Location", n(h.getLocation()) }, { "Pharmacy", h.getPharmacyName() },
		            { "Shift", h.getShift() }, { "Status", h.getStatus() }, { "Submitted", date(h.getDateSubmitted()) },
		            { "Received", date(h.getDateAcknowledged()) } });
		pteam(doc, h, "OUTGOING");
		pteam(doc, h, "RECEIVING");
		section(doc, "HANDED STOCK ITEMS");
		List<String[]> rs = new ArrayList<String[]>();
		for (PharmacyHandoverItem i : h.getItems())
			rs.add(new String[] { i.getItemName(), i.getItemCode(), i.getBatchNumber(), only(i.getExpiryDate()),
			        v(i.getAvailableQuantity()) });
		table(doc, new String[] { "Item", "Code", "Batch", "Expiry date", "Handed quantity" },
		    rs.toArray(new String[rs.size()][]));
	}
	
	private void trolley(Document doc, String key, Location location) throws DocumentException {
		EmergencyTrolleyHandover h = service.getTrolleyHandoverByUuid(key);
		if (h == null || !same(h.getLocation(), location))
			throw new IllegalArgumentException("Trolley handover not found.");
		heading(doc, "EMERGENCY TROLLEY HANDOVER REPORT", h.getTransactionReference());
		meta(
		    doc,
		    new String[][] {
		            { "Location", n(h.getLocation()) },
		            { "Trolley type", h.getTrolley() == null ? "Default Emergency Trolley" : h.getTrolley().toString() },
		            { "Shift", h.getShift() },
		            { "Status", h.getStatus() },
		            { "Submitted", date(h.getDateSubmitted()) },
		            { "Received / Cancelled",
		                    date("CANCELLED".equals(h.getStatus()) ? h.getDateCancelled() : h.getDateReceived()) },
		            { "Cancellation reason", h.getCancellationReason() } });
		teams(doc, service.getHandoverTeam("TROLLEY", h.getUuid()), null);
		section(doc, "TROLLEY ITEMS");
		List<String[]> rs = new ArrayList<String[]>();
		for (EmergencyTrolleyHandoverItem i : h.getItems())
			rs.add(new String[] { i.getItemNameSnapshot(), v(i.getPreviousQuantity()), v(i.getHandedQuantity()), i.getNote() });
		table(doc, new String[] { "Item", "Previous quantity", "Handed quantity", "Note" },
		    rs.toArray(new String[rs.size()][]));
	}
	
	private void equipment(Document doc, String key, Location location) throws DocumentException {
		EquipmentHandover h = service.getEquipmentHandoverByUuid(key);
		if (h == null || !same(h.getLocation(), location))
			throw new IllegalArgumentException("Equipment handover not found.");
		heading(doc, "EQUIPMENT HANDOVER REPORT", h.getTransactionReference());
		meta(doc,
		    new String[][] { { "Location", n(h.getLocation()) }, { "Shift", h.getShift() }, { "Status", h.getStatus() },
		            { "Submitted", date(h.getDateSubmitted()) }, { "Received", date(h.getDateReceived()) },
		            { "Equipment items", v(h.getItems().size()) } });
		teams(doc, service.getHandoverTeam("EQUIPMENT", h.getUuid()), null);
		section(doc, "HANDED EQUIPMENT");
		List<String[]> rows = new ArrayList<String[]>();
		for (EquipmentHandoverItem item : h.getItems())
			rows.add(new String[] { item.getEquipmentName(), v(item.getQuantity()), item.getEquipmentStatus(),
			        item.getObservation() });
		table(doc, new String[] { "Equipment name", "Handed quantity", "Status", "Observation" },
		    rows.toArray(new String[rows.size()][]));
	}
	
	private void teams(Document doc, List<HandoverTeamMember> team, PatientHandover legacy) throws DocumentException {
		role(doc, team, "OUTGOING", legacy);
		role(doc, team, "RECEIVING", legacy);
	}
	
	private void role(Document doc, List<HandoverTeamMember> team, String role, PatientHandover legacy)
	        throws DocumentException {
		section(doc, "OUTGOING".equals(role) ? "SENDING TEAM" : "RECEIVING TEAM");
		List<String[]> rs = new ArrayList<String[]>();
		for (HandoverTeamMember m : team)
			if (!Boolean.TRUE.equals(m.getVoided()) && role.equals(m.getRole()))
				rs.add(new String[] {
				        m.getPerson() == null ? "" : m.getPerson().getPersonName().getFullName(),
				        m.isSigned() ? "SIGNED" : "AWAITING",
				        m.getSignedBy() == null || m.getSignedBy().getPerson() == null ? "" : m.getSignedBy().getPerson()
				                .getPersonName().getFullName(), date(m.getDateSigned()) });
		if (rs.isEmpty() && legacy != null)
			rs.add(new String[] {
			        "OUTGOING".equals(role) ? (legacy.getCreator() == null ? "" : legacy.getCreator().getPerson()
			                .getPersonName().getFullName()) : (legacy.getReceivingProvider() == null ? "" : legacy
			                .getReceivingProvider().getName()),
			        "OUTGOING".equals(role) || legacy.isAcknowledged() ? "SIGNED" : "AWAITING", "",
			        "OUTGOING".equals(role) ? date(legacy.getDateCreated()) : date(legacy.getDateAcknowledged()) });
		table(doc, new String[] { "Member", "Signature status", "Signed by", "Signed at" },
		    rs.toArray(new String[rs.size()][]));
	}
	
	private void pteam(Document doc, PharmacyHandover h, String role) throws DocumentException {
		section(doc, "OUTGOING".equals(role) ? "SENDING TEAM" : "RECEIVING TEAM");
		List<String[]> rs = new ArrayList<String[]>();
		for (PharmacyHandoverParticipant p : h.getParticipants())
			if (!Boolean.TRUE.equals(p.getVoided()) && role.equals(p.getRole()))
				rs.add(new String[] {
				        p.getPerson() == null ? "" : p.getPerson().getPersonName().getFullName(),
				        p.isSigned() ? "SIGNED" : "AWAITING",
				        p.getSignedBy() == null || p.getSignedBy().getPerson() == null ? "" : p.getSignedBy().getPerson()
				                .getPersonName().getFullName(), date(p.getDateSigned()) });
		if (rs.isEmpty())
			rs.add(new String[] {
			        "OUTGOING".equals(role) ? (h.getCreator() == null ? "" : h.getCreator().getPerson().getPersonName()
			                .getFullName()) : (h.getReceivingProvider() == null ? "" : h.getReceivingProvider().getName()),
			        "OUTGOING".equals(role) || h.isAcknowledged() ? "SIGNED" : "AWAITING", "",
			        "OUTGOING".equals(role) ? date(h.getDateSubmitted()) : date(h.getDateAcknowledged()) });
		table(doc, new String[] { "Member", "Signature status", "Signed by", "Signed at" },
		    rs.toArray(new String[rs.size()][]));
	}
	
	private void heading(Document d, String title, String ref) throws DocumentException {
		String country = property("billing.healthFacilityCountryHeading", "hospital.country", "registrationcore.country");
		String facilityName = property("billing.healthFacilityName", "hospital.name", "facility.name");
		String address = property("billing.healthFacilityAddress", "hospital.address", "facility.address");
		String email = property("billing.healthFacilityEmail", "hospital.email", "facility.email");
		String logo = property("billing.healthFacilityLogo", "hospital.logo", "facility.logo");
		if (country.isEmpty())
			country = "REPUBLIC OF RWANDA";
		if (facilityName.isEmpty())
			facilityName = "Hospital";
		PdfPTable identity = new PdfPTable(new float[] { 1f, 4f });
		identity.setWidthPercentage(100);
		PdfPCell logoCell = new PdfPCell();
		logoCell.setBorder(Rectangle.NO_BORDER);
		if (!logo.isEmpty())
			try {
				Image image = Image.getInstance(logo);
				image.scaleToFit(72, 72);
				logoCell.addElement(image);
			}
			catch (Exception ignored) {}
		PdfPCell details = new PdfPCell();
		details.setBorder(Rectangle.NO_BORDER);
		details.addElement(new Paragraph(country, NORMAL));
		details.addElement(new Paragraph(facilityName, SECTION));
		if (!address.isEmpty())
			details.addElement(new Paragraph(address, NORMAL));
		if (!email.isEmpty())
			details.addElement(new Paragraph(email, NORMAL));
		identity.addCell(logoCell);
		identity.addCell(details);
		d.add(identity);
		d.add(new Paragraph(title, TITLE));
		d.add(new Paragraph("Reference: " + v(ref) + "    Generated: " + date(new Date()), NORMAL));
		d.add(new Paragraph(" "));
	}
	
	private String property(String... names) {
		for (String name : names) {
			String value = Context.getAdministrationService().getGlobalProperty(name);
			if (value != null && !value.trim().isEmpty())
				return value.trim();
		}
		return "";
	}
	
	private void section(Document d, String title) throws DocumentException {
		Paragraph p = new Paragraph(title, SECTION);
		p.setSpacingBefore(8);
		p.setSpacingAfter(4);
		d.add(p);
	}
	
	private void meta(Document d, String[][] vs) throws DocumentException {
		PdfPTable t = new PdfPTable(vs.length);
		t.setWidthPercentage(100);
		for (String[] x : vs) {
			PdfPCell c = new PdfPCell();
			c.setBackgroundColor(PALE);
			c.setPadding(6);
			c.addElement(new Phrase(v(x[0]), SECTION));
			c.addElement(new Phrase(v(x[1]), NORMAL));
			t.addCell(c);
		}
		d.add(t);
	}
	
	private void table(Document d, String[] hs, String[][] rs) throws DocumentException {
		PdfPTable t = new PdfPTable(hs.length);
		t.setWidthPercentage(100);
		t.setHeaderRows(1);
		for (String h : hs) {
			PdfPCell c = new PdfPCell(new Phrase(h, HEADER));
			c.setBackgroundColor(TEAL);
			c.setPadding(5);
			t.addCell(c);
		}
		for (int r = 0; r < rs.length; r++)
			for (String x : rs[r]) {
				PdfPCell c = new PdfPCell(new Phrase(v(x), NORMAL));
				if (r % 2 == 1)
					c.setBackgroundColor(PALE);
				c.setPadding(5);
				c.setVerticalAlignment(Element.ALIGN_TOP);
				t.addCell(c);
			}
		d.add(t);
	}
	
	private boolean same(Location a, Location b) {
		return a != null && b != null && a.getLocationId().equals(b.getLocationId());
	}
	
	private String n(Location l) {
		return l == null ? "" : l.getName();
	}
	
	private String date(Date x) {
		return x == null ? "-" : DT.format(x);
	}
	
	private String only(Date x) {
		return x == null ? "-" : D.format(x);
	}
	
	private String v(Object x) {
		return x == null ? "" : String.valueOf(x);
	}
}
