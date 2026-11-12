package org.openmrs.module.patienthandover.page.controller;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.servlet.http.*;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.*;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class LaboratoryHandoverReportPageController {
	
	public void get(@RequestParam(value = "fromDate", defaultValue = "") String fromText,
	        @RequestParam(value = "toDate", defaultValue = "") String toText,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status,
	        @RequestParam(value = "page", defaultValue = "1") int page,
	        @RequestParam(value = "export", defaultValue = "false") boolean export, PageModel model,
	        UiSessionContext context, HttpServletRequest request, HttpServletResponse response,
	        @SpringBean PatienthandoverService service) throws IOException {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_REPORTS);
		Location location = context.getSessionLocation();
		if (!isLaboratoryLocation(location)) {
			model.addAttribute("laboratoryLocationAllowed", false);
			model.addAttribute("currentLocation", location);
			model.addAttribute("handovers", Collections.emptyList());
			model.addAttribute("laboratoryTeams", Collections.emptyMap());
			model.addAttribute("fromDate", fromText);
			model.addAttribute("toDate", toText);
			model.addAttribute("shift", shift);
			model.addAttribute("status", status);
			model.addAttribute("canExportReports", false);
			return;
		}
		model.addAttribute("laboratoryLocationAllowed", true);
		model.addAttribute("openmrsContextPath", request.getContextPath());
		Date from = parse(fromText, false), to = parse(toText, true);
		List<LaboratoryHandover> rows = new ArrayList<LaboratoryHandover>();
		if (location != null)
			for (LaboratoryHandover h : service.getLaboratoryHandovers(location, 2000)) {
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
		if (export) {
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS);
			response.setContentType("text/csv");
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Content-Disposition", "attachment; filename=laboratory-handover-report.csv");
			response.getWriter()
			        .println(
			            "Reference,Submitted,Shift,Status,Received,Patient,Identifier,Lab code,Ordered on,Comment,Sending team,Receiving team");
			Map<String, List<HandoverTeamMember>> exportTeams = service.getHandoverTeams("LABORATORY", uuids(rows));
			for (LaboratoryHandover h : rows) {
				List<HandoverTeamMember> team = exportTeams.get(h.getUuid());
				for (LaboratoryHandoverItem i : h.getItems())
					response.getWriter().println(
					    csv(h.getTransactionReference()) + "," + csv(h.getDateSubmitted()) + "," + csv(h.getShift()) + ","
					            + csv(h.getStatus()) + "," + csv(h.getDateReceived()) + "," + csv(i.getPatientName()) + ","
					            + csv(i.getPatientIdentifier()) + "," + csv(i.getLabCode()) + "," + csv(i.getOrderedOn())
					            + "," + csv(i.getComment()) + "," + csv(names(team, "OUTGOING")) + ","
					            + csv(names(team, "RECEIVING")));
			}
			return;
		}
		Map<String, List<HandoverTeamMember>> teams = service.getHandoverTeams("LABORATORY", uuids(rows));
		model.addAttribute("currentLocation", location);
		model.addAttribute("fromDate", fromText);
		model.addAttribute("toDate", toText);
		model.addAttribute("shift", shift);
		model.addAttribute("status", status);
		model.addAttribute("canExportReports", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS));
		model.addAttribute("laboratoryTeams", teams);
		model.addAttribute("handovers", PaginationSupport.list(rows, page, model, "laboratoryReport"));
		model.addAttribute("laboratoryReportCount", rows.size());
	}
	
	private String names(List<HandoverTeamMember> team, String role) {
		StringBuilder b = new StringBuilder();
		for (HandoverTeamMember m : team)
			if (role.equals(m.getRole()) && !m.isVoided()) {
				if (b.length() > 0)
					b.append("; ");
				b.append(m.getPerson().getPersonName()).append(m.isSigned() ? " (signed)" : " (pending)");
			}
		return b.toString();
	}
	
	private Date parse(String v, boolean next) {
		if (v == null || v.trim().isEmpty())
			return null;
		try {
			Date d = new SimpleDateFormat("yyyy-MM-dd").parse(v);
			if (next) {
				Calendar c = Calendar.getInstance();
				c.setTime(d);
				c.add(Calendar.DAY_OF_MONTH, 1);
				return c.getTime();
			}
			return d;
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private String csv(Object o) {
		String s = o == null ? "" : String.valueOf(o);
		return "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\"";
	}
	
	private boolean isLaboratoryLocation(Location location) {
		if (location == null)
			return false;
		String configured = Context.getAdministrationService()
		        .getGlobalProperty("patienthandover.laboratoryLocationIds", "");
		if (!configured.trim().isEmpty())
			for (String id : configured.split(","))
				if (id.trim().equals(String.valueOf(location.getLocationId())))
					return true;
		Location current = location;
		while (current != null) {
			String name = current.getName() == null ? "" : current.getName().toLowerCase();
			if (name.contains("laboratory") || name.matches(".*\\blab\\b.*"))
				return true;
			current = current.getParentLocation();
		}
		return false;
	}
	
	private static List<String> uuids(List<LaboratoryHandover> handovers) {
		List<String> keys = new ArrayList<String>();
		for (LaboratoryHandover h : handovers)
			keys.add(h.getUuid());
		return keys;
	}
}
