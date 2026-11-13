package org.openmrs.module.patienthandover.page.controller;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.HandoverTeamSupport;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.*;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

public class EmergencyTrolleyPageController {
	
	public void get(@RequestParam(value = "trolleyId", required = false) Integer trolleyId, PageModel model,
	        UiSessionContext context, @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		Location location = context.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("providers", Context.getProviderService().getAllProviders(false));
		if (location == null)
			return;
		List<EmergencyTrolleyUnit> trolleys = service.getActiveTrolleyUnits(location);
		if (trolleys.isEmpty() && Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE)) {
			EmergencyTrolleyUnit defaultTrolley = new EmergencyTrolleyUnit();
			base(defaultTrolley);
			defaultTrolley.setLocation(location);
			defaultTrolley.setTrolleyName("Default Emergency Trolley");
			defaultTrolley.setTrolleyCode("DEFAULT");
			defaultTrolley.setDescription("Default trolley for this location");
			defaultTrolley.setActive(true);
			service.saveTrolleyUnit(defaultTrolley);
			trolleys = service.getActiveTrolleyUnits(location);
		}
		EmergencyTrolleyUnit selectedTrolley = selectTrolley(trolleys, trolleyId);
		model.addAttribute("trolleys", trolleys);
		model.addAttribute("selectedTrolley", selectedTrolley);
		model.addAttribute("items", selectedTrolley == null ? new java.util.ArrayList<EmergencyTrolleyCatalogItem>()
		        : service.getActiveTrolleyCatalog(selectedTrolley));
		List<EmergencyTrolleyHandover> trolleyHandovers = new java.util.ArrayList<EmergencyTrolleyHandover>();
		for (EmergencyTrolleyHandover candidate : service.getTrolleyHandovers(location, 100))
			if (selectedTrolley == null
			        || (candidate.getTrolley() != null && selectedTrolley.getId().equals(candidate.getTrolley().getId())))
				trolleyHandovers.add(candidate);
		model.addAttribute("handovers", trolleyHandovers);
		
