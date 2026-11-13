package org.openmrs.module.patienthandover.page.controller;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.page.controller.PatientHandoverAuditSupport;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.openmrs.module.patienthandover.domain.PatientHandoverTask;
import org.openmrs.module.patienthandover.domain.PatientHandoverDraft;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

public class LocationHandoverPageController {
	
	/**
	 * Admission (visit start) date range of the worklist, from the "Admitted from / to" date
	 * fields. Both dates are inclusive; missing or invalid dates default to the last two days up to
	 * today, and reversed dates are swapped.
	 */
	static final class AdmissionRange {
		
		static final int DEFAULT_DAYS_BACK = 2;
		
		final Date from;
		
		final Date toExclusive;
		
		final String fromText;
		
		final String toText;
		
		private AdmissionRange(Date from, Date to) {
			this.from = from;
			Calendar end = Calendar.getInstance();
			end.setTime(to);
			end.add(Calendar.DAY_OF_MONTH, 1);
			this.toExclusive = end.getTime();
			this.fromText = format(from);
			this.toText = format(to);
		}
		
		static AdmissionRange parse(String fromText, String toText) {
			Date today = startOfDay(new Date());
			Date to = parse(toText);
			if (to == null)
				to = today;
			Date from = parse(fromText);
			if (from == null) {
				Calendar c = Calendar.getInstance();
				c.setTime(to);
				c.add(Calendar.DAY_OF_MONTH, -DEFAULT_DAYS_BACK);
				from = c.getTime();
			}
			return from.after(to) ? new AdmissionRange(to, from) : new AdmissionRange(from, to);
		}
		
		private static Date parse(String text) {
			if (text == null || text.trim().isEmpty())
				return null;
			try {
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
				format.setLenient(false);
				return startOfDay(format.parse(text.trim()));
			}
			catch (ParseException e) {
				return null;
			}
		}
		
		private static Date startOfDay(Date date) {
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			return c.getTime();
		}
		
		private static String format(Date date) {
			return new SimpleDateFormat("yyyy-MM-dd").format(date);
		}
	}
	
	/** SBAR columns are varchar(1000). */
	static final int MAX_SBAR_LENGTH = 1000;
	
	/** A JSON array of strings, e.g. ["Pulse","Systolic"]. */
	static String toJsonArray(List<String> values) {
		StringBuilder out = new StringBuilder("[");
		for (String value : values) {
			if (out.length() > 1)
				out.append(',');
			out.append('"');
			for (char c : value.toCharArray()) {
				if (c == '"' || c == '\\')
					out.append('\\').append(c);
				else if (c < 0x20)
					out.append(' ');
				else
					out.append(c);
			}
			out.append('"');
		}
		return out.append(']').toString();
	}
	
