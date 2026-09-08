package org.openmrs.module.patienthandover.page.controller;

import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Location;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PharmacyHandover;
import org.openmrs.module.patienthandover.domain.PharmacyHandoverItem;
import org.openmrs.module.patienthandover.domain.PharmacyHandoverParticipant;
import org.openmrs.module.rwandapharmacymanagement.api.InventoryService;
import org.openmrs.module.rwandapharmacymanagement.api.PharmacyService;
import org.openmrs.module.rwandapharmacymanagement.model.InventoryBalance;
import org.openmrs.module.rwandapharmacymanagement.model.PharmacyLocation;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

public class PharmacyHandoverPageController {
	
	public void get(@RequestParam(value = "pharmacyLocationId", required = false) Integer pharmacyLocationId,
	        @RequestParam(value = "stockPage", required = false, defaultValue = "1") Integer stockPage,
	        @RequestParam(value = "handoverPage", required = false, defaultValue = "1") Integer handoverPage,
	        @RequestParam(value = "showAll", required = false, defaultValue = "false") Boolean showAll, PageModel model,
	        UiSessionContext sessionContext, Session session) {
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CREATE)
		        && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE))
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		PharmacyService pharmacyService = resolveService(PharmacyService.class, "pharmacyService");
		InventoryService inventoryService = resolveService(InventoryService.class, "inventoryService");
		Location loginLocation = sessionContext.getSessionLocation();
		List<PharmacyLocation> locations = activeLocations(pharmacyService.getAllPharmacyLocations());
		PharmacyLocation selected = selectLocation(pharmacyService, locations, pharmacyLocationId, loginLocation);
		model.addAttribute("currentLocation", loginLocation);
		model.addAttribute("outgoingUser", Context.getAuthenticatedUser());
		model.addAttribute("providers", Context.getProviderService().getAllProviders(false));
		model.addAttribute("pharmacyLocations", locations);
		model.addAttribute("selectedPharmacyLocation", selected);
		model.addAttribute("canCreate", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CREATE));
		model.addAttribute("canReceive", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE));
		model.addAttribute("canManagePharmacy", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute(
		    "canEditPharmacy",
		    Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EDIT)
		            || Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute(
		    "canCancelPharmacy",
		    Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CANCEL)
		            || Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE));
		model.addAttribute("showAll", Boolean.TRUE.equals(showAll));
		Map<Integer, List<InventoryBalance>> editBalances = new LinkedHashMap<Integer, List<InventoryBalance>>();
		for (PharmacyLocation pharmacyLocation : locations)
			editBalances.put(pharmacyLocation.getPharmacyLocationId(), inventory(pharmacyLocation, inventoryService));
		model.addAttribute("pharmacyEditBalances", editBalances);
		addHandovers(model, loginLocation, handoverPage == null ? 1 : handoverPage);
		copyFlash(session, model, "pharmacyHandoverMessage");
		copyFlash(session, model, "pharmacyHandoverError");
		if (selected == null) {
			model.addAttribute("inventoryBalances", Collections.emptyList());
			if (locations.isEmpty())
				model.addAttribute("pharmacyHandoverError", "No active pharmacy locations are configured.");
			return;
		}
		List<InventoryBalance> balances = inventory(selected, inventoryService);
		model.addAttribute("inventoryBalances", balances);
		model.addAttribute(
		    "displayedInventoryBalances",
		    Boolean.TRUE.equals(showAll) ? balances : PaginationSupport.list(balances, stockPage == null ? 1 : stockPage,
		        model, "pharmacyStock"));
	}
	
	public String post(HttpServletRequest request, Session session) {
		if (request.getParameter("editPharmacyHandoverUuid") != null)
			return editPending(request.getParameter("editPharmacyHandoverUuid"), request, session);
		if (request.getParameter("cancelPharmacyHandoverUuid") != null)
			return cancel(request.getParameter("cancelPharmacyHandoverUuid"), request.getParameter("cancellationReason"),
			    session);
		if (request.getParameter("signHandoverUuid") != null)
			return sign(request.getParameter("signHandoverUuid"), session);
		if (request.getParameter("receiveHandoverUuid") != null)
			return receive(request.getParameter("receiveHandoverUuid"), session);
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		Integer pharmacyId = integer(request.getParameter("pharmacyLocationId"));
		List<Provider> receivingTeam = providers(request.getParameterValues("receivingProviderIds"));
		if (receivingTeam.isEmpty())
			receivingTeam = providers(request.getParameterValues("receivingProviderId"));
		List<Provider> outgoingTeam = providers(request.getParameterValues("outgoingProviderIds"));
		Provider receiving = receivingTeam.isEmpty() ? null : receivingTeam.get(0);
		String shift = request.getParameter("shift");
		String redirect = "redirect:patienthandover/pharmacyHandover.page"
		        + (pharmacyId == null ? "" : "?pharmacyLocationId=" + pharmacyId);
		if (pharmacyId == null || (!"DAY".equals(shift) && !"NIGHT".equals(shift))) {
			session.setAttribute("pharmacyHandoverError", "Select a pharmacy and shift.");
			return redirect;
		}
		if (receiving == null) {
			session.setAttribute("pharmacyHandoverError", "Select at least one valid receiving team member.");
			return redirect;
		}
		PharmacyService pharmacyService = resolveService(PharmacyService.class, "pharmacyService");
		InventoryService inventoryService = resolveService(InventoryService.class, "inventoryService");
		PharmacyLocation pharmacy = pharmacyService.getPharmacyLocation(pharmacyId);
		if (pharmacy == null || Boolean.FALSE.equals(pharmacy.getActive())) {
			session.setAttribute("pharmacyHandoverError", "Select a valid active pharmacy.");
			return redirect;
		}
		List<InventoryBalance> available = inventory(pharmacy, inventoryService);
		Map<Integer, InventoryBalance> byId = new LinkedHashMap<Integer, InventoryBalance>();
		for (InventoryBalance b : available)
			byId.put(b.getInventoryBalanceId(), b);
		List<InventoryBalance> chosen = new ArrayList<InventoryBalance>();
		if ("all".equals(request.getParameter("submitMode")))
			chosen.addAll(available);
		else {
			String[] ids = request.getParameterValues("handoverBalanceId");
			if (ids != null)
				for (String id : ids) {
					InventoryBalance b = byId.get(integer(id));
					if (b != null && !chosen.contains(b))
						chosen.add(b);
				}
		}
		if (chosen.isEmpty()) {
			session.setAttribute("pharmacyHandoverError", "Select at least one available stock item.");
			return redirect;
		}
		PatienthandoverService service = Context.getService(PatienthandoverService.class);
		PharmacyHandover handover = new PharmacyHandover();
		handover.setUuid(UUID.randomUUID().toString());
		handover.setPharmacyLocationId(pharmacy.getPharmacyLocationId());
		handover.setPharmacyName(pharmacy.getName());
		handover.setLocation(Context.getUserContext().getLocation());
		handover.setShift(shift);
		handover.setReceivingProvider(receiving);
		handover.setDateSubmitted(new Date());
		handover.setCreator(Context.getAuthenticatedUser());
		handover.setDateCreated(new Date());
		addParticipant(handover, Context.getAuthenticatedUser().getPerson(), "OUTGOING", true);
		for (Provider provider : outgoingTeam)
			addParticipant(handover, provider.getPerson(), "OUTGOING",
			    samePerson(provider.getPerson(), Context.getAuthenticatedUser().getPerson()));
		for (Provider provider : receivingTeam)
			addParticipant(handover, provider.getPerson(), "RECEIVING", false);
		for (InventoryBalance b : chosen) {
			PharmacyHandoverItem item = new PharmacyHandoverItem();
			item.setUuid(UUID.randomUUID().toString());
			item.setInventoryBalanceId(b.getInventoryBalanceId());
			item.setItemName(b.getItem().getName());
			item.setItemCode(b.getItem().getCode());
			item.setBatchNumber(b.getBatchNumber());
			item.setExpiryDate(b.getExpiryDate());
			item.setAvailableQuantity(b.getQuantityOnHand());
			item.setCreator(Context.getAuthenticatedUser());
			item.setDateCreated(new Date());
			handover.addItem(item);
		}
		service.savePharmacyHandover(handover);
		PatientHandoverAuditSupport.record(service, "PHARMACY_HANDOVER_SUBMITTED", handover.getUuid(), handover.getUuid(),
		    null, true, chosen.size() + " pharmacy stock item(s) submitted.");
		session.setAttribute("pharmacyHandoverMessage", chosen.size() + " pharmacy stock item(s) submitted to "
		        + receivingTeam.size() + " receiving team member(s) for the " + shift.toLowerCase() + " shift.");
		return redirect;
	}
	
	private String editPending(String uuid, HttpServletRequest request, Session session) {
		PatienthandoverService service = Context.getService(PatienthandoverService.class);
		PharmacyHandover handover = service.getPharmacyHandoverByUuid(uuid);
		User user = Context.getAuthenticatedUser();
		if (!canChange(handover, user, PatienthandoverConfig.PRIVILEGE_EDIT)) {
			session.setAttribute("pharmacyHandoverError",
			    "Only the handover creator or a handover manager may edit this pending handover.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		String mode = request.getParameter("editMode");
		String reason = request.getParameter("editReason");
		if (reason == null || reason.trim().isEmpty()) {
			session.setAttribute("pharmacyHandoverError", "Enter a reason for the change.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		String changeSummary;
		if ("handover".equals(mode)) {
			String shift = request.getParameter("shift");
			if (!"DAY".equals(shift) && !"NIGHT".equals(shift)) {
				session.setAttribute("pharmacyHandoverError", "Select DAY or NIGHT shift.");
				return "redirect:patienthandover/pharmacyHandover.page";
			}
			String previousShift = handover.getShift();
			handover.setShift(shift);
			PharmacyService pharmacyService = resolveService(PharmacyService.class, "pharmacyService");
			InventoryService inventoryService = resolveService(InventoryService.class, "inventoryService");
			PharmacyLocation pharmacy = pharmacyService.getPharmacyLocation(handover.getPharmacyLocationId());
			List<InventoryBalance> available = pharmacy == null ? Collections.<InventoryBalance> emptyList() : inventory(
			    pharmacy, inventoryService);
			Map<Integer, InventoryBalance> availableById = new LinkedHashMap<Integer, InventoryBalance>();
			for (InventoryBalance balance : available)
				availableById.put(balance.getInventoryBalanceId(), balance);
			List<InventoryBalance> selectedItems = new ArrayList<InventoryBalance>();
			String[] selectedIds = request.getParameterValues("editBalanceIds");
			if (selectedIds != null)
				for (String selectedId : selectedIds) {
					InventoryBalance balance = availableById.get(integer(selectedId));
					if (balance != null && !selectedItems.contains(balance))
						selectedItems.add(balance);
				}
			if (selectedItems.isEmpty()) {
				session.setAttribute("pharmacyHandoverError", "Select at least one available stock item.");
				return "redirect:patienthandover/pharmacyHandover.page";
			}
			int previousItems = handover.getItems().size();
			handover.getItems().clear();
			for (InventoryBalance balance : selectedItems) {
				PharmacyHandoverItem item = new PharmacyHandoverItem();
				item.setUuid(UUID.randomUUID().toString());
				item.setInventoryBalanceId(balance.getInventoryBalanceId());
				item.setItemName(balance.getItem().getName());
				item.setItemCode(balance.getItem().getCode());
				item.setBatchNumber(balance.getBatchNumber());
				item.setExpiryDate(balance.getExpiryDate());
				item.setAvailableQuantity(balance.getQuantityOnHand());
				item.setCreator(user);
				item.setDateCreated(new Date());
				handover.addItem(item);
			}
			changeSummary = "Shift changed from " + previousShift + " to " + shift + "; stock lines changed from "
			        + previousItems + " to " + selectedItems.size();
		} else if ("team".equals(mode)) {
			List<Provider> receivers = providers(request.getParameterValues("receivingProviderIds"));
			List<Provider> senders = providers(request.getParameterValues("outgoingProviderIds"));
			boolean hasSignedReceiver = false;
			for (PharmacyHandoverParticipant participant : handover.getParticipants())
				if ("RECEIVING".equals(participant.getRole()) && participant.isSigned())
					hasSignedReceiver = true;
			if (receivers.isEmpty() && !hasSignedReceiver) {
				session.setAttribute("pharmacyHandoverError", "Select at least one receiving team member.");
				return "redirect:patienthandover/pharmacyHandover.page";
			}
			for (java.util.Iterator<PharmacyHandoverParticipant> it = handover.getParticipants().iterator(); it.hasNext();) {
				PharmacyHandoverParticipant participant = it.next();
				if (!participant.isSigned())
					it.remove();
			}
			for (Provider sender : senders)
				addParticipant(handover, sender.getPerson(), "OUTGOING", false);
			for (Provider receiver : receivers)
				addParticipant(handover, receiver.getPerson(), "RECEIVING", false);
			if (!receivers.isEmpty())
				handover.setReceivingProvider(receivers.get(0));
			changeSummary = "Sending and receiving teams updated";
		} else {
			session.setAttribute("pharmacyHandoverError", "Select a valid handover edit action.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		boolean completedByEdit = reconcileCompletion(handover, user);
		handover.setChangedBy(user);
		handover.setDateChanged(new Date());
		service.savePharmacyHandover(handover);
		PatientHandoverAuditSupport.record(service, "PHARMACY_HANDOVER_AMENDED", uuid, uuid, null, true, changeSummary
		        + ". Reason: " + reason.trim());
		session.setAttribute("pharmacyHandoverMessage",
		    completedByEdit ? "The handover was updated and marked RECEIVED because all assigned team members have signed."
		            : "The pending pharmacy handover was updated and audited.");
		return "redirect:patienthandover/pharmacyHandover.page";
	}
	
	private String cancel(String uuid, String reason, Session session) {
		PatienthandoverService service = Context.getService(PatienthandoverService.class);
		PharmacyHandover handover = service.getPharmacyHandoverByUuid(uuid);
		User user = Context.getAuthenticatedUser();
		if (!canChange(handover, user, PatienthandoverConfig.PRIVILEGE_CANCEL)) {
			session.setAttribute("pharmacyHandoverError",
			    "Only authorized outgoing staff or an administrator may cancel this pending handover.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		if (reason == null || reason.trim().isEmpty()) {
			session.setAttribute("pharmacyHandoverError", "Enter a cancellation reason.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		Date now = new Date();
		handover.setDateCancelled(now);
		handover.setCancelledBy(user);
		handover.setCancellationReason(reason.trim());
		handover.setChangedBy(user);
		handover.setDateChanged(now);
		service.savePharmacyHandover(handover);
		PatientHandoverAuditSupport.record(service, "PHARMACY_HANDOVER_CANCELLED", uuid, uuid, null, true,
		    "Pharmacy handover cancelled. Reason: " + reason.trim());
		session.setAttribute("pharmacyHandoverMessage", "Pharmacy handover cancelled; it remains in reports.");
		return "redirect:patienthandover/pharmacyHandover.page";
	}
	
	private boolean canChange(PharmacyHandover handover, User user, String privilege) {
		if (handover == null || handover.isAcknowledged() || handover.isCancelled() || user == null
		        || handover.getLocation() == null || Context.getUserContext().getLocation() == null
		        || !handover.getLocation().getLocationId().equals(Context.getUserContext().getLocation().getLocationId()))
			return false;
		if (Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE))
			return true;
		return handover.getCreator() != null && handover.getCreator().getUserId().equals(user.getUserId());
	}
	
	private boolean hasRole(PharmacyHandover handover, String role) {
		for (PharmacyHandoverParticipant participant : handover.getParticipants())
			if (role.equals(participant.getRole()) && !participant.getVoided())
				return true;
		return false;
	}
	
	private boolean reconcileCompletion(PharmacyHandover handover, User user) {
		if (handover == null || handover.isAcknowledged() || handover.isCancelled() || !hasRole(handover, "OUTGOING")
		        || !hasRole(handover, "RECEIVING") || !handover.allSigned("OUTGOING") || !handover.allSigned("RECEIVING"))
			return false;
		Date now = new Date();
		handover.setAcknowledged(true);
		handover.setAcknowledgedBy(user);
		handover.setDateAcknowledged(now);
		handover.setChangedBy(user);
		handover.setDateChanged(now);
		return true;
	}
	
	private String sign(String uuid, Session session) {
		PatienthandoverService service = Context.getService(PatienthandoverService.class);
		PharmacyHandover handover = service.getPharmacyHandoverByUuid(uuid);
		User user = Context.getAuthenticatedUser();
		Location login = Context.getUserContext().getLocation();
		if (handover == null || handover.isAcknowledged() || login == null || handover.getLocation() == null
		        || !login.getLocationId().equals(handover.getLocation().getLocationId()) || user == null
		        || user.getPerson() == null) {
			session.setAttribute("pharmacyHandoverError", "This pharmacy team assignment is not available.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		PharmacyHandoverParticipant assignment = null;
		for (PharmacyHandoverParticipant participant : handover.getParticipants())
			if (!participant.isSigned() && samePerson(participant.getPerson(), user.getPerson())) {
				if ("OUTGOING".equals(participant.getRole()) || handover.allSigned("OUTGOING")) {
					assignment = participant;
					break;
				}
			}
		if (assignment == null) {
			session.setAttribute("pharmacyHandoverError",
			    handover.allSigned("OUTGOING") ? "You have no unsigned assignment on this handover."
			            : "The receiving team can sign after every outgoing team member has signed.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		if ("OUTGOING".equals(assignment.getRole()))
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		else
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
		Date now = new Date();
		assignment.setSigned(true);
		assignment.setSignedBy(user);
		assignment.setDateSigned(now);
		assignment.setChangedBy(user);
		assignment.setDateChanged(now);
		if ("RECEIVING".equals(assignment.getRole()) && handover.allSigned("RECEIVING")) {
			handover.setAcknowledged(true);
			handover.setAcknowledgedBy(user);
			handover.setDateAcknowledged(now);
		}
		handover.setChangedBy(user);
		handover.setDateChanged(now);
		service.savePharmacyHandover(handover);
		PatientHandoverAuditSupport.record(service, "PHARMACY_" + assignment.getRole() + "_SIGNED", uuid, uuid, null, true,
		    user.getPerson().getPersonName().getFullName() + " signed the pharmacy handover.");
		session.setAttribute("pharmacyHandoverMessage", "Your " + assignment.getRole().toLowerCase()
		        + " team signature was recorded.");
		return "redirect:patienthandover/pharmacyHandover.page";
	}
	
	private List<Provider> providers(String[] ids) {
		List<Provider> result = new ArrayList<Provider>();
		Set<Integer> seen = new LinkedHashSet<Integer>();
		if (ids != null)
			for (String id : ids) {
				Integer providerId = integer(id);
				Provider provider = providerId == null ? null : Context.getProviderService().getProvider(providerId);
				if (provider != null && !Boolean.TRUE.equals(provider.getRetired()) && provider.getPerson() != null
				        && seen.add(provider.getPerson().getPersonId()))
					result.add(provider);
			}
		return result;
	}
	
	private void addParticipant(PharmacyHandover handover, Person person, String role, boolean signed) {
		if (person == null)
			return;
		for (PharmacyHandoverParticipant existing : handover.getParticipants())
			if (role.equals(existing.getRole()) && samePerson(person, existing.getPerson()))
				return;
		Date now = new Date();
		PharmacyHandoverParticipant participant = new PharmacyHandoverParticipant();
		participant.setUuid(UUID.randomUUID().toString());
		participant.setPerson(person);
		participant.setRole(role);
		participant.setSigned(signed);
		participant.setSignedBy(signed ? Context.getAuthenticatedUser() : null);
		participant.setDateSigned(signed ? now : null);
		participant.setCreator(Context.getAuthenticatedUser());
		participant.setDateCreated(now);
		handover.addParticipant(participant);
	}
	
	private boolean samePerson(Person left, Person right) {
		return left != null && right != null && left.getPersonId().equals(right.getPersonId());
	}
	
	private String receive(String uuid, Session session) {
		PatienthandoverService service = Context.getService(PatienthandoverService.class);
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE)) {
			PatientHandoverAuditSupport.record(service, "UNAUTHORIZED_ATTEMPT", uuid, uuid, null, false,
			    "Attempted to receive a pharmacy handover without Receive privilege.");
			Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
		}
		PharmacyHandover handover = service.getPharmacyHandoverByUuid(uuid);
		User user = Context.getAuthenticatedUser();
		Location loginLocation = Context.getUserContext().getLocation();
		if (handover == null || handover.isAcknowledged() || handover.getLocation() == null || loginLocation == null
		        || !handover.getLocation().getLocationId().equals(loginLocation.getLocationId())
		        || !assigned(handover, user)) {
			PatientHandoverAuditSupport.record(service, "UNAUTHORIZED_ATTEMPT", uuid, uuid, null, false,
			    "Non-designated provider attempted pharmacy handover acknowledgement.");
			session.setAttribute("pharmacyHandoverError",
			    "Only the designated receiving provider may mark this pending handover as received.");
			return "redirect:patienthandover/pharmacyHandover.page";
		}
		Date now = new Date();
		handover.setAcknowledged(true);
		handover.setAcknowledgedBy(user);
		handover.setDateAcknowledged(now);
		handover.setChangedBy(user);
		handover.setDateChanged(now);
		service.savePharmacyHandover(handover);
		PatientHandoverAuditSupport.record(service, "PHARMACY_HANDOVER_RECEIVED", uuid, uuid, null, true,
		    "Entire pharmacy handover marked as received.");
		session.setAttribute("pharmacyHandoverMessage", "Pharmacy handover marked as received.");
		return "redirect:patienthandover/pharmacyHandover.page";
	}
	
	private void addHandovers(PageModel model, Location location, int requestedPage) {
		PatienthandoverService service = Context.getService(PatienthandoverService.class);
		User user = Context.getAuthenticatedUser();
		List<PharmacyHandover> visible = new ArrayList<PharmacyHandover>();
		Map<String, PharmacyHandover> unique = new LinkedHashMap<String, PharmacyHandover>();
		if (user != null && user.getPerson() != null) {
			for (PharmacyHandover h : service.getPendingPharmacyTeamHandovers(location, user.getPerson(), 500))
				unique.put(h.getUuid(), h);
			for (PharmacyHandover h : service.getPendingPharmacyHandovers(location, user.getPerson(), 500))
				unique.put(h.getUuid(), h);
		}
		for (PharmacyHandover h : service.getPharmacyHandovers(location, 500)) {
			boolean creator = user != null && h.getCreator() != null && h.getCreator().getUserId().equals(user.getUserId());
			boolean manager = Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
			if (!h.isAcknowledged() && !h.isCancelled() && (creator || manager)) {
				if (reconcileCompletion(h, user)) {
					service.savePharmacyHandover(h);
					PatientHandoverAuditSupport.record(service, "PHARMACY_HANDOVER_AUTO_COMPLETED", h.getUuid(),
					    h.getUuid(), null, true,
					    "Status reconciled after team changes because all assigned senders and receivers had signed.");
				} else
					unique.put(h.getUuid(), h);
			}
		}
		visible.addAll(unique.values());
		model.addAttribute("pharmacyHandovers", PaginationSupport.list(visible, requestedPage, model, "pharmacyHandover"));
		Map<String, String> ages = new LinkedHashMap<String, String>();
		long now = System.currentTimeMillis();
		for (PharmacyHandover h : visible) {
			long hours = Math.max(0, (now - h.getDateSubmitted().getTime()) / 3600000L);
			ages.put(h.getUuid(), hours >= 24 ? (hours / 24) + " day(s)" : hours + " hour(s)");
		}
		model.addAttribute("pharmacyHandoverAges", ages);
		Map<String, String> senderProgress = new LinkedHashMap<String, String>();
		Map<String, String> receiverProgress = new LinkedHashMap<String, String>();
		Map<String, String> signatureActions = new LinkedHashMap<String, String>();
		for (PharmacyHandover h : visible) {
			int senders = 0, sendersSigned = 0, receivers = 0, receiversSigned = 0;
			for (PharmacyHandoverParticipant p : h.getParticipants()) {
				if ("OUTGOING".equals(p.getRole())) {
					senders++;
					if (p.isSigned())
						sendersSigned++;
				}
				if ("RECEIVING".equals(p.getRole())) {
					receivers++;
					if (p.isSigned())
						receiversSigned++;
				}
				if (!p.isSigned() && samePerson(p.getPerson(), user.getPerson())) {
					if ("OUTGOING".equals(p.getRole()))
						signatureActions.put(h.getUuid(), "OUTGOING");
					else if (h.allSigned("OUTGOING") && !signatureActions.containsKey(h.getUuid()))
						signatureActions.put(h.getUuid(), "RECEIVING");
				}
			}
			senderProgress.put(h.getUuid(), senders == 0 ? "Legacy sender" : sendersSigned + "/" + senders + " signed");
			receiverProgress.put(h.getUuid(), receivers == 0 ? "Legacy receiver" : receiversSigned + "/" + receivers
			        + " signed");
		}
		model.addAttribute("pharmacySenderProgress", senderProgress);
		model.addAttribute("pharmacyReceiverProgress", receiverProgress);
		model.addAttribute("pharmacySignatureActions", signatureActions);
	}
	
	private boolean assigned(PharmacyHandover h, User user) {
		return h != null && h.getReceivingProvider() != null && h.getReceivingProvider().getPerson() != null && user != null
		        && user.getPerson() != null
		        && h.getReceivingProvider().getPerson().getPersonId().equals(user.getPerson().getPersonId());
	}
	
	private List<InventoryBalance> inventory(PharmacyLocation p, InventoryService s) {
		List<InventoryBalance> rows = s.getAvailableInventoryForPharmacy(p.getPharmacyLocationId());
		rows = rows == null ? new ArrayList<InventoryBalance>() : new ArrayList<InventoryBalance>(rows);
		Collections.sort(rows, INVENTORY_ORDER);
		return rows;
	}
	
	private <T> T resolveService(Class<T> type, String bean) {
		try {
			return Context.getService(type);
		}
		catch (RuntimeException e) {
			return Context.getRegisteredComponent(bean, type);
		}
	}
	
	private List<PharmacyLocation> activeLocations(List<PharmacyLocation> all) {
		List<PharmacyLocation> r = new ArrayList<PharmacyLocation>();
		if (all != null)
			for (PharmacyLocation p : all)
				if (p != null && !Boolean.FALSE.equals(p.getActive()))
					r.add(p);
		Collections.sort(r, new Comparator<PharmacyLocation>() {
			
			public int compare(PharmacyLocation a, PharmacyLocation b) {
				return text(a.getName()).compareToIgnoreCase(text(b.getName()));
			}
		});
		return r;
	}
	
	private PharmacyLocation selectLocation(PharmacyService s, List<PharmacyLocation> all, Integer id, Location login) {
		if (id != null) {
			for (PharmacyLocation p : all)
				if (id.equals(p.getPharmacyLocationId()))
					return p;
			return null;
		}
		if (login != null) {
			PharmacyLocation p = s.getPharmacyLocationByLocationId(login.getLocationId());
			if (p != null && !Boolean.FALSE.equals(p.getActive()))
				return p;
		}
		return all.size() == 1 ? all.get(0) : null;
	}
	
	private void copyFlash(Session s, PageModel m, String key) {
		String v = s.getAttribute(key, String.class);
		if (v != null) {
			m.addAttribute(key, v);
			s.setAttribute(key, null);
		}
	}
	
	private Integer integer(String v) {
		try {
			return v == null ? null : Integer.valueOf(v);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}
	
	private static String text(String v) {
		return v == null ? "" : v;
	}
	
	private static final Comparator<InventoryBalance> INVENTORY_ORDER = new Comparator<InventoryBalance>() {
		
		public int compare(InventoryBalance a, InventoryBalance b) {
			String x = a == null || a.getItem() == null ? "" : text(a.getItem().getName()), y = b == null
			        || b.getItem() == null ? "" : text(b.getItem().getName());
			int c = x.compareToIgnoreCase(y);
			return c != 0 ? c : text(a == null ? null : a.getBatchNumber()).compareToIgnoreCase(
			    text(b == null ? null : b.getBatchNumber()));
		}
	};
}
