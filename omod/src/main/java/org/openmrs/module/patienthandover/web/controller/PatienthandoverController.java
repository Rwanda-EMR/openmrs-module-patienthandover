package org.openmrs.module.patienthandover.web.controller;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("module/patienthandover/legacyPatienthandover.form")
public class PatienthandoverController {
	
	private static final String VIEW = "/module/patienthandover/patienthandover";
	
	@Autowired
	private PatientService patientService;
	
	@Autowired
	private PatienthandoverService patienthandoverService;
	
	@Autowired
	private ProviderService providerService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String showForm(@RequestParam(value = "patientId", required = false) Integer patientId,
	        @RequestParam(value = "query", required = false) String query, Model model) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		model.addAttribute("providers", providerService.getAllProviders(false));
		model.addAttribute("currentUserPersonId", Context.getAuthenticatedUser().getPerson().getPersonId());
		
		/*
		 * Case 1:
		 * A patient was selected from search results.
		 */
		if (patientId != null) {
			
			Patient patient = patientService.getPatient(patientId);
			
			if (patient != null) {
				
				model.addAttribute("patient", patient);
				
				model.addAttribute("handovers", patienthandoverService.getPatientHandoversByPatient(patient));
			}
			
			return VIEW;
		}
		
		/*
		 * Case 2:
		 * User entered a name or identifier.
		 */
		if (query != null && !query.trim().isEmpty()) {
			
			query = query.trim();
			
			List<Patient> patients = patientService.getPatients(query);
			
			model.addAttribute("searchQuery", query);
			model.addAttribute("patients", patients);
		}
		
		return VIEW;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "!acknowledge")
	public String saveHandover(@RequestParam("patientId") Integer patientId, @RequestParam("priority") String priority,
	        @RequestParam("shift") String shift, @RequestParam("careSetting") String careSetting,
	        @RequestParam("receivingProviderId") Integer receivingProviderId, @RequestParam("situation") String situation,
	        @RequestParam("background") String background, @RequestParam("assessment") String assessment,
	        @RequestParam("recommendation") String recommendation, HttpSession session) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		
		Patient patient = patientService.getPatient(patientId);
		
		if (patient == null) {
			
			session.setAttribute("patienthandoverError", "Patient not found.");
			
			return "redirect:patienthandover.form";
		}
		
		if (!"DAY".equals(shift) && !"NIGHT".equals(shift)) {
			session.setAttribute("patienthandoverError", "Please select a valid shift.");
			return "redirect:patienthandover.form?patientId=" + patientId;
		}
		
		if (!"OPD".equals(careSetting) && !"IPD".equals(careSetting)) {
			session.setAttribute("patienthandoverError", "Please select OPD or IPD.");
			return "redirect:patienthandover.form?patientId=" + patientId;
		}
		
		Provider receivingProvider = providerService.getProvider(receivingProviderId);
		if (receivingProvider == null || Boolean.TRUE.equals(receivingProvider.getRetired())) {
			session.setAttribute("patienthandoverError", "Please select a valid receiving provider.");
			return "redirect:patienthandover.form?patientId=" + patientId;
		}
		
		Location location = Context.getUserContext().getLocation();
		if (location == null) {
			session.setAttribute("patienthandoverError", "Please select a login location before creating a handover.");
			return "redirect:patienthandover.form?patientId=" + patientId;
		}
		
		PatientHandover handover = new PatientHandover();
		
		handover.setPatient(patient);
		handover.setPriority(priority);
		handover.setShift(shift);
		handover.setCareSetting(careSetting);
		handover.setLocation(location);
		handover.setReceivingProvider(receivingProvider);
		handover.setSituation(situation);
		handover.setBackground(background);
		handover.setAssessment(assessment);
		handover.setRecommendation(recommendation);
		
		patienthandoverService.savePatientHandover(handover);
		
		session.setAttribute("patienthandoverMessage", "Patient handover saved successfully.");
		
		return "redirect:patienthandover.form?patientId=" + patientId;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "acknowledge")
	public String acknowledgeHandover(@RequestParam("handoverUuid") String handoverUuid,
	        @RequestParam("patientId") Integer patientId, HttpSession session) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
		PatientHandover handover = patienthandoverService.getPatientHandoverByUuid(handoverUuid);
		if (handover == null || handover.getPatient() == null || !patientId.equals(handover.getPatient().getPatientId())) {
			session.setAttribute("patienthandoverError", "Handover not found.");
			return "redirect:patienthandover.form?patientId=" + patientId;
		}
		Provider receivingProvider = handover.getReceivingProvider();
		if (receivingProvider == null
		        || receivingProvider.getPerson() == null
		        || Context.getAuthenticatedUser().getPerson() == null
		        || !receivingProvider.getPerson().getPersonId()
		                .equals(Context.getAuthenticatedUser().getPerson().getPersonId())) {
			session.setAttribute("patienthandoverError",
			    "Only the designated receiving provider may mark this handover as received.");
			return "redirect:patienthandover.form?patientId=" + patientId;
		}
		
		List<PatientHandover> batch = new java.util.ArrayList<PatientHandover>();
		if (handover.getBatchUuid() == null || handover.getBatchUuid().trim().isEmpty()) {
			batch.add(handover);
		} else {
			batch = patienthandoverService.getHandoversByBatchUuid(handover.getBatchUuid());
		}
		
		for (PatientHandover batchHandover : batch) {
			if (!batchHandover.isAcknowledged()) {
				batchHandover.setAcknowledged(true);
				batchHandover.setAcknowledgedBy(Context.getAuthenticatedUser());
				batchHandover.setDateAcknowledged(new Date());
			}
		}
		patienthandoverService.savePatientHandovers(batch);
		session.setAttribute("patienthandoverMessage", batch.size() + " handover(s) marked as received as one transaction.");
		return "redirect:patienthandover.form?patientId=" + patientId;
	}
	
}
