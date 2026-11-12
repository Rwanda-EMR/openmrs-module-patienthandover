package org.openmrs.module.patienthandover.page.controller;

import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;

/** Dedicated equipment catalogue page; handover transactions remain on Equipment Handover. */
public class EquipmentItemsPageController {
	
	public void get(PageModel model, UiSessionContext context, Session session, @SpringBean PatienthandoverService service) {
		new EquipmentHandoverPageController().get(1, model, context, session, service);
	}
}
