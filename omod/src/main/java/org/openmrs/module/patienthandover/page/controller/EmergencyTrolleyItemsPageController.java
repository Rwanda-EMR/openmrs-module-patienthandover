package org.openmrs.module.patienthandover.page.controller;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.text.SimpleDateFormat;
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
	        @RequestParam(value = "viewAll", defaultValue = "false") boolean viewAll,
	        @RequestParam(value = "trolleyId", required = false) Integer trolleyId, PageModel model,
	        UiSessionContext context, @SpringBean PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE);
		Location location = context.getSessionLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("viewAll", viewAll);
		if (location != null) {
			List<EmergencyTrolleyUnit> trolleys = service.getActiveTrolleyUnits(location);
			model.addAttribute("trolleys", trolleys);
			EmergencyTrolleyUnit selectedTrolley = selectTrolley(trolleys, trolleyId);
			model.addAttribute("selectedTrolley", selectedTrolley);
			List<EmergencyTrolleyCatalogItem> allItems = selectedTrolley == null ? new java.util.ArrayList<EmergencyTrolleyCatalogItem>()
			        : service.getActiveTrolleyCatalog(selectedTrolley);
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
			if ("addTrolley".equals(action)) {
				EmergencyTrolleyUnit trolley = new EmergencyTrolleyUnit();
				base(trolley);
				trolley.setLocation(location);
				trolley.setTrolleyName(req(request, "trolleyName"));
				trolley.setTrolleyCode(req(request, "trolleyCode"));
				trolley.setDescription(request.getParameter("description"));
				trolley.setActive(true);
				service.saveTrolleyUnit(trolley);
				success(session, "Emergency trolley type added.");
			} else if ("addItem".equals(action)) {
				EmergencyTrolleyUnit selectedTrolley = requireTrolley(request, location, service);
				String name = req(request, "itemName");
				if (hasItem(service.getActiveTrolleyCatalog(selectedTrolley), name))
					throw new IllegalArgumentException("That item already exists at this location.");
				EmergencyTrolleyCatalogItem item = new EmergencyTrolleyCatalogItem();
				base(item);
				item.setLocation(location);
				item.setTrolley(selectedTrolley);
				item.setItemName(name);
				item.setCurrentQuantity(qty(request.getParameter("startingQuantity"), name));
				item.setMinimumQuantity(qty(request.getParameter("minimumQuantity"), name + " minimum"));
				item.setMaximumQuantity(qty(request.getParameter("maximumQuantity"), name + " maximum"));
				validateLimits(item.getCurrentQuantity(), item.getMinimumQuantity(), item.getMaximumQuantity());
				item.setExpirationDate(date(request.getParameter("expirationDate")));
				item.setStandardNote(request.getParameter("standardNote"));
				item.setActive(true);
				service.saveTrolleyCatalogItem(item);
				success(session, "Trolley item added.");
			} else if ("updateStockLimits".equals(action)) {
				EmergencyTrolleyCatalogItem item = service.getTrolleyCatalogItem(Integer.valueOf(req(request, "itemId")));
				if (item == null || !item.getLocation().equals(location) || item.isVoided())
					throw new IllegalArgumentException("Item not found.");
				item.setMinimumQuantity(qty(request.getParameter("minimumQuantity"), item.getItemName() + " minimum"));
				item.setMaximumQuantity(qty(request.getParameter("maximumQuantity"), item.getItemName() + " maximum"));
				validateLimits(item.getCurrentQuantity(), item.getMinimumQuantity(), item.getMaximumQuantity());
				item.setExpirationDate(date(request.getParameter("expirationDate")));
				item.setChangedBy(Context.getAuthenticatedUser());
				item.setDateChanged(new Date());
				service.saveTrolleyCatalogItem(item);
				success(session, "Stock limits and expiration date updated for " + item.getItemName() + ".");
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
		EmergencyTrolleyUnit selectedTrolley = requireTrolley(request, location, service);
		List<EmergencyTrolleyItem> rows = TrolleySpreadsheetParser.parse(request, "catalog");
		int added = 0, skipped = 0;
		for (EmergencyTrolleyItem row : rows) {
			if (hasItem(service.getActiveTrolleyCatalog(selectedTrolley), row.getItemName())) {
				skipped++;
				continue;
			}
			EmergencyTrolleyCatalogItem item = new EmergencyTrolleyCatalogItem();
			base(item);
			item.setLocation(location);
			item.setTrolley(selectedTrolley);
			item.setItemName(row.getItemName());
			item.setCurrentQuantity(row.getRemainingQuantity());
			item.setMinimumQuantity(row.getMinimumQuantity() == null ? 0.0 : row.getMinimumQuantity());
			item.setMaximumQuantity(row.getMaximumQuantity());
			validateLimits(item.getCurrentQuantity(), item.getMinimumQuantity(), item.getMaximumQuantity());
			item.setExpirationDate(row.getExpiryDate());
			item.setStandardNote(row.getRemarks() == null ? row.getUnit() : row.getRemarks());
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
	
	private boolean hasItem(List<EmergencyTrolleyCatalogItem> items, String name) {
		for (EmergencyTrolleyCatalogItem item : items)
			if (item.getItemName().equalsIgnoreCase(name))
				return true;
		return false;
	}
	
	private EmergencyTrolleyUnit requireTrolley(HttpServletRequest request, Location location, PatienthandoverService service) {
		EmergencyTrolleyUnit trolley = service.getTrolleyUnit(Integer.valueOf(req(request, "trolleyId")));
		if (trolley == null || !location.equals(trolley.getLocation()) || !trolley.isActive() || trolley.isVoided())
			throw new IllegalArgumentException("Select a valid emergency trolley.");
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
	
	private Date date(String value) {
		if (value == null || value.trim().isEmpty())
			return null;
		try {
			return new SimpleDateFormat("yyyy-MM-dd").parse(value.trim());
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Enter expiration date as YYYY-MM-DD.");
		}
	}
	
	private void validateLimits(Double current, Double minimum, Double maximum) {
		if (maximum == null)
			throw new IllegalArgumentException("Maximum quantity is required.");
		if (minimum != null && maximum < minimum)
			throw new IllegalArgumentException("Maximum quantity cannot be below minimum quantity.");
		if (current != null && current > maximum)
			throw new IllegalArgumentException("Starting/current quantity cannot exceed maximum quantity.");
	}
	
	private void success(Session s, String m) {
		s.setAttribute("trolleyItemsMessage", m);
	}
}
