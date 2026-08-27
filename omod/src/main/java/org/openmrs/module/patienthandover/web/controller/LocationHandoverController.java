package org.openmrs.module.patienthandover.web.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
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
import org.openmrs.module.patienthandover.domain.PatientHandoverTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("module/patienthandover/locationHandover.form")
public class LocationHandoverController {
	
	private static final String VIEW = "/module/patienthandover/locationHandover";
	
	@Autowired
	private PatientService patientService;
	
	@Autowired
	private PatienthandoverService patienthandoverService;
	
	@Autowired
	private ProviderService providerService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String showWorklist(@RequestParam(value = "careSetting", defaultValue = "IPD") String careSetting,
	        @RequestParam(value = "query", required = false) String query,
	        @RequestParam(value = "manualPatientId", required = false) Integer manualPatientId,
	        @RequestParam(value = "removePatientId", required = false) Integer removePatientId,
	        @RequestParam(value = "clearManual", defaultValue = "false") boolean clearManual,
	        @RequestParam(value = "showSaved", defaultValue = "false") boolean showSaved, Model model, HttpSession session) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		Location location = Context.getUserContext().getLocation();
		model.addAttribute("currentLocation", location);
		model.addAttribute("careSetting", careSetting);
		model.addAttribute("providers", providerService.getAllProviders(false));
		if (location != null && isValidCareSetting(careSetting)) {
			model.addAttribute("draftStorageKey", getDraftStorageKey(location, careSetting));
		}
		
		if (location == null) {
			model.addAttribute("worklistError", "Please select a login location before opening the handover worklist.");
			return VIEW;
		}
		
		if (!isValidCareSetting(careSetting)) {
			model.addAttribute("worklistError", "Please select OPD or IPD.");
			return VIEW;
		}
		
		String sessionKey = getManualSessionKey(location, careSetting);
		String excludedSessionKey = getExcludedSessionKey(location, careSetting);
		if (clearManual) {
			session.removeAttribute(sessionKey);
		}
		
		if (removePatientId != null) {
			getExcludedPatientIds(session, excludedSessionKey).add(removePatientId);
			getManualPatientIds(session, sessionKey).remove(removePatientId);
			session.setAttribute("locationHandoverMessage", "Patient removed from this worklist.");
		}
		
		if (manualPatientId != null) {
			Patient manualPatient = patientService.getPatient(manualPatientId);
			if (manualPatient != null && !Boolean.TRUE.equals(manualPatient.getVoided())) {
				getExcludedPatientIds(session, excludedSessionKey).remove(manualPatientId);
				getManualPatientIds(session, sessionKey).add(manualPatientId);
				session.setAttribute("locationHandoverMessage", manualPatient.getPersonName().getFullName()
				        + " added manually to this worklist.");
			}
		}
		if (query != null && !query.trim().isEmpty()) {
			query = query.trim();
			List<Patient> searchResults = patientService.getPatients(query);
			if (searchResults.size() > 20) {
				searchResults = new ArrayList<Patient>(searchResults.subList(0, 20));
			}
			model.addAttribute("searchQuery", query);
			model.addAttribute("searchResults", searchResults);
		}
		
