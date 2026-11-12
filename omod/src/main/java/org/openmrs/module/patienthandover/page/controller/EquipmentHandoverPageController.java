package org.openmrs.module.patienthandover.page.controller;

import java.io.*;
import java.util.*;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

public class EquipmentHandoverPageController {
	
	public void get(@RequestParam(value = "page", defaultValue = "1") int page, PageModel model, UiSessionContext context,
	        Session session, @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		Location location = context.getSessionLocation();
		User user = Context.getAuthenticatedUser();
		model.addAttribute("currentLocation", location);
		model.addAttribute("currentUser", user);
		model.addAttribute("canManageEquipment", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute("providers", Context.getProviderService().getAllProviders(false));
		model.addAttribute("equipmentItems",
		    location == null ? Collections.emptyList() : service.getActiveEquipment(location));
		List<EquipmentHandover> visible = new ArrayList<EquipmentHandover>();
		Map<String, List<HandoverTeamMember>> teams = new LinkedHashMap<String, List<HandoverTeamMember>>();
		List<EquipmentHandover> handovers = location == null ? Collections.<EquipmentHandover> emptyList() : service
		        .getEquipmentHandovers(location, 500);
		if (!handovers.isEmpty()) {
			List<String> keys = new ArrayList<String>();
			for (EquipmentHandover h : handovers)
				keys.add(h.getUuid());
			teams = service.getHandoverTeams("EQUIPMENT", keys);
		}
		for (EquipmentHandover h : handovers) {
			List<HandoverTeamMember> team = teams.get(h.getUuid());
			if ("PENDING".equals(h.getStatus())
			        && (h.getCreator().equals(user) || Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE) || HandoverTeamSupport
			                .isMember(team, user)))
				visible.add(h);
		}
		model.addAttribute("equipmentHandovers", PaginationSupport.list(visible, page, model, "equipment"));
		model.addAttribute("equipmentTeams", teams);
		Object msg = session.getAttribute("equipmentMessage", String.class);
		if (msg != null) {
			model.addAttribute("equipmentMessage", msg);
			session.setAttribute("equipmentMessage", null);
		}
		Object err = session.getAttribute("equipmentError", String.class);
		if (err != null) {
			model.addAttribute("equipmentError", err);
			session.setAttribute("equipmentError", null);
		}
	}
	
