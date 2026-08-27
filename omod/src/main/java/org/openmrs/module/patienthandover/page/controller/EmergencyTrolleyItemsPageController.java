package org.openmrs.module.patienthandover.page.controller;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.*;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

public class EmergencyTrolleyItemsPageController {
	
	public void get(@RequestParam(value = "page", defaultValue = "1") int page,
	        @RequestParam(value = "viewAll", defaultValue = "false") boolean viewAll, PageModel model,
	        UiSessionContext context, @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
		Location location = context.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("viewAll", viewAll);
		if (location != null) {
			List<EmergencyTrolleyCatalogItem> allItems = service.getActiveTrolleyCatalog(location);
			model.addAttribute("items", viewAll ? allItems : PaginationSupport.list(allItems, page, model, "item"));
			if (viewAll) {
				model.addAttribute("itemCurrentPage", 1);
				model.addAttribute("itemTotalPages", 1);
				model.addAttribute("itemTotalItems", allItems.size());
			}
		}
	}
	
	public String post(HttpServletRequest request, UiSessionContext context, Session session,
	        @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
		Location location = context.getSessionLocation();
		try {
			if (location == null)
				throw new IllegalArgumentException("Select a login location.");
			String action = request.getParameter("action");
			if ("addItem".equals(action)) {
				String name = req(request, "itemName");
				if (service.findTrolleyCatalogItem(location, name) != null)
					throw new IllegalArgumentException("That item already exists at this location.");
				EmergencyTrolleyCatalogItem item = new EmergencyTrolleyCatalogItem();
				base(item);
				item.setLocation(location);
				item.setItemName(name);
				item.setCurrentQuantity(qty(request.getParameter("startingQuantity"), name));
				item.setStandardNote(request.getParameter("standardNote"));
				item.setActive(true);
				service.saveTrolleyCatalogItem(item);
				success(session, "Trolley item added.");
			} else if ("retireItem".equals(action)) {
				EmergencyTrolleyCatalogItem item = service.getTrolleyCatalogItem(Integer.valueOf(req(request, "itemId")));
				if (item == null || !item.getLocation().equals(location))
					throw new IllegalArgumentException("Item not found.");
				item.setActive(false);
				service.saveTrolleyCatalogItem(item);
				success(session, "Trolley item removed from future handovers.");
			} else if ("importItems".equals(action)) {
				importItems(request, location, service, session);
			}
		}
		catch (Exception e) {
			session.setAttribute("trolleyItemsError", e.getMessage());
		}
		return "redirect:patienthandover/emergencyTrolleyItems.page";
	}
	
	private void importItems(HttpServletRequest request, Location location, PatienthandoverService service, Session session) {
		List<EmergencyTrolleyItem> rows = TrolleySpreadsheetParser.parse(request, "catalog");
		int added = 0, skipped = 0;
		for (EmergencyTrolleyItem row : rows) {
			if (service.findTrolleyCatalogItem(location, row.getItemName()) != null) {
				skipped++;
				continue;
			}
			EmergencyTrolleyCatalogItem item = new EmergencyTrolleyCatalogItem();
			base(item);
			item.setLocation(location);
			item.setItemName(row.getItemName());
			item.setCurrentQuantity(row.getRemainingQuantity());
			item.setStandardNote(row.getUnit());
			item.setActive(true);
			service.saveTrolleyCatalogItem(item);
			added++;
		}
		EmergencyTrolleyImport audit = new EmergencyTrolleyImport();
		base(audit);
		audit.setLocation(location);
		audit.setFileName("uploaded spreadsheet");
		audit.setTotalRows(rows.size());
		audit.setItemsAdded(added);
		audit.setItemsSkipped(skipped);
		audit.setItemsFailed(0);
		audit.setStatus("COMPLETED");
		service.saveTrolleyImport(audit);
		success(session, added + " trolley item(s) imported; " + skipped + " duplicate(s) skipped.");
	}
	
	private void base(org.openmrs.BaseOpenmrsData d) {
		d.setUuid(UUID.randomUUID().toString());
		d.setCreator(Context.getAuthenticatedUser());
		d.setDateCreated(new Date());
	}
	
	private String req(HttpServletRequest r, String n) {
		String v = r.getParameter(n);
		if (v == null || v.trim().isEmpty())
			throw new IllegalArgumentException("Complete all required fields.");
		return v.trim();
	}
	
	private Double qty(String v, String n) {
		try {
			double q = Double.parseDouble(v);
			if (q < 0)
				throw new Exception();
			return q;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Enter a valid non-negative quantity for " + n + ".");
		}
	}
	
	private void success(Session s, String m) {
		s.setAttribute("trolleyItemsMessage", m);
	}
}
