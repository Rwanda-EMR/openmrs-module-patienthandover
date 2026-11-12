package org.openmrs.module.patienthandover.fragment.controller;

import java.util.Date;
import java.util.UUID;

import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandoverDraft;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AJAX actions behind the Make Handover autosave:
 * {@code /<context>/patienthandover/handoverDraft/save.action} and {@code .../discard.action}. A
 * draft always belongs to the authenticated user, so a caller can only read or change their own
 * drafts.
 */
public class HandoverDraftFragmentController {
	
	/**
	 * Generous upper bound for a whole worklist of SBAR text (protects the database from runaway
	 * requests).
	 */
	static final int MAX_DRAFT_LENGTH = 2000000;
	
	public SimpleObject save(@RequestParam("locationId") Integer locationId,
	        @RequestParam("careSetting") String careSetting, @RequestParam("draft") String draftJson,
	        @SpringBean("patienthandover.PatienthandoverService") PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		User user = Context.getAuthenticatedUser();
		Location location = Context.getLocationService().getLocation(locationId);
		if (user == null || location == null || !isCareSetting(careSetting))
			return SimpleObject.create("saved", false, "error", "Unknown location or care setting.");
		if (draftJson == null || !draftJson.trim().startsWith("{") || draftJson.length() > MAX_DRAFT_LENGTH)
			return SimpleObject.create("saved", false, "error", "Draft is empty or too large.");
		Date now = new Date();
		PatientHandoverDraft draft = service.getPatientHandoverDraft(user, location, careSetting);
		if (draft == null) {
			draft = new PatientHandoverDraft();
			draft.setUuid(UUID.randomUUID().toString());
			draft.setUser(user);
			draft.setLocation(location);
			draft.setCareSetting(careSetting);
			draft.setDateCreated(now);
		}
		draft.setDraftData(draftJson);
		draft.setDateChanged(now);
		service.savePatientHandoverDraft(draft);
		return SimpleObject.create("saved", true, "savedAt", now.getTime());
	}
	
	public SimpleObject discard(@RequestParam("locationId") Integer locationId,
	        @RequestParam("careSetting") String careSetting,
	        @SpringBean("patienthandover.PatienthandoverService") PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		Location location = Context.getLocationService().getLocation(locationId);
		if (location != null && isCareSetting(careSetting))
			service.deletePatientHandoverDraft(Context.getAuthenticatedUser(), location, careSetting);
		return SimpleObject.create("discarded", true);
	}
	
	private static boolean isCareSetting(String careSetting) {
		return "IPD".equals(careSetting) || "OPD".equals(careSetting);
	}
}