		model.addAttribute("draftMaxAgeHours", PatienthandoverConfig.draftMaxAgeHours());
		model.addAttribute("canManage", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute("canCreate", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CREATE));
		model.addAttribute("canReceive", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE));
		model.addAttribute(
		    "canEdit",
		    Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EDIT)
		            || Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute(
		    "canCancel",
		    Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CANCEL)
		            || Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		EmergencyTrolleyHandover pending = null;
		for (EmergencyTrolleyHandover candidate : trolleyHandovers)
			if ("PENDING".equals(candidate.getStatus())) {
				pending = candidate;
				break;
			}
		List<HandoverTeamMember> team = pending == null ? new java.util.ArrayList<HandoverTeamMember>() : service
		        .getHandoverTeam("TROLLEY", pending.getUuid());
		User currentUser = Context.getAuthenticatedUser();
		boolean ownerOrManager = pending != null
		        && ((currentUser != null && pending.getCreator() != null && currentUser.getUserId().equals(
		            pending.getCreator().getUserId())) || Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute("canEdit", ownerOrManager);
		model.addAttribute("canCancel", ownerOrManager);
		HandoverTeamMember assignment = HandoverTeamSupport.assignment(team, Context.getAuthenticatedUser());
		model.addAttribute("handoverTeam", team);
		model.addAttribute("teamMode", !team.isEmpty());
		model.addAttribute("teamSignatureRole", assignment == null ? null : assignment.getRole());
		model.addAttribute("canSignTeam", HandoverTeamSupport.maySign(assignment, team));
		model.addAttribute("sendersSigned", HandoverTeamSupport.count(team, HandoverTeamSupport.OUTGOING, true));
		model.addAttribute("sendersTotal", HandoverTeamSupport.count(team, HandoverTeamSupport.OUTGOING, false));
		model.addAttribute("receiversSigned", HandoverTeamSupport.count(team, HandoverTeamSupport.RECEIVING, true));
		model.addAttribute("receiversTotal", HandoverTeamSupport.count(team, HandoverTeamSupport.RECEIVING, false));
	}
	
	public String post(HttpServletRequest request, UiSessionContext context, Session session,
	        @SpringBean PatienthandoverService service) {
		Location location = context.getSessionLocation();
		if (location == null) {
			session.setAttribute("trolleyError", "Select a login location.");
			return redirect();
		}
		String action = request.getParameter("action");
		try {
			if ("addItem".equals(action)) {
				Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
				addItem(request, location, service);
				message(session, "Trolley item added.");
			} else if ("retireItem".equals(action)) {
				Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
				retire(request, location, service);
				message(session, "Trolley item removed from future handovers.");
			} else if ("importItems".equals(action)) {
				Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
				importItems(request, location, service, session);
			} else if ("submitHandover".equals(action)) {
				Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
				submit(request, location, service);
				session.setAttribute("completedTrolleyDraft", true);
				message(session, "Emergency trolley handover submitted for receipt.");
			} else if ("editPendingItem".equals(action)) {
				editPendingItem(request, location, service);
				message(session, "Trolley item correction saved and audited.");
			} else if ("editTeam".equals(action)) {
				editTeam(request, location, service);
				message(session, "Trolley team updated. Signed members and signature times were retained.");
			} else if ("reassignHandover".equals(action)) {
				reassign(request, location, service);
				message(session, "Emergency trolley handover reassigned.");
			} else if ("cancelHandover".equals(action)) {
				cancel(request, location, service);
				message(session, "Emergency trolley handover cancelled. It remains in the trolley report.");
			} else if ("receiveHandover".equals(action)) {
				Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
				receive(request, location, service);
				message(session,
				    "Your trolley handover signature was recorded. Quantities become current after every assigned team member signs.");
			}
		}
		catch (Exception e) {
			session.setAttribute("trolleyError", e.getMessage());
		}
		return redirect();
	}
	
	private void addItem(HttpServletRequest r, Location l, PatienthandoverService s) {
		String name = req(r, "itemName");
		if (s.findTrolleyCatalogItem(l, name) != null)
			throw new IllegalArgumentException("That item already exists at this location.");
		EmergencyTrolleyCatalogItem i = new EmergencyTrolleyCatalogItem();
		base(i);
		i.setLocation(l);
		i.setItemName(name);
		i.setCurrentQuantity(qty(r.getParameter("startingQuantity"), name));
		i.setStandardNote(r.getParameter("standardNote"));
		i.setActive(true);
		s.saveTrolleyCatalogItem(i);
	}
	
	private void retire(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyCatalogItem i = s.getTrolleyCatalogItem(Integer.valueOf(req(r, "itemId")));
		if (i == null || !i.getLocation().equals(l))
			throw new IllegalArgumentException("Item not found.");
		i.setActive(false);
		s.saveTrolleyCatalogItem(i);
	}
	
	private void importItems(HttpServletRequest r, Location l, PatienthandoverService s, Session session) {
		List<EmergencyTrolleyItem> rows = TrolleySpreadsheetParser.parse(r, "catalog");
		int added = 0, skipped = 0;
		for (EmergencyTrolleyItem row : rows) {
			if (s.findTrolleyCatalogItem(l, row.getItemName()) != null) {
				skipped++;
				continue;
			}
			EmergencyTrolleyCatalogItem i = new EmergencyTrolleyCatalogItem();
			base(i);
			i.setLocation(l);
			i.setItemName(row.getItemName());
			i.setCurrentQuantity(row.getRemainingQuantity());
			i.setMinimumQuantity(row.getMinimumQuantity() == null ? 0.0 : row.getMinimumQuantity());
			i.setStandardNote(row.getUnit());
			i.setActive(true);
			s.saveTrolleyCatalogItem(i);
			added++;
		}
		EmergencyTrolleyImport audit = new EmergencyTrolleyImport();
		base(audit);
		audit.setLocation(l);
		audit.setFileName("uploaded spreadsheet");
		audit.setTotalRows(rows.size());
		audit.setItemsAdded(added);
		audit.setItemsSkipped(skipped);
		audit.setItemsFailed(0);
		audit.setStatus("COMPLETED");
		s.saveTrolleyImport(audit);
		message(session, added + " trolley item(s) imported; " + skipped + " duplicate(s) skipped.");
	}
	
	private void submit(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyUnit selectedTrolley = requireTrolley(r, l, s);
		for (EmergencyTrolleyHandover existing : s.getTrolleyHandovers(l, 100))
			if ("PENDING".equals(existing.getStatus()))
				throw new IllegalArgumentException("Receive the existing pending trolley handover before creating another.");
		String shift = req(r, "shift");
		if (!"DAY".equals(shift) && !"NIGHT".equals(shift))
			throw new IllegalArgumentException("Select DAY or NIGHT shift.");
		List<Provider> receivers = selectedProviders(r.getParameterValues("receivingProviderIds"));
		List<Provider> senders = selectedProviders(r.getParameterValues("outgoingProviderIds"));
		if (receivers.isEmpty())
			throw new IllegalArgumentException("Add at least one receiving team member.");
		List<EmergencyTrolleyCatalogItem> catalog = s.getActiveTrolleyCatalog(selectedTrolley);
		if (catalog.isEmpty())
			throw new IllegalArgumentException("Add trolley items before making a handover.");
		EmergencyTrolleyHandover h = new EmergencyTrolleyHandover();
		base(h);
		h.setLocation(l);
		h.setTrolley(selectedTrolley);
		h.setShift(shift);
		h.setReceivingProvider(receivers.get(0));
		h.setStatus("PENDING");
		h.setDateSubmitted(new Date());
		for (EmergencyTrolleyCatalogItem item : catalog) {
			EmergencyTrolleyHandoverItem x = new EmergencyTrolleyHandoverItem();
			base(x);
			x.setCatalogItem(item);
			x.setItemNameSnapshot(item.getItemName());
			x.setPreviousQuantity(item.getCurrentQuantity());
			Double handed = qty(r.getParameter("quantity_" + item.getId()), item.getItemName());
			if (item.getMaximumQuantity() != null && handed > item.getMaximumQuantity())
				throw new IllegalArgumentException(item.getItemName() + " cannot exceed its maximum quantity of "
				        + item.getMaximumQuantity() + ".");
			x.setHandedQuantity(handed);
			x.setMinimumQuantitySnapshot(item.getMinimumQuantity());
			x.setMaximumQuantitySnapshot(item.getMaximumQuantity());
			x.setExpirationDateSnapshot(item.getExpirationDate());
			x.setNote(r.getParameter("note_" + item.getId()));
			h.addItem(x);
		}
		User creator = Context.getAuthenticatedUser();
		s.saveTrolleyHandover(h, HandoverTeamSupport.newTeam("TROLLEY", h.getUuid(),
		    creator == null ? null : creator.getPerson(), HandoverTeamSupport.people(senders),
		    HandoverTeamSupport.people(receivers)));
	}
	
	private void editPendingItem(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyHandover h = s.getTrolleyHandoverByUuid(req(r, "handoverUuid"));
		User user = Context.getAuthenticatedUser();
		boolean creatorCanEdit = h != null && h.getCreator() != null && user != null
		        && h.getCreator().getUserId().equals(user.getUserId());
		if (h == null || !h.getLocation().equals(l) || !"PENDING".equals(h.getStatus())
		        || (!creatorCanEdit && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE)))
			throw new IllegalArgumentException(
			        "Only authorized outgoing staff or an administrator may edit a pending trolley handover.");
		String itemUuid = req(r, "handoverItemUuid");
		EmergencyTrolleyHandoverItem selected = null;
		for (EmergencyTrolleyHandoverItem item : h.getItems())
			if (itemUuid.equals(item.getUuid()))
				selected = item;
		if (selected == null)
			throw new IllegalArgumentException("The trolley item was not found in this handover.");
		String reason = req(r, "editReason");
		Double newQuantity = qty(r.getParameter("handedQuantity"), selected.getItemNameSnapshot());
		String previous = "Previous quantity=" + selected.getHandedQuantity() + "; previous note=" + selected.getNote();
		selected.setHandedQuantity(newQuantity);
		selected.setNote(r.getParameter("note"));
		selected.setChangedBy(user);
		selected.setDateChanged(new Date());
		h.setChangedBy(user);
		h.setDateChanged(new Date());
		s.saveTrolleyHandover(h);
		PatientHandoverAuditSupport.record(s, "TROLLEY_ITEM_AMENDED", h.getTransactionReference(), h.getUuid(), itemUuid,
		    true, "Trolley item edited. Reason: " + reason + ". " + previous);
	}
	
	private void editTeam(HttpServletRequest r, Location location, PatienthandoverService service) {
		EmergencyTrolleyHandover handover = service.getTrolleyHandoverByUuid(req(r, "handoverUuid"));
		User user = Context.getAuthenticatedUser();
		boolean allowed = handover != null
		        && "PENDING".equals(handover.getStatus())
		        && handover.getLocation().equals(location)
		        && (Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE) || (user != null
		                && handover.getCreator() != null && user.getUserId().equals(handover.getCreator().getUserId())));
		if (!allowed)
			throw new IllegalArgumentException("Only the handover creator or a manager may edit a pending trolley team.");
		String reason = req(r, "editReason");
		List<HandoverTeamMember> team = service.getHandoverTeam("TROLLEY", handover.getUuid());
		List<Provider> senders = selectedProviders(r.getParameterValues("outgoingProviderIds"));
		List<Provider> receivers = selectedProviders(r.getParameterValues("receivingProviderIds"));
		if (!hasSignedRole(team, HandoverTeamSupport.OUTGOING) && senders.isEmpty())
			throw new IllegalArgumentException("The sending team must contain at least one member.");
		if (!hasSignedRole(team, HandoverTeamSupport.RECEIVING) && receivers.isEmpty())
			throw new IllegalArgumentException("The receiving team must contain at least one member.");
		Date now = new Date();
		for (HandoverTeamMember member : team)
			if (!member.isSigned() && !Boolean.TRUE.equals(member.getVoided())) {
				member.setVoided(true);
				member.setVoidedBy(user);
				member.setDateVoided(now);
				member.setVoidReason("Team changed: " + reason);
				service.saveHandoverTeamMember(member);
			}
		for (Provider provider : senders)
			addTrolleyTeamMember(service, team, handover.getUuid(), provider, HandoverTeamSupport.OUTGOING);
		for (Provider provider : receivers)
			addTrolleyTeamMember(service, team, handover.getUuid(), provider, HandoverTeamSupport.RECEIVING);
		if (!receivers.isEmpty())
			handover.setReceivingProvider(receivers.get(0));
		List<HandoverTeamMember> updated = service.getHandoverTeam("TROLLEY", handover.getUuid());
		if (HandoverTeamSupport.allSigned(updated, HandoverTeamSupport.OUTGOING)
		        && HandoverTeamSupport.allSigned(updated, HandoverTeamSupport.RECEIVING)) {
			service.receiveTrolleyHandover(handover);
		} else {
			handover.setChangedBy(user);
			handover.setDateChanged(now);
			service.saveTrolleyHandover(handover);
		}
		PatientHandoverAuditSupport.record(service, "TROLLEY_TEAM_AMENDED", handover.getTransactionReference(),
		    handover.getUuid(), null, true, "Trolley team changed. Reason: " + reason);
	}
	
	private boolean hasSignedRole(List<HandoverTeamMember> team, String role) {
		for (HandoverTeamMember member : team)
			if (!Boolean.TRUE.equals(member.getVoided()) && member.isSigned() && role.equals(member.getRole()))
				return true;
		return false;
	}
	
	private void addTrolleyTeamMember(PatienthandoverService service, List<HandoverTeamMember> existing, String key,
	        Provider provider, String role) {
		for (HandoverTeamMember member : existing)
			if (!Boolean.TRUE.equals(member.getVoided()) && role.equals(member.getRole()) && member.getPerson() != null
			        && member.getPerson().getPersonId().equals(provider.getPerson().getPersonId()))
				return;
		service.saveHandoverTeamMember(HandoverTeamSupport.newMember("TROLLEY", key, provider.getPerson(), role, false));
	}
	
	private void reassign(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyHandover h = s.getTrolleyHandoverByUuid(req(r, "handoverUuid"));
		User user = Context.getAuthenticatedUser();
		boolean creatorCanEdit = h != null && h.getCreator() != null && user != null
		        && h.getCreator().getUserId().equals(user.getUserId());
		if (h == null || !h.getLocation().equals(l) || !"PENDING".equals(h.getStatus())
		        || (!creatorCanEdit && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE)))
			throw new IllegalArgumentException(
			        "Only authorized outgoing staff or an administrator may reassign a pending trolley handover.");
		Provider provider = Context.getProviderService().getProvider(Integer.valueOf(req(r, "receivingProviderId")));
		if (provider == null || provider.isRetired())
			throw new IllegalArgumentException("Select an active receiving provider.");
		String reason = req(r, "editReason");
		String previous = h.getReceivingProvider() == null ? "Unassigned" : h.getReceivingProvider().getName();
		h.setReceivingProvider(provider);
		h.setChangedBy(user);
		h.setDateChanged(new Date());
		s.saveTrolleyHandover(h);
		PatientHandoverAuditSupport.record(s, "TROLLEY_REASSIGNED", h.getTransactionReference(), h.getUuid(), null, true,
		    "Receiving provider changed from " + previous + " to " + provider.getName() + ". Reason: " + reason);
	}
	
	private void cancel(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyHandover h = s.getTrolleyHandoverByUuid(req(r, "handoverUuid"));
		User user = Context.getAuthenticatedUser();
		boolean creatorCanCancel = h != null && h.getCreator() != null && user != null
		        && h.getCreator().getUserId().equals(user.getUserId());
		if (h == null || !h.getLocation().equals(l) || !"PENDING".equals(h.getStatus())
		        || (!creatorCanCancel && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE)))
			throw new IllegalArgumentException(
			        "Only an authorized outgoing staff member or administrator may cancel a pending trolley handover.");
		String reason = req(r, "cancellationReason");
		h.setStatus("CANCELLED");
		h.setDateCancelled(new Date());
		h.setCancelledBy(user);
		h.setCancellationReason(reason);
		h.setChangedBy(user);
		h.setDateChanged(new Date());
		s.saveTrolleyHandover(h);
		PatientHandoverAuditSupport.record(s, "TROLLEY_CANCELLED", h.getTransactionReference(), h.getUuid(), null, true,
		    "Emergency trolley handover cancelled. Reason: " + reason);
	}
	
	private void receive(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyHandover h = s.getTrolleyHandoverByUuid(req(r, "handoverUuid"));
		if (h == null || !h.getLocation().equals(l) || !"PENDING".equals(h.getStatus()))
			throw new IllegalArgumentException("Pending trolley handover not found.");
		User user = Context.getAuthenticatedUser();
		List<HandoverTeamMember> team = s.getHandoverTeam("TROLLEY", h.getUuid());
		if (!team.isEmpty()) {
			HandoverTeamMember member = HandoverTeamSupport.assignment(team, user);
			if (!HandoverTeamSupport.maySign(member, team))
				throw new IllegalArgumentException(member == null ? "You are not an unsigned member of this trolley team."
				        : "Receiving team members can sign after every outgoing team member has signed.");
			if (!HandoverTeamSupport.sign(member, s))
				return;
		} else if (h.getReceivingProvider() == null || user == null || user.getPerson() == null
		        || !h.getReceivingProvider().getPerson().getPersonId().equals(user.getPerson().getPersonId()))
			throw new IllegalArgumentException("Only the designated receiving provider can receive this trolley handover.");
		s.receiveTrolleyHandover(h);
	}
	
	private List<Provider> selectedProviders(String[] values) {
		List<Provider> result = new java.util.ArrayList<Provider>();
		if (values == null)
			return result;
		for (String value : values) {
			if (value == null || value.trim().isEmpty())
				continue;
			Provider p;
			try {
				p = Context.getProviderService().getProvider(Integer.valueOf(value.trim()));
			}
			catch (Exception e) {
				p = null;
			}
			if (p != null && !p.isRetired() && p.getPerson() != null) {
				boolean duplicate = false;
				for (Provider x : result)
					if (x.getPerson().getPersonId().equals(p.getPerson().getPersonId()))
						duplicate = true;
				if (!duplicate)
					result.add(p);
			}
		}
		return result;
	}
	
	private EmergencyTrolleyUnit requireTrolley(HttpServletRequest request, Location location, PatienthandoverService service) {
		EmergencyTrolleyUnit trolley = service.getTrolleyUnit(Integer.valueOf(req(request, "trolleyId")));
		if (trolley == null || !location.equals(trolley.getLocation()) || !trolley.isActive() || trolley.isVoided())
			throw new IllegalArgumentException("Select a valid emergency trolley type.");
		return trolley;
	}
	
	private EmergencyTrolleyUnit selectTrolley(List<EmergencyTrolleyUnit> values, Integer id) {
		if (values == null || values.isEmpty())
			return null;
		if (id != null)
			for (EmergencyTrolleyUnit value : values)
				if (id.equals(value.getId()))
					return value;
		return values.get(0);
	}
	
	private void base(org.openmrs.BaseOpenmrsData d) {
		d.setUuid(UUID.randomUUID().toString());
		d.setCreator(Context.getAuthenticatedUser());
		d.setDateCreated(new Date());
	}
	
	private String req(HttpServletRequest r, String n) {
		String v = r.getParameter(n);
		if (v == null || v.trim().isEmpty())
			throw new IllegalArgumentException("Complete all required trolley fields.");
		return v.trim();
	}
	
	private Double qty(String v, String name) {
		try {
			double q = Double.parseDouble(v);
			if (q < 0)
				throw new Exception();
			return q;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Enter a valid non-negative quantity for " + name + ".");
		}
	}
	
	private void message(Session s, String m) {
		s.setAttribute("trolleyMessage", m);
	}
	
	private String redirect() {
		return redirect(null);
	}
	
	private String redirect(String trolleyId) {
		return trolleyId == null || trolleyId.trim().isEmpty() ? "redirect:patienthandover/emergencyTrolley.page"
		        : "redirect:patienthandover/emergencyTrolley.page?trolleyId=" + trolleyId;
	}
}
