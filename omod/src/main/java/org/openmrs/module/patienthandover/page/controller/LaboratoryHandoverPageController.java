package org.openmrs.module.patienthandover.page.controller;

import java.util.*;
import java.text.SimpleDateFormat;
import javax.servlet.http.HttpServletRequest;
import org.openmrs.*;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.*;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.*;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

public class LaboratoryHandoverPageController {
	
	public void get(@RequestParam(value = "page", defaultValue = "1") int page,
	        @RequestParam(value = "viewAll", defaultValue = "false") boolean viewAll,
	        @RequestParam(value = "search", required = false) String search,
	        @RequestParam(value = "fromDate", defaultValue = "") String fromText,
	        @RequestParam(value = "toDate", defaultValue = "") String toText, PageModel model, UiSessionContext context,
	        Session session, HttpServletRequest request, @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		Location location = context.getSessionLocation();
		if (!isLaboratoryLocation(location)) {
			model.addAttribute("laboratoryLocationAllowed", false);
			model.addAttribute("currentLocation", location);
			model.addAttribute("laboratoryConfigured", true);
			model.addAttribute("laboratoryWorklist", Collections.emptyList());
			model.addAttribute("laboratoryHandovers", Collections.emptyList());
			model.addAttribute("laboratoryTeams", Collections.emptyMap());
			model.addAttribute("providers", Collections.emptyList());
			return;
		}
		model.addAttribute("laboratoryLocationAllowed", true);
		model.addAttribute("openmrsContextPath", request.getContextPath());
		Date from = parseDate(fromText, false), to = parseDate(toText, true);
		if (from == null) {
			Calendar c = Calendar.getInstance();
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			c.add(Calendar.DAY_OF_MONTH, -2);
			from = c.getTime();
			fromText = formatDate(from);
		}
		if (to == null) {
			Calendar c = Calendar.getInstance();
			c.add(Calendar.DAY_OF_MONTH, 1);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			to = c.getTime();
			toText = formatDate(new Date());
		}
		Integer orderTypeId = integerGp("laboratorymanagement.orderType.labOrderTypeId");
		List<Integer> facilityIds = locationIds(location, false);
		List<PendingLaboratoryPatient> rows = orderTypeId == null ? Collections.<PendingLaboratoryPatient> emptyList()
		        : service.getPendingLaboratoryPatients(facilityIds, orderTypeId, null, null, from, to, viewAll ? 5000 : 200);
		Set<String> manual = stringSet(session, "laboratoryManualRows");
		if (!manual.isEmpty() && orderTypeId != null) {
			List<Integer> allLocations = locationIds(location, true);
			for (String manualKey : manual)
				try {
					Integer patientId = Integer.valueOf(manualKey.substring(0, manualKey.indexOf('|')));
					for (PendingLaboratoryPatient candidate : service.getPendingLaboratoryPatients(allLocations,
					    orderTypeId, patientId, null, null, null, 100))
						if (manualKey.equals(key(candidate)) && !contains(rows, manualKey))
							rows.add(candidate);
				}
				catch (Exception ignored) {}
		}
		Set<String> removed = stringSet(session, "laboratoryRemovedRows");
		List<Map<String, Object>> worklist = decorate(rows, removed);
		model.addAttribute("laboratoryConfigured", orderTypeId != null);
		model.addAttribute("laboratoryWorklist",
		    viewAll ? worklist : PaginationSupport.list(worklist, page, model, "laboratoryWorklist"));
		model.addAttribute("viewAll", viewAll);
		model.addAttribute("providers", Context.getProviderService().getAllProviders(false));
		model.addAttribute("currentLocation", location);
		model.addAttribute("currentUser", Context.getAuthenticatedUser());
		model.addAttribute("canManageLaboratory", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute("search", search == null ? "" : search);
		model.addAttribute("fromDate", fromText);
		model.addAttribute("toDate", toText);
		if (search != null && !search.trim().isEmpty() && orderTypeId != null) {
			List<PendingLaboratoryPatient> found = new ArrayList<PendingLaboratoryPatient>();
			List<Integer> allLocations = locationIds(location, true);
			for (Integer patientId : service.searchActivePatientIds(search, 30))
				for (PendingLaboratoryPatient candidate : service.getPendingLaboratoryPatients(allLocations, orderTypeId,
				    patientId, null, null, null, 30))
					if (!contains(found, key(candidate)))
						found.add(candidate);
			for (PendingLaboratoryPatient candidate : service.getPendingLaboratoryPatients(allLocations, orderTypeId, null,
			    search, null, null, 30))
				if (!contains(found, key(candidate)))
					found.add(candidate);
			model.addAttribute("laboratorySearchResults", decorate(found, Collections.<String> emptySet()));
		}
		List<LaboratoryHandover> visible = new ArrayList<LaboratoryHandover>();
		List<LaboratoryHandover> recent = new ArrayList<LaboratoryHandover>();
		Map<String, List<HandoverTeamMember>> teams = new LinkedHashMap<String, List<HandoverTeamMember>>();
		if (location != null) {
			List<LaboratoryHandover> handovers = service.getLaboratoryHandovers(location, 500);
			List<String> keys = new ArrayList<String>();
			for (LaboratoryHandover h : handovers)
				keys.add(h.getUuid());
			teams = service.getHandoverTeams("LABORATORY", keys);
			for (LaboratoryHandover h : handovers) {
				if (recent.size() < 50)
					recent.add(h);
				List<HandoverTeamMember> team = teams.get(h.getUuid());
				if ("PENDING".equals(h.getStatus())
				        && (h.getCreator().equals(Context.getAuthenticatedUser())
				                || Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE) || HandoverTeamSupport
				                    .isMember(team, Context.getAuthenticatedUser())))
					visible.add(h);
			}
		}
		model.addAttribute("laboratoryHandovers", visible);
		model.addAttribute("recentLaboratoryHandovers", recent);
		model.addAttribute("laboratoryTeams", teams);
		flash(session, model, "laboratoryMessage");
		flash(session, model, "laboratoryError");
	}
	
