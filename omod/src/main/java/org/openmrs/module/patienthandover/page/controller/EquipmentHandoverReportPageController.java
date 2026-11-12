package org.openmrs.module.patienthandover.page.controller;

import java.io.IOException;
import java.text.*;
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

public class EquipmentHandoverReportPageController {
	
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
		Date from = parse(fromText, false), to = parse(toText, true);
		List<EquipmentHandover> rows = new ArrayList<EquipmentHandover>();
		if (location != null)
			for (EquipmentHandover h : service.getEquipmentHandovers(location, 2000)) {
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
			response.setHeader("Content-Disposition", "attachment; filename=equipment-handover-report.csv");
			response.getWriter().println(
			    "Reference,Submitted,Shift,Status,Received,Equipment name,Quantity,Equipment status,Observation");
			for (EquipmentHandover h : rows)
				for (EquipmentHandoverItem i : h.getItems())
					response.getWriter().println(
					    csv(h.getTransactionReference()) + "," + csv(h.getDateSubmitted()) + "," + csv(h.getShift()) + ","
					            + csv(h.getStatus()) + "," + csv(h.getDateReceived()) + "," + csv(i.getEquipmentName())
					            + "," + csv(i.getQuantity()) + "," + csv(i.getEquipmentStatus()) + ","
					            + csv(i.getObservation()));
			return;
		}
		List<String> keys = new ArrayList<String>();
		for (EquipmentHandover h : rows)
			keys.add(h.getUuid());
		Map<String, List<HandoverTeamMember>> teams = service.getHandoverTeams("EQUIPMENT", keys);
		model.addAttribute("openmrsContextPath", request.getContextPath());
		model.addAttribute("currentLocation", location);
		model.addAttribute("fromDate", fromText);
		model.addAttribute("toDate", toText);
		model.addAttribute("shift", shift);
		model.addAttribute("status", status);
		model.addAttribute("canExportReports", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS));
		model.addAttribute("equipmentTeams", teams);
		model.addAttribute("handovers", PaginationSupport.list(rows, page, model, "equipmentReport"));
		model.addAttribute("equipmentReportCount", rows.size());
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
}
