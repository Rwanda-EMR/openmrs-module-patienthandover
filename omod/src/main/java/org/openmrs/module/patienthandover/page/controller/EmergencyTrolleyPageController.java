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
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.*;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;

public class EmergencyTrolleyPageController {
	
	public void get(PageModel model, UiSessionContext context, @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		Location location = context.getSessionLocation();
		model.addAttribute("currentLocation", location);
		if (location == null)
			return;
		model.addAttribute("items", service.getActiveTrolleyCatalog(location));
		model.addAttribute("handovers", service.getTrolleyHandovers(location, 30));
		model.addAttribute("providers", Context.getProviderService().getAllProviders(false));
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
				message(session, "Emergency trolley handover submitted for receipt.");
			} else if ("editPendingItem".equals(action)) {
				editPendingItem(request, location, service);
				message(session, "Trolley item correction saved and audited.");
			} else if ("reassignHandover".equals(action)) {
				reassign(request, location, service);
				message(session, "Emergency trolley handover reassigned.");
			} else if ("cancelHandover".equals(action)) {
				cancel(request, location, service);
				message(session, "Emergency trolley handover cancelled. It remains in the trolley report.");
			} else if ("receiveHandover".equals(action)) {
				Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
				receive(request, location, service);
				message(session, "Emergency trolley handover received. These quantities are now current.");
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
		for (EmergencyTrolleyHandover h : s.getTrolleyHandovers(l, 10))
			if ("PENDING".equals(h.getStatus()))
				throw new IllegalArgumentException("Receive the existing pending trolley handover before creating another.");
		String shift = req(r, "shift");
		if (!"DAY".equals(shift) && !"NIGHT".equals(shift))
			throw new IllegalArgumentException("Select DAY or NIGHT shift.");
		Provider provider = Context.getProviderService().getProvider(Integer.valueOf(req(r, "receivingProviderId")));
		if (provider == null || provider.isRetired())
			throw new IllegalArgumentException("Select a valid receiving provider.");
		List<EmergencyTrolleyCatalogItem> catalog = s.getActiveTrolleyCatalog(l);
		if (catalog.isEmpty())
			throw new IllegalArgumentException("Add trolley items before making a handover.");
		EmergencyTrolleyHandover h = new EmergencyTrolleyHandover();
		base(h);
		h.setLocation(l);
		h.setShift(shift);
		h.setReceivingProvider(provider);
		h.setStatus("PENDING");
		h.setDateSubmitted(new Date());
		for (EmergencyTrolleyCatalogItem item : catalog) {
			EmergencyTrolleyHandoverItem x = new EmergencyTrolleyHandoverItem();
			base(x);
			x.setCatalogItem(item);
			x.setItemNameSnapshot(item.getItemName());
			x.setPreviousQuantity(item.getCurrentQuantity());
			x.setHandedQuantity(qty(r.getParameter("quantity_" + item.getId()), item.getItemName()));
			x.setNote(r.getParameter("note_" + item.getId()));
			h.addItem(x);
		}
		s.saveTrolleyHandover(h);
	}
	
	private void editPendingItem(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyHandover h = s.getTrolleyHandoverByUuid(req(r, "handoverUuid"));
		User user = Context.getAuthenticatedUser();
		boolean creatorCanEdit = h != null && Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EDIT)
		        && h.getCreator() != null && user != null && h.getCreator().getUserId().equals(user.getUserId());
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
	
	private void reassign(HttpServletRequest r, Location l, PatienthandoverService s) {
		EmergencyTrolleyHandover h = s.getTrolleyHandoverByUuid(req(r, "handoverUuid"));
		User user = Context.getAuthenticatedUser();
		boolean creatorCanEdit = h != null && Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EDIT)
		        && h.getCreator() != null && user != null && h.getCreator().getUserId().equals(user.getUserId());
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
		boolean creatorCanCancel = Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CANCEL) && h != null
		        && h.getCreator() != null && user != null && h.getCreator().getUserId().equals(user.getUserId());
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
		User u = Context.getAuthenticatedUser();
		if (h.getReceivingProvider() == null || u == null || u.getPerson() == null
		        || !h.getReceivingProvider().getPerson().getPersonId().equals(u.getPerson().getPersonId()))
			throw new IllegalArgumentException("Only the designated receiving provider can receive this trolley handover.");
		h.setStatus("RECEIVED");
		h.setDateReceived(new Date());
		h.setReceivedBy(u);
		s.receiveTrolleyHandover(h);
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
		return "redirect:patienthandover/emergencyTrolley.page";
	}
}