	public String post(HttpServletRequest r, Session session, @SpringBean PatienthandoverService service) {
		String action = value(r, "action");
		if (!value(r, "removeKey").isEmpty()) {
			action = "removeWorklist";
		}
		if ("addManual".equals(action)) {
			Set<String> set = stringSet(session, "laboratoryManualRows");
			set.add(value(r, "rowKey"));
			session.setAttribute("laboratoryManualRows", new ArrayList<String>(set));
			Set<String> removed = stringSet(session, "laboratoryRemovedRows");
			removed.remove(value(r, "rowKey"));
			session.setAttribute("laboratoryRemovedRows", new ArrayList<String>(removed));
			session.setAttribute("laboratoryMessage", "Patient added to the laboratory worklist.");
			return "redirect:patienthandover/laboratoryHandover.page";
		}
		if ("removeWorklist".equals(action) || "restoreWorklist".equals(action)) {
			Set<String> set = stringSet(session, "laboratoryRemovedRows");
			String key = value(r, "removeKey").isEmpty() ? value(r, "rowKey") : value(r, "removeKey");
			if ("removeWorklist".equals(action))
				set.add(key);
			else
				set.remove(key);
			session.setAttribute("laboratoryRemovedRows", new ArrayList<String>(set));
			return "redirect:patienthandover/laboratoryHandover.page";
		}
		if ("sign".equals(action)) {
			sign(value(r, "handoverUuid"), service, session);
			return "redirect:patienthandover/laboratoryHandover.page";
		}
		if ("editHandover".equals(action)) {
			LaboratoryHandover handover = service.getLaboratoryHandoverByUuid(value(r, "handoverUuid"));
			if (handover == null || !"PENDING".equals(handover.getStatus()))
				return error(session, "Only a pending laboratory handover can be edited.");
			if (!handover.getCreator().equals(Context.getAuthenticatedUser())
			        && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE))
				return error(session, "Only the handover creator or a manager may edit it.");
			for (LaboratoryHandoverItem item : handover.getItems()) {
				String comment = value(r, "editComment_" + item.getId());
				if (comment.length() > 2000)
					return error(session, "A laboratory comment cannot exceed 2000 characters.");
				item.setComment(comment);
				item.setChangedBy(Context.getAuthenticatedUser());
				item.setDateChanged(new Date());
			}
			handover.setChangedBy(Context.getAuthenticatedUser());
			handover.setDateChanged(new Date());
			service.saveLaboratoryHandover(handover);
			session.setAttribute("laboratoryMessage", "Laboratory handover comments updated.");
			return "redirect:patienthandover/laboratoryHandover.page";
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		Location location = Context.getUserContext().getLocation();
		if (!isLaboratoryLocation(location))
			return error(session, "Laboratory handovers are available only when signed in at a laboratory service location.");
		String shift = value(r, "shift");
		if (!"DAY".equals(shift) && !"NIGHT".equals(shift))
			return error(session, "Select a shift.");
		List<Provider> receivers = providers(values(r, "receivingProviderIds"));
		List<Provider> senders = providers(values(r, "outgoingProviderIds"));
		if (receivers.isEmpty())
			return error(session, "Select at least one receiving team member.");
		Set<String> selected = new LinkedHashSet<String>(Arrays.asList(values(r, "laboratoryRowKeys")));
		if (selected.isEmpty())
			return error(session, "Select at least one laboratory patient.");
		Integer orderTypeId = integerGp("laboratorymanagement.orderType.labOrderTypeId");
		if (orderTypeId == null)
			return error(session, "Laboratory order type is not configured.");
		Map<String, PendingLaboratoryPatient> available = new HashMap<String, PendingLaboratoryPatient>();
		List<Integer> allLocations = locationIds(location, true);
		Set<Integer> selectedPatients = new LinkedHashSet<Integer>();
		for (String selectedKey : selected)
			try {
				selectedPatients.add(Integer.valueOf(selectedKey.substring(0, selectedKey.indexOf('|'))));
			}
			catch (Exception ignored) {}
		for (Integer patientId : selectedPatients)
			for (PendingLaboratoryPatient p : service.getPendingLaboratoryPatients(allLocations, orderTypeId, patientId,
			    null, null, null, 100))
				available.put(key(p), p);
		Date now = new Date();
		LaboratoryHandover h = new LaboratoryHandover();
		h.setUuid(UUID.randomUUID().toString());
		h.setLocation(location);
		h.setShift(shift);
		h.setStatus("PENDING");
		h.setDateSubmitted(now);
		h.setCreator(Context.getAuthenticatedUser());
		h.setDateCreated(now);
		for (String k : selected) {
			PendingLaboratoryPatient row = available.get(k);
			if (row == null)
				continue;
			String comment = value(r, "comment_" + safe(k));
			if (comment.length() > 2000)
				return error(session, "A laboratory comment cannot exceed 2000 characters.");
			Patient p = Context.getPatientService().getPatient(row.getPatientId());
			if (p == null)
				continue;
			LaboratoryHandoverItem line = new LaboratoryHandoverItem();
			line.setUuid(UUID.randomUUID().toString());
			line.setPatient(p);
			line.setPatientName(row.getPatientName());
			line.setPatientIdentifier(row.getPatientIdentifier());
			line.setLabCode(row.getLabCode());
			line.setOrderIds(row.getOrderIds());
			line.setOrderedOn(row.getOrderedOn());
			line.setComment(comment);
			line.setCreator(Context.getAuthenticatedUser());
			line.setDateCreated(now);
			h.addItem(line);
		}
		if (h.getItems().isEmpty())
			return error(session,
			    "Selected laboratory orders now have results or are no longer available. Refresh the worklist.");
		service.saveLaboratoryHandover(h, HandoverTeamSupport.newTeam("LABORATORY", h.getUuid(), Context
		        .getAuthenticatedUser().getPerson(), HandoverTeamSupport.people(senders), HandoverTeamSupport
		        .people(receivers)));
		session.setAttribute("laboratoryMessage", h.getItems().size() + " laboratory patient(s) handed over.");
		return "redirect:patienthandover/laboratoryHandover.page";
	}
	