		model.addAttribute("patients", getPatientsForWorklist(location, careSetting, session));
		model.addAttribute("showSaved", showSaved);
		if (showSaved) {
			List<PatientHandover> recentHandovers = patienthandoverService.getRecentHandoversByLocation(location,
			    careSetting, 500);
			model.addAttribute("recentHandoverBatches", groupHandoversByBatch(recentHandovers));
		}
		return VIEW;
	}
	
	@RequestMapping(method = RequestMethod.POST)
	public String saveBatch(@RequestParam(value = "patientIds", required = false) List<Integer> patientIds,
	        @RequestParam("careSetting") String careSetting, @RequestParam("shift") String shift,
	        @RequestParam("receivingProviderId") Integer receivingProviderId, @RequestParam Map<String, String> parameters,
	        HttpServletRequest request, HttpSession session) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		Location location = Context.getUserContext().getLocation();
		
		if (location == null) {
			session.setAttribute("locationHandoverError", "Please select a login location before saving handovers.");
			return "redirect:locationHandover.form";
		}
		
		if (!isValidCareSetting(careSetting) || (!"DAY".equals(shift) && !"NIGHT".equals(shift))) {
			session.setAttribute("locationHandoverError", "Please select a valid care setting and shift.");
			return "redirect:locationHandover.form?careSetting=" + careSetting;
		}
		
		Provider receivingProvider = providerService.getProvider(receivingProviderId);
		if (receivingProvider == null || Boolean.TRUE.equals(receivingProvider.getRetired())) {
			session.setAttribute("locationHandoverError", "Please select a valid receiving provider.");
			return "redirect:locationHandover.form?careSetting=" + careSetting;
		}
		
		if (patientIds == null || patientIds.isEmpty()) {
			session.setAttribute("locationHandoverError", "Select at least one patient.");
			return "redirect:locationHandover.form?careSetting=" + careSetting;
		}
		
		Map<Integer, Patient> allowedPatients = new LinkedHashMap<Integer, Patient>();
		for (Patient patient : getPatientsForWorklist(location, careSetting, session)) {
			allowedPatients.put(patient.getPatientId(), patient);
		}
		
		String batchUuid = UUID.randomUUID().toString();
		List<PatientHandover> handovers = new ArrayList<PatientHandover>();
		for (Integer patientId : patientIds) {
			Patient patient = allowedPatients.get(patientId);
			if (patient == null) {
				continue;
			}
			
			String priority = parameters.get("priority_" + patientId);
			if (!isValidPriority(priority)) {
				session.setAttribute("locationHandoverError", "Each selected patient must have a valid priority.");
				return "redirect:locationHandover.form?careSetting=" + careSetting;
			}
			
			if (isBlank(parameters.get("situation_" + patientId)) || isBlank(parameters.get("background_" + patientId))
			        || isBlank(parameters.get("assessment_" + patientId))
			        || isBlank(parameters.get("recommendation_" + patientId))) {
				session.setAttribute("locationHandoverError",
				    "Complete all SBAR fields for every selected patient before submitting the handover.");
				return "redirect:locationHandover.form?careSetting=" + careSetting;
			}
			String[] taskDescriptions = request.getParameterValues("taskDescription_" + patientId);
			String[] taskDueDates = request.getParameterValues("taskDueDate_" + patientId);
			List<PatientHandoverTask> structuredTasks = new ArrayList<PatientHandoverTask>();
			if (taskDescriptions != null) {
				for (int taskIndex = 0; taskIndex < taskDescriptions.length; taskIndex++) {
					String description = taskDescriptions[taskIndex] == null ? "" : taskDescriptions[taskIndex].trim();
					if (description.isEmpty())
						continue;
					if (taskDueDates == null || taskIndex >= taskDueDates.length || isBlank(taskDueDates[taskIndex])) {
						session.setAttribute("locationHandoverError", "Every entered task must have its own due time.");
						return "redirect:locationHandover.form?careSetting=" + careSetting;
					}
					Date dueDate;
					try {
						dueDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(taskDueDates[taskIndex]);
					}
					catch (ParseException e) {
						session.setAttribute("locationHandoverError", "A task due date is invalid.");
						return "redirect:locationHandover.form?careSetting=" + careSetting;
					}
					PatientHandoverTask task = new PatientHandoverTask();
					task.setUuid(UUID.randomUUID().toString());
					task.setDescription(description);
					task.setDueDate(dueDate);
					task.setAssignee(receivingProvider);
					task.setCreator(Context.getAuthenticatedUser());
					task.setDateCreated(new Date());
					structuredTasks.add(task);
				}
			}
			
			PatientHandover handover = new PatientHandover();
			handover.setPatient(patient);
			handover.setBatchUuid(batchUuid);
			handover.setLocation(location);
			handover.setCareSetting(careSetting);
			handover.setShift(shift);
			handover.setReceivingProvider(receivingProvider);
			handover.setPriority(priority);
			handover.setSituation(parameters.get("situation_" + patientId));
			handover.setBackground(parameters.get("background_" + patientId));
			handover.setAssessment(parameters.get("assessment_" + patientId));
			handover.setRecommendation(parameters.get("recommendation_" + patientId));
			for (PatientHandoverTask task : structuredTasks) {
				handover.addTask(task);
			}
			handovers.add(handover);
		}
		
		int saved = handovers.size();
		if (saved > 0) {
			patienthandoverService.savePatientHandovers(handovers);
			PatientHandoverAuditSupport.record(patienthandoverService, "SUBMITTED", batchUuid, handovers.get(0).getUuid(),
			    null, true, saved + " patient handover(s) submitted atomically.");
		}
		
		if (saved == 0) {
			session.setAttribute("locationHandoverError",
			    "No handovers were saved because the selected patients are no longer active at this location.");
			return "redirect:locationHandover.form?careSetting=" + careSetting;
		}
		session.setAttribute("completedDraftStorageKey", getDraftStorageKey(location, careSetting));
		session.setAttribute("dashboardMessage", saved + " patient handover(s) submitted successfully as transaction "
		        + handovers.get(0).getTransactionReference() + ".");
		return "redirect:handoverDashboard.form";
	}
	
	private List<Patient> getPatientsAtLocation(Location location, String careSetting) {
		List<Integer> locationIds = new ArrayList<Integer>();
		locationIds.add(location.getLocationId());
		for (Location descendant : location.getDescendantLocations(false)) {
			locationIds.add(descendant.getLocationId());
		}
		
		Date minimumEncounterDate = "OPD".equals(careSetting) ? getStartOfToday() : null;
		List<Integer> patientIds = patienthandoverService.getActivePatientIdsByEncounterLocations(locationIds,
		    minimumEncounterDate);
		
		List<Patient> patients = new ArrayList<Patient>();
		for (Integer patientId : patientIds) {
			Patient patient = patientService.getPatient(patientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
				patients.add(patient);
			}
		}
		return patients;
	}
	
	private List<Patient> getPatientsForWorklist(Location location, String careSetting, HttpSession session) {
		Map<Integer, Patient> patients = new LinkedHashMap<Integer, Patient>();
		for (Patient patient : getPatientsAtLocation(location, careSetting)) {
			patients.put(patient.getPatientId(), patient);
		}
		
		String sessionKey = getManualSessionKey(location, careSetting);
		String excludedSessionKey = getExcludedSessionKey(location, careSetting);
		for (Integer patientId : getManualPatientIds(session, sessionKey)) {
			Patient patient = patientService.getPatient(patientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
				patients.put(patientId, patient);
			}
		}
		for (Integer excludedPatientId : getExcludedPatientIds(session, getExcludedSessionKey(location, careSetting))) {
			patients.remove(excludedPatientId);
		}
		return new ArrayList<Patient>(patients.values());
	}
	
	@SuppressWarnings("unchecked")
	private Set<Integer> getManualPatientIds(HttpSession session, String sessionKey) {
		Set<Integer> patientIds = (Set<Integer>) session.getAttribute(sessionKey);
		if (patientIds == null) {
			patientIds = new LinkedHashSet<Integer>();
			session.setAttribute(sessionKey, patientIds);
		}
		return patientIds;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Integer> getExcludedPatientIds(HttpSession session, String sessionKey) {
		Set<Integer> patientIds = (Set<Integer>) session.getAttribute(sessionKey);
		if (patientIds == null) {
			patientIds = new LinkedHashSet<Integer>();
			session.setAttribute(sessionKey, patientIds);
		}
		return patientIds;
	}
	
	private String getExcludedSessionKey(Location location, String careSetting) {
		return "patienthandoverExcludedPatients_" + location.getLocationId() + "_" + careSetting;
	}
	
	private Map<String, List<PatientHandover>> groupHandoversByBatch(List<PatientHandover> handovers) {
		Map<String, List<PatientHandover>> batches = new LinkedHashMap<String, List<PatientHandover>>();
		for (PatientHandover handover : handovers) {
			String key = handover.getBatchUuid() == null || handover.getBatchUuid().trim().isEmpty() ? "single:"
			        + handover.getUuid() : handover.getBatchUuid();
			List<PatientHandover> batch = batches.get(key);
			if (batch == null) {
				batch = new ArrayList<PatientHandover>();
				batches.put(key, batch);
			}
			batch.add(handover);
		}
		return batches;
	}
	
	private String getDraftStorageKey(Location location, String careSetting) {
		Integer userId = Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId();
		return "patienthandoverDraft_" + (userId == null ? "anonymous" : userId) + "_" + location.getLocationId() + "_"
		        + careSetting;
	}
	
	private String getManualSessionKey(Location location, String careSetting) {
		return "patienthandoverManualPatients_" + location.getLocationId() + "_" + careSetting;
	}
	
	private Date getStartOfToday() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}
	
	private boolean isValidCareSetting(String careSetting) {
		return "OPD".equals(careSetting) || "IPD".equals(careSetting);
	}
	
	private boolean isValidPriority(String priority) {
		return "STABLE".equals(priority) || "URGENT".equals(priority) || "CRITICAL".equals(priority);
	}
	
	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