	/**
	 * Renders a value as a JavaScript string literal that is safe inside an inline script element
	 * (no "</script>", HTML-significant characters or line separators survive unescaped). Null
	 * becomes the literal null.
	 */
	static String jsStringLiteral(String value) {
		if (value == null)
			return "null";
		StringBuilder out = new StringBuilder(value.length() + 16).append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '"' || c == '\\')
				out.append('\\').append(c);
			else if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == '\'' || c == 0x2028 || c == 0x2029)
				out.append(String.format("\\u%04x", (int) c));
			else
				out.append(c);
		}
		return out.append('"').toString();
	}
	
	/**
	 * Handles GET requests: displays the worklist.
	 */
	public void get(@RequestParam(value = "careSetting", defaultValue = "IPD") String careSetting,
	        @RequestParam(value = "admittedFrom", required = false) String admittedFrom,
	        @RequestParam(value = "admittedTo", required = false) String admittedTo,
	        @RequestParam(value = "query", required = false) String query,
	        @RequestParam(value = "worklistQuery", required = false) String worklistQuery,
	        @RequestParam(value = "manualPatientId", required = false) Integer manualPatientId,
	        @RequestParam(value = "stagePatientId", required = false) Integer stagePatientId,
	        @RequestParam(value = "removeStagedPatientId", required = false) Integer removeStagedPatientId,
	        @RequestParam(value = "commitStaged", defaultValue = "false") boolean commitStaged,
	        @RequestParam(value = "clearStaged", defaultValue = "false") boolean clearStaged,
	        @RequestParam(value = "removePatientId", required = false) Integer removePatientId,
	        @RequestParam(value = "clearManual", defaultValue = "false") boolean clearManual,
	        @RequestParam(value = "showSaved", defaultValue = "false") boolean showSaved,
	        @RequestParam(value = "showManual", defaultValue = "false") boolean showManual,
	        @RequestParam(value = "viewAll", defaultValue = "false") boolean viewAll,
	        @RequestParam(value = "selectAll", defaultValue = "false") boolean selectAll,
	        @RequestParam(value = "page", defaultValue = "1") int page, PageModel model, UiSessionContext sessionContext,
	        Session session, HttpServletRequest request) {
		
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		
		// Get services from Context
		PatientService patientService = Context.getPatientService();
		ProviderService providerService = Context.getProviderService();
		PatienthandoverService patienthandoverService = Context.getService(PatienthandoverService.class);
		
		Location location = sessionContext.getSessionLocation();
		AdmissionRange admission = AdmissionRange.parse(admittedFrom, admittedTo);
		model.addAttribute("currentLocation", location);
		model.addAttribute("careSetting", careSetting);
		model.addAttribute("admittedFrom", admission.fromText);
		model.addAttribute("admittedTo", admission.toText);
		model.addAttribute("providers", providerService.getAllProviders(false));
		model.addAttribute("handoverLocations", destinationLocations(location));
		
		// Move any flash-style session messages into the model
		String locationHandoverMessage = session.getAttribute("locationHandoverMessage", String.class);
		if (locationHandoverMessage != null) {
			model.addAttribute("locationHandoverMessage", locationHandoverMessage);
			session.setAttribute("locationHandoverMessage", null);
		}
		String locationHandoverError = session.getAttribute("locationHandoverError", String.class);
		if (locationHandoverError != null) {
			model.addAttribute("locationHandoverError", locationHandoverError);
			session.setAttribute("locationHandoverError", null);
		}
		
		if (location != null && isValidCareSetting(careSetting)) {
			model.addAttribute("draftStorageKey", getDraftStorageKey(location, careSetting));
			model.addAttribute("draftMaxAgeHours", PatienthandoverConfig.draftMaxAgeHours());
			// Drafts live only in the browser tab now; remove any copy an earlier version kept on the server.
			if (patienthandoverService.getPatientHandoverDraft(Context.getAuthenticatedUser(), location, careSetting) != null)
				patienthandoverService.deletePatientHandoverDraft(Context.getAuthenticatedUser(), location, careSetting);
			Map<String, String[]> rejectedForm = session.getAttribute(getRejectedFormSessionKey(location, careSetting),
			    Map.class);
			if (rejectedForm != null)
				model.addAttribute("rejectedForm", rejectedForm);
		}
		
		if (location == null) {
			model.addAttribute("worklistError", "Please select a login location before opening the handover worklist.");
			return;
		}
		
		if (!isValidCareSetting(careSetting)) {
			model.addAttribute("worklistError", "Please select OPD or IPD.");
			return;
		}
		
		String sessionKey = getManualSessionKey(location, careSetting);
		String stagedSessionKey = getStagedSessionKey(location, careSetting);
		String excludedSessionKey = getExcludedSessionKey(location, careSetting);
		Set<Integer> stagedPatientIds = getManualPatientIds(session, stagedSessionKey);
		if (clearStaged)
			stagedPatientIds.clear();
		if (removeStagedPatientId != null)
			stagedPatientIds.remove(removeStagedPatientId);
		if (stagePatientId != null) {
			Patient patient = patientService.getPatient(stagePatientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
				stagedPatientIds.add(stagePatientId);
				model.addAttribute(
				    "locationHandoverMessage",
				    patient.getPersonName().getFullName()
				            + " added to the temporary patient list. Search for another patient or add the list to the worklist.");
			}
		}
		if (commitStaged && !stagedPatientIds.isEmpty()) {
			int added = 0;
			for (Integer patientId : new LinkedHashSet<Integer>(stagedPatientIds)) {
				getExcludedPatientIds(session, excludedSessionKey).remove(patientId);
				if (getManualPatientIds(session, sessionKey).add(patientId))
					added++;
			}
			stagedPatientIds.clear();
			model.addAttribute("locationHandoverMessage", added + " patient(s) added to the worklist.");
		}
		if (clearManual) {
			session.setAttribute(sessionKey, null);
		}
		
		if (removePatientId != null) {
			getExcludedPatientIds(session, excludedSessionKey).add(removePatientId);
			getManualPatientIds(session, sessionKey).remove(removePatientId);
			session.setAttribute("locationHandoverMessage", "Patient removed from this worklist.");
		}
		String[] removePatientIds = request.getParameterValues("removePatientIds");
		int removedPatients = 0;
		if (removePatientIds != null)
			for (String value : removePatientIds)
				try {
					Integer patientId = Integer.valueOf(value);
					getExcludedPatientIds(session, excludedSessionKey).add(patientId);
					getManualPatientIds(session, sessionKey).remove(patientId);
					removedPatients++;
				}
				catch (NumberFormatException ignored) {}
		if (removedPatients > 0)
			session.setAttribute("locationHandoverMessage", removedPatients + " patient(s) removed from this worklist.");
		
		if (manualPatientId != null) {
			Patient manualPatient = patientService.getPatient(manualPatientId);
			if (manualPatient != null && !Boolean.TRUE.equals(manualPatient.getVoided())) {
				getExcludedPatientIds(session, excludedSessionKey).remove(manualPatientId);
				getManualPatientIds(session, sessionKey).add(manualPatientId);
				session.setAttribute("locationHandoverMessage", manualPatient.getPersonName().getFullName()
				        + " added manually to this worklist.");
			}
		}
		
		String[] manualPatientIds = request.getParameterValues("manualPatientIds");
		int manuallyAdded = 0;
		if (manualPatientIds != null)
			for (String value : manualPatientIds)
				try {
					Integer patientId = Integer.valueOf(value);
					Patient patient = patientService.getPatient(patientId);
					if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
						getExcludedPatientIds(session, excludedSessionKey).remove(patientId);
						if (getManualPatientIds(session, sessionKey).add(patientId))
							manuallyAdded++;
					}
				}
				catch (NumberFormatException ignored) {}
		if (manuallyAdded > 0)
			session.setAttribute("locationHandoverMessage", manuallyAdded + " patient(s) added manually to this worklist.");
		
		if (query != null && !query.trim().isEmpty()) {
			query = query.trim();
			List<Patient> searchResults = new ArrayList<Patient>();
			for (Integer patientId : patienthandoverService.searchActivePatientIds(query, 50)) {
				Patient patient = patientService.getPatient(patientId);
				if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
					searchResults.add(patient);
				}
			}
			model.addAttribute("searchQuery", query);
			model.addAttribute("searchResults", searchResults);
		}
		
		List<Patient> manuallyAddedPatients = new ArrayList<Patient>();
		for (Integer patientId : getManualPatientIds(session, sessionKey)) {
			Patient patient = patientService.getPatient(patientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided()))
				manuallyAddedPatients.add(patient);
		}
		model.addAttribute("manuallyAddedPatients", manuallyAddedPatients);
		List<Patient> stagedPatients = new ArrayList<Patient>();
		for (Integer patientId : stagedPatientIds) {
			Patient patient = patientService.getPatient(patientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided()))
				stagedPatients.add(patient);
		}
		model.addAttribute("stagedPatients", stagedPatients);
		
		List<Patient> worklistPatients = getPatientsForWorklist(location, careSetting, admission, session, patientService,
		    patienthandoverService);
		model.addAttribute("showManualPatients", false);
		if (worklistQuery != null && !worklistQuery.trim().isEmpty()) {
			worklistQuery = worklistQuery.trim();
			worklistPatients = filterPatients(worklistPatients, worklistQuery);
		}
		model.addAttribute("worklistQuery", worklistQuery);
		model.addAttribute("viewAllPatients", viewAll);
		model.addAttribute("selectAllPatientsOnLoad", viewAll && selectAll);
		List<Patient> shownPatients;
		if (viewAll) {
			shownPatients = new ArrayList<Patient>(worklistPatients);
			model.addAttribute("patientCurrentPage", 1);
			model.addAttribute("patientTotalPages", 1);
			model.addAttribute("patientTotalItems", worklistPatients.size());
		} else {
			shownPatients = PaginationSupport.list(worklistPatients, page, model, "patient");
		}
		model.addAttribute("patients", shownPatients);
		// Suggestions only for the patients rendered on this page, so large worklists stay fast.
		model.addAttribute("sbarSuggestions", patienthandoverService.getSbarSuggestions(shownPatients));
		model.addAttribute("canExplainSbar", PatienthandoverConfig.canExplainSbar());
		List<String> removableTitles = new ArrayList<String>();
		List<String> keptTitles = new ArrayList<String>();
		for (Map.Entry<String, Boolean> title : patienthandoverService.getSbarTitles().entrySet())
			(Boolean.TRUE.equals(title.getValue()) ? removableTitles : keptTitles).add(title.getKey());
		model.addAttribute("sbarTitlesLiteral", jsStringLiteral(toJsonArray(removableTitles)));
		model.addAttribute("sbarKeptTitlesLiteral", jsStringLiteral(toJsonArray(keptTitles)));
		model.addAttribute("showSaved", showSaved);
		if (showSaved) {
			List<PatientHandover> recentHandovers = patienthandoverService.getRecentHandoversByLocation(location,
			    careSetting, 500);
			model.addAttribute("recentHandoverBatches", groupHandoversByBatch(recentHandovers));
		}
	}
	
	/**
	 * Handles POST requests: saves the batch.
	 */
	public String post(HttpServletRequest request, UiSessionContext sessionContext, Session session) {
		
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_CREATE);
		
		// Get services from Context
		PatientService patientService = Context.getPatientService();
		ProviderService providerService = Context.getProviderService();
		PatienthandoverService patienthandoverService = Context.getService(PatienthandoverService.class);
		
		Location location = sessionContext.getSessionLocation();
		
		String careSetting = request.getParameter("careSetting");
		careSetting = careSetting == null ? null : careSetting.trim().toUpperCase();
		AdmissionRange admission = AdmissionRange.parse(request.getParameter("admittedFrom"),
		    request.getParameter("admittedTo"));
		String shift = request.getParameter("shift");
		shift = shift == null ? null : shift.trim().toUpperCase();
		String receivingProviderIdParam = request.getParameter("receivingProviderId");
		Location destinationLocation = location;
		String destinationLocationId = request.getParameter("destinationLocationId");
		if (destinationLocationId != null && !destinationLocationId.trim().isEmpty()) {
			try {
				destinationLocation = Context.getLocationService().getLocation(Integer.valueOf(destinationLocationId));
			}
			catch (Exception ignored) {
				destinationLocation = null;
			}
		}
		
		if (location != null) {
			String snapshotCareSetting = isValidCareSetting(careSetting) ? careSetting : "IPD";
			session.setAttribute(getRejectedFormSessionKey(location, snapshotCareSetting), copyParameters(request));
		}
		
		if (location == null) {
			session.setAttribute("locationHandoverError", "Please select a login location before saving handovers.");
			return validationRedirect(request, null);
		}
		if (destinationLocation == null || destinationLocation.isRetired()) {
			session.setAttribute("locationHandoverError", "Please select a valid destination location.");
			return validationRedirect(request, careSetting);
		}
		
		if (!isValidCareSetting(careSetting)) {
			session.setAttribute("locationHandoverError", "Please select a valid care setting (IPD or OPD).");
			return validationRedirect(request, "IPD");
		}
		if (!"DAY".equals(shift) && !"NIGHT".equals(shift)) {
			session.setAttribute("locationHandoverError",
			    "Please select a shift (DAY or NIGHT). Your entered handover details were preserved.");
			return validationRedirect(request, careSetting);
		}
		
		List<Provider> receivingTeam = selectedProviders(request.getParameterValues("receivingProviderIds"), providerService);
		if (receivingTeam.isEmpty() && receivingProviderIdParam != null)
			receivingTeam = selectedProviders(new String[] { receivingProviderIdParam }, providerService);
		List<Provider> outgoingTeam = selectedProviders(request.getParameterValues("outgoingProviderIds"), providerService);
		if (receivingTeam.isEmpty()) {
			session.setAttribute("locationHandoverError", "Please select at least one receiving team member.");
			return validationRedirect(request, careSetting);
		}
		Provider receivingProvider = receivingTeam.get(0);
		
		String[] patientIdParams = request.getParameterValues("patientIds");
		if (patientIdParams == null || patientIdParams.length == 0) {
			session.setAttribute("locationHandoverError", "Select at least one patient.");
			return validationRedirect(request, careSetting);
		}
		
		Map<Integer, Patient> allowedPatients = new LinkedHashMap<Integer, Patient>();
		for (Patient patient : getPatientsForWorklist(location, careSetting, admission, session, patientService,
		    patienthandoverService)) {
			allowedPatients.put(patient.getPatientId(), patient);
		}
		
		String batchUuid = UUID.randomUUID().toString();
		List<PatientHandover> handovers = new ArrayList<PatientHandover>();
		for (String patientIdParam : patientIdParams) {
			Integer patientId;
			try {
				patientId = Integer.valueOf(patientIdParam);
			}
			catch (NumberFormatException e) {
				continue;
			}
			Patient patient = allowedPatients.get(patientId);
			if (patient == null) {
				continue;
			}
			
			String priority = request.getParameter("priority_" + patientId);
			if (!isValidPriority(priority)) {
				session.setAttribute("locationHandoverError", "Each selected patient must have a valid status.");
				return validationRedirect(request, careSetting);
			}
			
			String[] taskDescriptions = request.getParameterValues("taskDescription_" + patientId);
			String[] taskDueDates = request.getParameterValues("taskDueDate_" + patientId);
			List<PatientHandoverTask> structuredTasks = new ArrayList<PatientHandoverTask>();
			if (taskDescriptions != null) {
				for (int taskIndex = 0; taskIndex < taskDescriptions.length; taskIndex++) {
					String description = taskDescriptions[taskIndex] == null ? "" : taskDescriptions[taskIndex].trim();
					if (description.isEmpty()) {
						continue;
					}
					if (taskDueDates == null || taskIndex >= taskDueDates.length || isBlank(taskDueDates[taskIndex])) {
						session.setAttribute("locationHandoverError", "Every entered task must have its own due time.");
						return validationRedirect(request, careSetting);
					}
					Date dueDate;
					try {
						dueDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(taskDueDates[taskIndex]);
					}
					catch (ParseException e) {
						session.setAttribute("locationHandoverError", "A task due date is invalid.");
						return validationRedirect(request, careSetting);
					}
					for (Provider receiver : receivingTeam) {
						PatientHandoverTask task = new PatientHandoverTask();
						task.setUuid(UUID.randomUUID().toString());
						task.setDescription(description);
						task.setDueDate(dueDate);
						task.setAssignee(receiver);
						task.setCreator(Context.getAuthenticatedUser());
						task.setDateCreated(new Date());
						structuredTasks.add(task);
					}
				}
			}
			
			PatientHandover handover = new PatientHandover();
			handover.setPatient(patient);
			handover.setBatchUuid(batchUuid);
			handover.setLocation(location);
			handover.setDestinationLocation(destinationLocation);
			handover.setCareSetting(careSetting);
			handover.setShift(shift);
			handover.setReceivingProvider(receivingProvider);
			handover.setPriority(priority);
			for (String field : new String[] { "situation", "background", "assessment", "recommendation" }) {
				String text = request.getParameter(field + "_" + patientId);
				if (text != null && text.length() > MAX_SBAR_LENGTH) {
					session.setAttribute("locationHandoverError", "The " + field + " of "
					        + patient.getPersonName().getFullName() + " is longer than " + MAX_SBAR_LENGTH
					        + " characters. Shorten it and submit again.");
					return validationRedirect(request, careSetting);
				}
			}
			handover.setSituation(request.getParameter("situation_" + patientId));
			handover.setBackground(request.getParameter("background_" + patientId));
			handover.setAssessment(request.getParameter("assessment_" + patientId));
			handover.setRecommendation(request.getParameter("recommendation_" + patientId));
			for (PatientHandoverTask task : structuredTasks) {
				handover.addTask(task);
			}
			handovers.add(handover);
		}
		int saved = handovers.size();
		if (saved > 0) {
			patienthandoverService.savePatientHandovers(handovers);
			saveTeamMember(patienthandoverService, batchUuid, Context.getAuthenticatedUser().getPerson(), "OUTGOING", true);
			for (Provider provider : outgoingTeam)
				if (!provider.getPerson().equals(Context.getAuthenticatedUser().getPerson()))
					saveTeamMember(patienthandoverService, batchUuid, provider.getPerson(), "OUTGOING", false);
			for (Provider provider : receivingTeam)
				saveTeamMember(patienthandoverService, batchUuid, provider.getPerson(), "RECEIVING", false);
			PatientHandoverAuditSupport.record(patienthandoverService, "SUBMITTED", batchUuid, handovers.get(0).getUuid(),
			    null, true, saved + " patient handover(s) submitted atomically.");
		}
		
		if (saved == 0) {
			session.setAttribute("locationHandoverError",
			    "No handovers were saved because the selected patients are no longer active at this location.");
			return validationRedirect(request, careSetting);
		}
		
		session.setAttribute(getRejectedFormSessionKey(location, careSetting), null);
		session.setAttribute("completedDraftStorageKey", getDraftStorageKey(location, careSetting));
		patienthandoverService.deletePatientHandoverDraft(Context.getAuthenticatedUser(), location, careSetting);
		session.setAttribute("dashboardMessage", saved + " patient handover(s) submitted successfully.");
		return "redirect:patienthandover/handoverDashboard.page";
	}
	
	private String validationRedirect(HttpServletRequest request, String careSetting) {
		StringBuilder redirect = new StringBuilder("redirect:patienthandover/locationHandover.page");
		boolean first = true;
		if (!isBlank(careSetting)) {
			redirect.append("?careSetting=").append(urlEncode(careSetting));
			first = false;
		}
		String viewAll = request.getParameter("viewAll");
		if ("true".equalsIgnoreCase(viewAll)) {
			redirect.append(first ? '?' : '&').append("viewAll=true");
			first = false;
		}
		String showManual = request.getParameter("showManual");
		if ("true".equalsIgnoreCase(showManual)) {
			redirect.append(first ? '?' : '&').append("showManual=true");
			first = false;
		}
		String page = request.getParameter("page");
		if (page != null && page.matches("[1-9][0-9]*")) {
			redirect.append(first ? '?' : '&').append("page=").append(page);
			first = false;
		}
		String worklistQuery = request.getParameter("worklistQuery");
		if (!isBlank(worklistQuery))
			redirect.append(first ? '?' : '&').append("worklistQuery=").append(urlEncode(worklistQuery));
		for (String name : new String[] { "admittedFrom", "admittedTo" }) {
			String value = request.getParameter(name);
			if (!isBlank(value)) {
				redirect.append(first ? '?' : '&').append(name).append('=').append(urlEncode(value));
				first = false;
			}
		}
		return redirect.toString();
	}
	
	private String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		}
		catch (UnsupportedEncodingException impossible) {
			return value;
		}
	}
	
	private List<Patient> getPatientsAtLocation(Location location, String careSetting, AdmissionRange admission,
	        PatientService patientService, PatienthandoverService patienthandoverService) {
		List<Integer> locationIds = new ArrayList<Integer>();
		locationIds.add(location.getLocationId());
		for (Location descendant : location.getDescendantLocations(false)) {
			locationIds.add(descendant.getLocationId());
		}
		
		List<Integer> patientIds = patienthandoverService.getActivePatientIdsByEncounterLocations(locationIds,
		    admission.from, admission.toExclusive);
		
		List<Patient> patients = new ArrayList<Patient>();
		for (Integer patientId : patientIds) {
			Patient patient = patientService.getPatient(patientId);
			if (patient != null && !Boolean.TRUE.equals(patient.getVoided())) {
				patients.add(patient);
			}
		}
		return patients;
	}
	
	private List<Patient> getPatientsForWorklist(Location location, String careSetting, AdmissionRange admission,
	        Session session, PatientService patientService, PatienthandoverService patienthandoverService) {
		Map<Integer, Patient> patients = new LinkedHashMap<Integer, Patient>();
		for (Patient patient : getPatientsAtLocation(location, careSetting, admission, patientService,
		    patienthandoverService)) {
			patients.put(patient.getPatientId(), patient);
		}
		
		String sessionKey = getManualSessionKey(location, careSetting);
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
	
	private List<Patient> filterPatients(List<Patient> patients, String query) {
		String normalized = query.toLowerCase();
		List<Patient> matches = new ArrayList<Patient>();
		for (Patient patient : patients) {
			String name = patient.getPersonName() == null ? "" : patient.getPersonName().getFullName();
			boolean matched = name != null && name.toLowerCase().contains(normalized);
			if (!matched) {
				for (org.openmrs.PatientIdentifier identifier : patient.getIdentifiers()) {
					if (identifier.getIdentifier() != null && identifier.getIdentifier().toLowerCase().contains(normalized)) {
						matched = true;
						break;
					}
				}
			}
			if (matched) {
				matches.add(patient);
			}
		}
		return matches;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Integer> getManualPatientIds(Session session, String sessionKey) {
		Set<Integer> patientIds = session.getAttribute(sessionKey, Set.class);
		if (patientIds == null) {
			patientIds = new LinkedHashSet<Integer>();
			session.setAttribute(sessionKey, patientIds);
		}
		return patientIds;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Integer> getExcludedPatientIds(Session session, String sessionKey) {
		Set<Integer> patientIds = session.getAttribute(sessionKey, Set.class);
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
	
	private Map<String, String[]> copyParameters(HttpServletRequest request) {
		Map<String, String[]> copy = new HashMap<String, String[]>();
		for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet())
			copy.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().clone());
		return copy;
	}
	
	private String getRejectedFormSessionKey(Location location, String careSetting) {
		Integer userId = Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId();
		return "patienthandoverRejectedForm_" + (userId == null ? "anonymous" : userId) + "_" + location.getLocationId()
		        + "_" + careSetting;
	}
	
	private String getDraftStorageKey(Location location, String careSetting) {
		Integer userId = Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId();
		return "patienthandoverDraft_" + (userId == null ? "anonymous" : userId) + "_" + location.getLocationId() + "_"
		        + careSetting;
	}
	
	private String getManualSessionKey(Location location, String careSetting) {
		return "patienthandoverManualPatients_" + location.getLocationId() + "_" + careSetting;
	}
	
	private String getStagedSessionKey(Location location, String careSetting) {
		Integer userId = Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId();
		return "patienthandoverStagedPatients_" + (userId == null ? "anonymous" : userId) + "_" + location.getLocationId()
		        + "_" + careSetting;
	}
	
	private Date getStartOfToday() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}
	
	private List<Provider> selectedProviders(String[] ids, ProviderService service) {
		List<Provider> result = new ArrayList<Provider>();
		Set<Integer> people = new LinkedHashSet<Integer>();
		if (ids != null)
			for (String value : ids)
				try {
					Provider provider = service.getProvider(Integer.valueOf(value));
					if (provider != null && !provider.isRetired() && provider.getPerson() != null
					        && people.add(provider.getPerson().getPersonId()))
						result.add(provider);
				}
				catch (NumberFormatException ignored) {}
		return result;
	}
	
	private void saveTeamMember(PatienthandoverService service, String batchUuid, org.openmrs.Person person, String role,
	        boolean signed) {
		if (person == null)
			return;
		org.openmrs.module.patienthandover.domain.HandoverTeamMember member = new org.openmrs.module.patienthandover.domain.HandoverTeamMember();
		Date now = new Date();
		member.setUuid(UUID.randomUUID().toString());
		member.setHandoverType("PATIENT");
		member.setTransactionKey(batchUuid);
		member.setPerson(person);
		member.setRole(role);
		member.setSigned(signed);
		member.setSignedBy(signed ? Context.getAuthenticatedUser() : null);
		member.setDateSigned(signed ? now : null);
		member.setCreator(Context.getAuthenticatedUser());
		member.setDateCreated(now);
		service.saveHandoverTeamMember(member);
	}
	
	private boolean isValidCareSetting(String careSetting) {
		return "OPD".equals(careSetting) || "IPD".equals(careSetting);
	}
	
	/** Limit transfers to departments directly belonging to the current hospital. */
	private List<Location> destinationLocations(Location loginLocation) {
		List<Location> destinations = new ArrayList<Location>();
		if (loginLocation == null)
			return destinations;
		Location mainHospital = loginLocation;
		while (mainHospital.getParentLocation() != null)
			mainHospital = mainHospital.getParentLocation();
		for (Location child : mainHospital.getChildLocations(false))
			if (!child.isRetired())
				destinations.add(child);
		if (destinations.isEmpty())
			destinations.add(loginLocation);
		return destinations;
	}
	
	private boolean isValidPriority(String priority) {
		return "STABLE".equals(priority) || "URGENT".equals(priority) || "CRITICAL".equals(priority)
		        || "DIED".equals(priority) || "CURED".equals(priority) || "DEFAULTED".equals(priority)
		        || "TRANSFERRED".equals(priority);
	}
	
	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