	public String post(HttpServletRequest request, Session session, @SpringBean PatienthandoverService service) {
		String action = value(request, "action");
		String catalogueRedirect = "equipmentItems".equals(value(request, "returnPage")) ? "redirect:patienthandover/equipmentItems.page"
		        : "redirect:patienthandover/equipmentHandover.page";
		Location location = Context.getUserContext().getLocation();
		if ("addItem".equals(action)) {
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
			try {
				int added = importItems(request, location, service);
				String name = value(request, "equipmentName");
				if (!name.isEmpty()) {
					saveItem(service, location, name, value(request, "quantity"), value(request, "equipmentStatus"),
					    value(request, "observation"));
					added++;
				}
				if (added == 0)
					throw new IllegalArgumentException("Enter one equipment item or choose a CSV file.");
				session.setAttribute("equipmentMessage", added + " equipment item(s) added.");
			}
			catch (Exception e) {
				session.setAttribute("equipmentError", e.getMessage());
			}
			return catalogueRedirect;
		}
		if ("sign".equals(action)) {
			sign(value(request, "handoverUuid"), service, session);
			return catalogueRedirect;
		}
		if ("removeItem".equals(action)) {
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
			try {
				Integer id = Integer.valueOf(value(request, "equipmentItemId"));
				EquipmentItem found = null;
				for (EquipmentItem item : service.getActiveEquipment(location))
					if (id.equals(item.getId()))
						found = item;
				if (found == null)
					throw new IllegalArgumentException("Equipment item not found at this location.");
				found.setActive(false);
				found.setChangedBy(Context.getAuthenticatedUser());
				found.setDateChanged(new Date());
				service.saveEquipmentItem(found);
				session.setAttribute("equipmentMessage",
				    "Equipment removed from future handovers. Existing handover records were preserved.");
			}
			catch (Exception e) {
				session.setAttribute("equipmentError", e.getMessage());
			}
			return catalogueRedirect;
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		String shift = value(request, "shift");
		if (!"DAY".equals(shift) && !"NIGHT".equals(shift)) {
			session.setAttribute("equipmentError", "Select a shift.");
			return "redirect:patienthandover/equipmentHandover.page";
		}
		List<EquipmentItem> all = service.getActiveEquipment(location);
		Set<Integer> selected = new LinkedHashSet<Integer>();
		if ("all".equals(value(request, "submitMode")))
			for (EquipmentItem i : all)
				selected.add(i.getId());
		else
			for (String id : values(request, "equipmentItemIds"))
				try {
					selected.add(Integer.valueOf(id));
				}
				catch (Exception ignored) {}
		if (selected.isEmpty()) {
			session.setAttribute("equipmentError", "Select at least one equipment item.");
			return "redirect:patienthandover/equipmentHandover.page";
		}
		Map<Integer, Double> handedQuantities = new HashMap<Integer, Double>();
		for (EquipmentItem item : all)
			if (selected.contains(item.getId())) {
				try {
					double handed = Double.parseDouble(value(request, "equipmentQuantity_" + item.getId()));
					if (handed <= 0 || handed > item.getQuantity())
						throw new NumberFormatException();
					handedQuantities.put(item.getId(), handed);
				}
				catch (Exception e) {
					session.setAttribute("equipmentError", "Handed quantity for " + item.getEquipmentName()
					        + " must be greater than zero and cannot exceed " + item.getQuantity() + ".");
					return "redirect:patienthandover/equipmentHandover.page";
				}
			}
		List<Provider> receivers = providers(values(request, "receivingProviderIds"));
		List<Provider> senders = providers(values(request, "outgoingProviderIds"));
		if (receivers.isEmpty()) {
			session.setAttribute("equipmentError", "Select at least one receiving team member.");
			return "redirect:patienthandover/equipmentHandover.page";
		}
		Date now = new Date();
		EquipmentHandover h = new EquipmentHandover();
		h.setUuid(UUID.randomUUID().toString());
		h.setLocation(location);
		h.setShift(shift);
		h.setStatus("PENDING");
		h.setDateSubmitted(now);
		h.setCreator(Context.getAuthenticatedUser());
		h.setDateCreated(now);
		for (EquipmentItem i : all)
			if (selected.contains(i.getId())) {
				EquipmentHandoverItem line = new EquipmentHandoverItem();
				line.setUuid(UUID.randomUUID().toString());
				line.setEquipmentItem(i);
				line.setEquipmentName(i.getEquipmentName());
				line.setQuantity(handedQuantities.get(i.getId()));
				String status = value(request, "equipmentStatus_" + i.getId());
				if (!("FUNCTIONAL".equals(status) || "NEEDS MAINTENANCE".equals(status) || "OUT OF SERVICE".equals(status))) {
					session.setAttribute("equipmentError", "Select a valid status for " + i.getEquipmentName() + ".");
					return "redirect:patienthandover/equipmentHandover.page";
				}
				String observation = value(request, "equipmentObservation_" + i.getId());
				if (observation.length() > 1000) {
					session.setAttribute("equipmentError", "Observation for " + i.getEquipmentName()
					        + " cannot exceed 1000 characters.");
					return "redirect:patienthandover/equipmentHandover.page";
				}
				line.setEquipmentStatus(status);
				line.setObservation(observation);
				line.setCreator(Context.getAuthenticatedUser());
				line.setDateCreated(now);
				h.addItem(line);
			}
		service.saveEquipmentHandover(h, HandoverTeamSupport.newTeam("EQUIPMENT", h.getUuid(), Context
		        .getAuthenticatedUser().getPerson(), HandoverTeamSupport.people(senders), HandoverTeamSupport
		        .people(receivers)));
		session.setAttribute("equipmentMessage", h.getItems().size() + " equipment item(s) submitted.");
		return "redirect:patienthandover/equipmentHandover.page";
	}
	
	private void sign(String uuid, PatienthandoverService service, Session session) {
		EquipmentHandover h = service.getEquipmentHandoverByUuid(uuid);
		List<HandoverTeamMember> team = h == null ? Collections.emptyList() : service.getHandoverTeam("EQUIPMENT", uuid);
		HandoverTeamMember m = HandoverTeamSupport.assignment(team, Context.getAuthenticatedUser());
		if (h == null || !"PENDING".equals(h.getStatus()) || !HandoverTeamSupport.maySign(m, team)) {
			session.setAttribute("equipmentError", "This equipment signature is not available.");
			return;
		}
		if ("OUTGOING".equals(m.getRole()))
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		else
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
		boolean complete;
		try {
			complete = HandoverTeamSupport.sign(m, service);
		}
		catch (APIException e) {
			session.setAttribute("equipmentError", e.getMessage());
			return;
		}
		if (complete) {
			h.setStatus("RECEIVED");
			h.setDateReceived(new Date());
			h.setReceivedBy(Context.getAuthenticatedUser());
			h.setChangedBy(Context.getAuthenticatedUser());
			h.setDateChanged(new Date());
			service.saveEquipmentHandover(h);
		}
		session.setAttribute("equipmentMessage", "Your equipment handover signature was recorded.");
	}
	
	private int importItems(HttpServletRequest request, Location location, PatienthandoverService service) throws Exception {
		if (!(request instanceof MultipartHttpServletRequest))
			return 0;
		MultipartFile f = ((MultipartHttpServletRequest) request).getFile("equipmentCsv");
		if (f == null || f.isEmpty())
			return 0;
		int n = 0;
		BufferedReader r = new BufferedReader(new InputStreamReader(f.getInputStream(), "UTF-8"));
		String line;
		boolean first = true;
		while ((line = r.readLine()) != null) {
			if (first) {
				first = false;
				continue;
			}
			String[] c = line.split(",", -1);
			if (c.length < 4 || c[0].trim().isEmpty())
				continue;
			saveItem(service, location, c[0], c[1], c[2], c[3]);
			n++;
		}
		return n;
	}
	
	private void saveItem(PatienthandoverService service, Location location, String name, String qty, String status,
	        String observation) {
		double q;
		try {
			q = Double.parseDouble(qty.trim());
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Equipment quantity must be numeric.");
		}
		if (q < 0 || status.trim().isEmpty())
			throw new IllegalArgumentException("Quantity and status are required.");
		EquipmentItem i = new EquipmentItem();
		i.setUuid(UUID.randomUUID().toString());
		i.setLocation(location);
		i.setEquipmentName(name.trim());
		i.setQuantity(q);
		i.setEquipmentStatus(status.trim());
		i.setObservation(observation.trim());
		i.setActive(true);
		i.setCreator(Context.getAuthenticatedUser());
		i.setDateCreated(new Date());
		service.saveEquipmentItem(i);
	}
	
	private List<Provider> providers(String[] ids) {
		List<Provider> r = new ArrayList<Provider>();
		Set<Integer> seen = new HashSet<Integer>();
		for (String id : ids)
			try {
				Provider p = Context.getProviderService().getProvider(Integer.valueOf(id));
				if (p != null && p.getPerson() != null && seen.add(p.getPerson().getPersonId()))
					r.add(p);
			}
			catch (Exception ignored) {}
		return r;
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