	private List<Map<String, Object>> decorate(List<PendingLaboratoryPatient> rows, Set<String> removed) {
		List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
		for (PendingLaboratoryPatient row : rows) {
			if (removed.contains(key(row)))
				continue;
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("key", key(row));
			m.put("safeKey", safe(key(row)));
			m.put("patientId", row.getPatientId());
			m.put("patientName", row.getPatientName());
			m.put("identifier", row.getPatientIdentifier());
			m.put("labCode", row.getLabCode());
			m.put("orderedOn", row.getOrderedOn());
			out.add(m);
		}
		return out;
	}
	
	private void sign(String uuid, PatienthandoverService s, Session session) {
		LaboratoryHandover h = s.getLaboratoryHandoverByUuid(uuid);
		List<HandoverTeamMember> team = h == null ? Collections.<HandoverTeamMember> emptyList() : s.getHandoverTeam(
		    "LABORATORY", uuid);
		HandoverTeamMember m = HandoverTeamSupport.assignment(team, Context.getAuthenticatedUser());
		if (h == null || !"PENDING".equals(h.getStatus()) || !HandoverTeamSupport.maySign(m, team)) {
			session.setAttribute("laboratoryError", "This laboratory signature is not available.");
			return;
		}
		if ("OUTGOING".equals(m.getRole()))
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		else
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
		boolean complete;
		try {
			complete = HandoverTeamSupport.sign(m, s);
		}
		catch (APIException e) {
			session.setAttribute("laboratoryError", e.getMessage());
			return;
		}
		if (complete) {
			h.setStatus("RECEIVED");
			h.setDateReceived(new Date());
			h.setReceivedBy(Context.getAuthenticatedUser());
			h.setChangedBy(Context.getAuthenticatedUser());
			h.setDateChanged(new Date());
			s.saveLaboratoryHandover(h);
		}
		session.setAttribute("laboratoryMessage", "Your laboratory handover signature was recorded.");
	}
	
