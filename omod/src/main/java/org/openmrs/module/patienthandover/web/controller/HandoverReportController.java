package org.openmrs.module.patienthandover.web.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.PatientHandoverTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("module/patienthandover/handoverReport.form")
public class HandoverReportController {
	
	private static final String VIEW = "/module/patienthandover/handoverReport";
	
	@Autowired
	private PatienthandoverService service;
	
	@RequestMapping(method = RequestMethod.GET, params = "!export")
	public String show(@RequestParam(value = "fromDate", defaultValue = "") String from,
	        @RequestParam(value = "toDate", defaultValue = "") String to,
	        @RequestParam(value = "careSetting", defaultValue = "ALL") String care,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status, Model model) {
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_REPORTS)) {
			PatientHandoverAuditSupport.record(service, "UNAUTHORIZED_ATTEMPT", null, null, null, false,
			    "Attempted to view audit reports without View Reports privilege.");
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_REPORTS);
		Criteria c = criteria(from, to, care, shift, status);
		Location location = Context.getUserContext().getLocation();
		addModel(model, c, location);
		if (location == null) {
			model.addAttribute("reportError", "Please select a login location.");
			return VIEW;
		}
		if (!c.valid) {
			model.addAttribute("reportError", c.error);
			return VIEW;
		}
		PatientHandoverAuditSupport.record(service, "REPORT_VIEWED", null, null, null, true,
		    "Viewed the handover audit report.");
		model.addAttribute("auditEvents", service.getAuditEventsByLocationAndDateRange(location, c.from, c.to));
		List<PatientHandover> rows = load(location, c);
		Map<String, List<PatientHandover>> batches = group(rows);
		int pending = 0, received = 0, critical = 0;
		for (PatientHandover h : rows) {
			if (h.isAcknowledged())
				received++;
			else
				pending++;
			if ("CRITICAL".equals(h.getPriority()))
				critical++;
		}
		model.addAttribute("batches", batches);
		model.addAttribute("patientCount", rows.size());
		model.addAttribute("batchCount", batches.size());
		model.addAttribute("pendingCount", pending);
		model.addAttribute("receivedCount", received);
		model.addAttribute("criticalCount", critical);
		return VIEW;
	}
	
	@RequestMapping(method = RequestMethod.GET, params = "export")
	public void csv(@RequestParam(value = "fromDate", defaultValue = "") String from,
	        @RequestParam(value = "toDate", defaultValue = "") String to,
	        @RequestParam(value = "careSetting", defaultValue = "ALL") String care,
	        @RequestParam(value = "shift", defaultValue = "ALL") String shift,
	        @RequestParam(value = "status", defaultValue = "ALL") String status, HttpServletResponse response)
	        throws IOException {
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS)) {
			PatientHandoverAuditSupport.record(service, "UNAUTHORIZED_ATTEMPT", null, null, null, false,
			    "Attempted CSV export without Export Reports privilege.");
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_EXPORT_REPORTS);
		Criteria c = criteria(from, to, care, shift, status);
		Location location = Context.getUserContext().getLocation();
		if (location == null || !c.valid) {
			response.sendError(400, "Invalid report criteria.");
			return;
		}
		response.setContentType("text/csv");
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=patient-handover-report.csv");
		PrintWriter w = response.getWriter();
		w.println("Date,Transaction,Patient,Identifier,Setting,Shift,Priority,Situation,Background,Assessment,Recommendation,Task Descriptions,Task Due,Task Assignees,Task Status,Tasks Completed By,Tasks Completed Date,Outgoing Staff,Receiving Provider,Received Time,Received By,Status");
		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		for (PatientHandover h : load(location, c)) {
			String batch = h.getTransactionReference();
			String provider = h.getReceivingProvider() == null ? "" : h.getReceivingProvider().getName();
			w.println(q(f.format(h.getDateCreated()))
			        + ","
			        + q(batch)
			        + ","
			        + q(patientName(h))
			        + ","
			        + q(identifier(h))
			        + ","
			        + q(h.getCareSetting())
			        + ","
			        + q(h.getShift())
			        + ","
			        + q(h.getPriority())
			        + ","
			        + q(h.getSituation())
			        + ","
			        + q(h.getBackground())
			        + ","
			        + q(h.getAssessment())
			        + ","
			        + q(h.getRecommendation())
			        + ","
			        + q(taskValues(h, f, 0))
			        + ","
			        + q(taskValues(h, f, 1))
			        + ","
			        + q(taskValues(h, f, 2))
			        + ","
			        + q(taskValues(h, f, 3))
			        + ","
			        + q(taskValues(h, f, 4))
			        + ","
			        + q(taskValues(h, f, 5))
			        + ","
			        + q(outgoingStaff(h))
			        + ","
			        + q(provider)
			        + ","
			        + q(h.getDateAcknowledged() == null ? "" : f.format(h.getDateAcknowledged()))
			        + ","
			        + q(h.getAcknowledgedBy() == null ? "" : h.getAcknowledgedBy().getPerson().getPersonName().getFullName())
			        + "," + q(h.getStatus()));
		}
		w.flush();
	}
	
	private List<PatientHandover> load(Location l, Criteria c) {
		List<PatientHandover> result = new ArrayList<PatientHandover>();
		for (PatientHandover h : service.getHandoversByLocationAndDateRange(l, c.from, c.to)) {
			if (!"ALL".equals(c.care) && !c.care.equals(h.getCareSetting()))
				continue;
			if (!"ALL".equals(c.shift) && !c.shift.equals(h.getShift()))
				continue;
			if ("PENDING".equals(c.status) && !"PENDING".equals(h.getStatus()))
				continue;
			if ("RECEIVED".equals(c.status) && !"RECEIVED".equals(h.getStatus()))
				continue;
			if ("CANCELLED".equals(c.status) && !"CANCELLED".equals(h.getStatus()))
				continue;
			result.add(h);
		}
		return result;
	}
	
	private Criteria criteria(String from, String to, String care, String shift, String status) {
		Criteria c = new Criteria();
		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
		f.setLenient(false);
		Calendar now = Calendar.getInstance();
		if (to.isEmpty())
			to = f.format(now.getTime());
		if (from.isEmpty()) {
			now.add(Calendar.DAY_OF_MONTH, -7);
			from = f.format(now.getTime());
		}
		c.fromText = from;
		c.toText = to;
		c.care = care;
		c.shift = shift;
		c.status = status;
		try {
			c.from = f.parse(from);
			Date inclusive = f.parse(to);
			Calendar end = Calendar.getInstance();
			end.setTime(inclusive);
			end.add(Calendar.DAY_OF_MONTH, 1);
			c.to = end.getTime();
			c.valid = !c.from.after(inclusive) && valid(care, shift, status);
			if (!c.valid)
				c.error = "Please select a valid date range and filters.";
		}
		catch (ParseException e) {
			c.error = "Dates must use YYYY-MM-DD.";
		}
		return c;
	}
	
	private boolean valid(String c, String s, String t) {
		return ("ALL".equals(c) || "IPD".equals(c) || "OPD".equals(c))
		        && ("ALL".equals(s) || "DAY".equals(s) || "NIGHT".equals(s))
		        && ("ALL".equals(t) || "PENDING".equals(t) || "RECEIVED".equals(t) || "CANCELLED".equals(t));
	}
	
	private Map<String, List<PatientHandover>> group(List<PatientHandover> rows) {
		Map<String, List<PatientHandover>> m = new LinkedHashMap<String, List<PatientHandover>>();
		for (PatientHandover h : rows) {
			String k = h.getBatchUuid() == null || h.getBatchUuid().isEmpty() ? "single:" + h.getUuid() : h.getBatchUuid();
			if (!m.containsKey(k))
				m.put(k, new ArrayList<PatientHandover>());
			m.get(k).add(h);
		}
		return m;
	}
	
	private void addModel(Model m, Criteria c, Location l) {
		m.addAttribute("currentLocation", l);
		m.addAttribute("fromDate", c.fromText);
		m.addAttribute("toDate", c.toText);
		m.addAttribute("careSetting", c.care);
		m.addAttribute("shift", c.shift);
		m.addAttribute("status", c.status);
	}
	
	private String taskValues(PatientHandover handover, SimpleDateFormat format, int field) {
		StringBuilder value = new StringBuilder();
		for (PatientHandoverTask task : handover.getTasks()) {
			if (value.length() > 0)
				value.append(" | ");
			switch (field) {
				case 0:
					value.append(task.getDescription());
					break;
				case 1:
					value.append(task.getDueDate() == null ? "" : format.format(task.getDueDate()));
					break;
				case 2:
					value.append(task.getAssignee() == null ? "" : task.getAssignee().getName());
					break;
				case 3:
					value.append(task.isCompleted() ? "COMPLETED" : "PENDING");
					break;
				case 4:
					value.append(task.getCompletedBy() == null ? "" : task.getCompletedBy().getPerson().getPersonName()
					        .getFullName());
					break;
				case 5:
					value.append(task.getDateCompleted() == null ? "" : format.format(task.getDateCompleted()));
					break;
				default:
					break;
			}
		}
		if (value.length() == 0 && handover.getPendingTasks() != null) {
			if (field == 0)
				return handover.getPendingTasks();
			if (field == 1)
				return handover.getTaskDueDate() == null ? "" : format.format(handover.getTaskDueDate());
			if (field == 2)
				return handover.getReceivingProvider() == null ? "" : handover.getReceivingProvider().getName();
			if (field == 3)
				return handover.isTasksCompleted() ? "COMPLETED" : "PENDING";
			if (field == 4)
				return handover.getTasksCompletedBy() == null ? "" : handover.getTasksCompletedBy().getPerson()
				        .getPersonName().getFullName();
			if (field == 5)
				return handover.getDateTasksCompleted() == null ? "" : format.format(handover.getDateTasksCompleted());
		}
		return value.toString();
	}
	
	private String patientName(PatientHandover handover) {
		return handover.getPatient() == null || handover.getPatient().getPersonName() == null ? "" : handover.getPatient()
		        .getPersonName().getFullName();
	}
	
	private String identifier(PatientHandover handover) {
		return handover.getPatient() == null || handover.getPatient().getPatientIdentifier() == null ? "" : handover
		        .getPatient().getPatientIdentifier().getIdentifier();
	}
	
	private String outgoingStaff(PatientHandover handover) {
		return handover.getCreator() == null || handover.getCreator().getPerson() == null
		        || handover.getCreator().getPerson().getPersonName() == null ? "" : handover.getCreator().getPerson()
		        .getPersonName().getFullName();
	}
	
	private String q(String s) {
		if (s == null)
			s = "";
		return "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
	}
	
	private static class Criteria {
		
		boolean valid;
		
		String error, fromText, toText, care, shift, status;
		
		Date from, to;
	}
}
