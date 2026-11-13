package org.openmrs.module.patienthandover.fragment.controller;

import java.util.Collections;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AJAX action behind the "Why?" button on Make Patient Handover:
 * {@code /<context>/patienthandover/sbarCheck/explain.action?patientId=..}. Returns plain-text
 * lines explaining which records were used for the patient's SBAR text and why others were not.
 * Restricted to the System Developer role, because it shows settings and concept UUIDs.
 */
public class SbarCheckFragmentController {
	
	public SimpleObject explain(@RequestParam("patientId") Integer patientId,
	        @SpringBean("patienthandover.PatienthandoverService") PatienthandoverService service) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		if (!PatienthandoverConfig.canExplainSbar())
			throw new org.openmrs.api.APIAuthenticationException(
			        "Only the System Developer role can view the SBAR explanation.");
		Patient patient = Context.getPatientService().getPatient(patientId);
		if (patient == null)
			return SimpleObject.create("lines", Collections.singletonList("Patient not found."));
		return SimpleObject.create("lines", service.explainSbarSuggestions(patient));
	}
}