	private List<Integer> locationIds(Location location, boolean all) {
		List<Integer> ids = new ArrayList<Integer>();
		Location root = location;
		while (root != null && root.getParentLocation() != null)
			root = root.getParentLocation();
		for (Location l : Context.getLocationService().getAllLocations(false)) {
			if (all || isWithin(l, root))
				ids.add(l.getLocationId());
		}
		return ids;
	}
	
	private boolean isWithin(Location candidate, Location root) {
		if (root == null)
			return false;
		Location x = candidate;
		while (x != null) {
			if (root.equals(x))
				return true;
			x = x.getParentLocation();
		}
		return false;
	}
	
	private boolean contains(List<PendingLaboratoryPatient> rows, String wanted) {
		for (PendingLaboratoryPatient row : rows)
			if (wanted.equals(key(row)))
				return true;
		return false;
	}
	
	private Integer integerGp(String name) {
		try {
			String v = Context.getAdministrationService().getGlobalProperty(name);
			return v == null || v.trim().isEmpty() ? null : Integer.valueOf(v.trim());
		}
		catch (Exception e) {
			return null;
		}
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
	
	private Date parseDate(String value, boolean endExclusive) {
		if (value == null || value.trim().isEmpty())
			return null;
		try {
			Date d = new SimpleDateFormat("yyyy-MM-dd").parse(value);
			if (endExclusive) {
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
	
	private String formatDate(Date date) {
		return new SimpleDateFormat("yyyy-MM-dd").format(date);
	}
	
	@SuppressWarnings("unchecked")
	private Set<String> stringSet(Session s, String n) {
		Object o = s.getAttribute(n, List.class);
		return o instanceof Collection ? new LinkedHashSet<String>((Collection<String>) o) : new LinkedHashSet<String>();
	}
	
	private List<Provider> providers(String[] ids) {
		List<Provider> out = new ArrayList<Provider>();
		Set<Integer> seen = new HashSet<Integer>();
		for (String id : ids)
			try {
				Provider p = Context.getProviderService().getProvider(Integer.valueOf(id));
				if (p != null && p.getPerson() != null && seen.add(p.getPerson().getPersonId()))
					out.add(p);
			}
			catch (Exception ignored) {}
		return out;
	}
	
	private String error(Session s, String message) {
		s.setAttribute("laboratoryError", message);
		return "redirect:patienthandover/laboratoryHandover.page";
	}
	
	private void flash(Session s, PageModel m, String n) {
		Object v = s.getAttribute(n, String.class);
		if (v != null) {
			m.addAttribute(n, v);
			s.setAttribute(n, null);
		}
	}
	
	private String key(PendingLaboratoryPatient p) {
		return p.getPatientId() + "|" + p.getLabCode();
	}
	
	/**
	 * Encodes a row key as a form-field-safe token that is unique per key (hex of its UTF-8 bytes).
	 */
	static String safe(String s) {
		StringBuilder out = new StringBuilder("k");
		try {
			for (byte b : s.getBytes("UTF-8"))
				out.append(String.format("%02x", b & 0xff));
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
		return out.toString();
	}
	
	private String value(HttpServletRequest r, String n) {
		String v = r.getParameter(n);
		return v == null ? "" : v.trim();
	}
	
	private String[] values(HttpServletRequest r, String n) {
		String[] v = r.getParameterValues(n);
		return v == null ? new String[0] : v;
	}
}
