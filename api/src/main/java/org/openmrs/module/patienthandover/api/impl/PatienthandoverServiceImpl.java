package org.openmrs.module.patienthandover.api.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNumeric;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Person;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.api.dao.PatienthandoverDao;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.PharmacyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyCatalogItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandoverItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyImport;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyUnit;
import org.openmrs.module.patienthandover.domain.PatientHandoverAuditEvent;
import org.openmrs.module.patienthandover.domain.HandoverTeamMember;
import org.openmrs.module.patienthandover.domain.EquipmentItem;
import org.openmrs.module.patienthandover.domain.EquipmentHandover;
import org.openmrs.module.patienthandover.domain.LaboratoryHandover;
import org.openmrs.module.patienthandover.domain.PendingLaboratoryPatient;
import org.openmrs.module.patienthandover.domain.PatientHandoverDraft;
import org.openmrs.module.patienthandover.sbar.SbarSuggestionBuilder;
import org.springframework.transaction.annotation.Transactional;

public class PatienthandoverServiceImpl extends BaseOpenmrsService implements PatienthandoverService {
	
	static final String STATUS_PENDING = "PENDING";
	
	static final String STATUS_RECEIVED = "RECEIVED";
	
	static final String ROLE_OUTGOING = "OUTGOING";
	
	static final String ROLE_RECEIVING = "RECEIVING";
	
	private PatienthandoverDao dao;
	
	/**
	 * Injected in moduleApplicationContext.xml
	 */
	public void setDao(PatienthandoverDao dao) {
		this.dao = dao;
	}
	
	@Override
	@Transactional
	public HandoverTeamMember saveHandoverTeamMember(HandoverTeamMember member) {
		return dao.saveHandoverTeamMember(member);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<HandoverTeamMember> getHandoverTeam(String type, String key) {
		return dao.getHandoverTeam(type, key);
	}
	
	@Override
	@Transactional(readOnly = true)
	public Map<String, List<HandoverTeamMember>> getHandoverTeams(String type, Collection<String> keys) {
		Map<String, List<HandoverTeamMember>> teams = new LinkedHashMap<String, List<HandoverTeamMember>>();
		if (keys == null || keys.isEmpty())
			return teams;
		for (String key : keys)
			teams.put(key, new ArrayList<HandoverTeamMember>());
		for (HandoverTeamMember member : dao.getHandoverTeams(type, new ArrayList<String>(teams.keySet()))) {
			List<HandoverTeamMember> team = teams.get(member.getTransactionKey());
			if (team != null)
				team.add(member);
		}
		return teams;
	}
	
	@Override
	@Transactional
	public boolean signHandoverTeamMember(HandoverTeamMember member) {
		if (member == null || member.getId() == null)
			throw new APIException("Only a saved handover team member can sign.");
		// Locking read: returns the latest committed state of every member and blocks concurrent signers of this team.
		List<Object[]> rows = dao.lockHandoverTeam(member.getHandoverType(), member.getTransactionKey());
		boolean found = false, outgoingSeen = false, receivingSeen = false, outgoingDone = true, receivingDone = true;
		for (Object[] row : rows) {
			Integer id = ((Number) row[0]).intValue();
			String role = (String) row[1];
			boolean signed = truthy(row[2]);
			if (id.equals(member.getId())) {
				if (signed)
					throw new APIException("This handover signature has already been recorded.");
				found = true;
				signed = true;
			}
			if (ROLE_OUTGOING.equals(role)) {
				outgoingSeen = true;
				outgoingDone &= signed;
			} else if (ROLE_RECEIVING.equals(role)) {
				receivingSeen = true;
				receivingDone &= signed;
			}
		}
		if (!found)
			throw new APIException("This handover team assignment is no longer active.");
		User user = Context.getAuthenticatedUser();
		Date now = new Date();
		member.setSigned(true);
		member.setSignedBy(user);
		member.setDateSigned(now);
		member.setChangedBy(user);
		member.setDateChanged(now);
		dao.saveHandoverTeamMember(member);
		return outgoingSeen && receivingSeen && outgoingDone && receivingDone;
	}
	
	private static boolean truthy(Object value) {
		if (value instanceof Boolean)
			return (Boolean) value;
		return value instanceof Number && ((Number) value).intValue() != 0;
	}
	
	private void saveTeam(List<HandoverTeamMember> team) {
		if (team != null)
			for (HandoverTeamMember member : team)
				dao.saveHandoverTeamMember(member);
	}
	
	@Override
	@Transactional
	public PatientHandoverAuditEvent saveAuditEvent(PatientHandoverAuditEvent event) throws APIException {
		return dao.saveAuditEvent(event);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PatientHandoverAuditEvent> getAuditEventsByLocationAndDateRange(Location location, Date fromDate, Date toDate)
	        throws APIException {
		return dao.getAuditEventsByLocationAndDateRange(location, fromDate, toDate);
	}
	
	@Override
	@Transactional(readOnly = true)
	public PatientHandover getPatientHandoverByUuid(String uuid) throws APIException {
		return dao.getPatientHandoverByUuid(uuid);
	}
	
	@Override
	@Transactional
	public PatientHandover savePatientHandover(PatientHandover handover) throws APIException {
		return dao.savePatientHandover(handover);
	}
	
	@Override
	@Transactional
	public List<PatientHandover> savePatientHandovers(List<PatientHandover> handovers) throws APIException {
		return dao.savePatientHandovers(handovers);
	}
	
	@Override
	@Transactional
	public List<PatientHandover> savePatientHandovers(List<PatientHandover> handovers,
	        List<EmergencyTrolleyItem> trolleyItems) throws APIException {
		for (PatientHandover handover : handovers)
			dao.savePatientHandover(handover);
		for (EmergencyTrolleyItem item : trolleyItems)
			dao.saveTrolleyItem(item);
		return handovers;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<EmergencyTrolleyItem> getTrolleyItemsByBatchUuid(String batchUuid) throws APIException {
		return dao.getTrolleyItemsByBatchUuid(batchUuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PatientHandover> getHandoversByBatchUuid(String batchUuid) throws APIException {
		return dao.getHandoversByBatchUuid(batchUuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PatientHandover> getPatientHandoversByPatient(Patient patient) throws APIException {
		return dao.getPatientHandoversByPatient(patient);
	}
	
	@Override
	@Transactional(readOnly = true)
	public PatientHandoverDraft getPatientHandoverDraft(User user, Location location, String careSetting) {
		if (user == null || location == null || careSetting == null)
			return null;
		return dao.getPatientHandoverDraft(user, location, careSetting);
	}
	
	@Override
	@Transactional
	public PatientHandoverDraft savePatientHandoverDraft(PatientHandoverDraft draft) {
		return dao.savePatientHandoverDraft(draft);
	}
	
	@Override
	@Transactional
	public void deletePatientHandoverDraft(User user, Location location, String careSetting) {
		PatientHandoverDraft draft = getPatientHandoverDraft(user, location, careSetting);
		if (draft != null)
			dao.deletePatientHandoverDraft(draft);
	}
	
	static final String GP_SBAR_PREFILL = "patienthandover.sbar.prefillEnabled";
	
	/**
	 * 0 (default) = use the latest saved value of each concept whatever its age; N = only values
	 * from the last N days.
	 */
	static final String GP_SBAR_MAX_AGE = "patienthandover.sbar.maxAgeDays";
	
	private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory
	        .getLog(PatienthandoverServiceImpl.class);
	
	/** Previous handovers older than this are not offered as SBAR text. */
	static final int PREVIOUS_HANDOVER_DAYS = 30;
	
	@Override
	@Transactional(readOnly = true)
	public Map<Integer, Map<String, SbarSuggestionBuilder.Suggestion>> getSbarSuggestions(List<Patient> patients) {
		Map<Integer, Map<String, SbarSuggestionBuilder.Suggestion>> none = new HashMap<Integer, Map<String, SbarSuggestionBuilder.Suggestion>>();
		if (patients == null || patients.isEmpty())
			return none;
		String enabled = Context.getAdministrationService().getGlobalProperty(GP_SBAR_PREFILL, "true");
		if (!"true".equalsIgnoreCase(enabled.trim()))
			return none;
		int maxAgeDays = 0;
		try {
			maxAgeDays = Integer.parseInt(Context.getAdministrationService().getGlobalProperty(GP_SBAR_MAX_AGE, "0").trim());
		}
		catch (NumberFormatException ignored) {}
		Date since = maxAgeDays > 0 ? daysAgo(maxAgeDays) : null;
		
		List<Integer> patientIds = new ArrayList<Integer>();
		for (Patient patient : patients)
			if (patient != null && patient.getPatientId() != null)
				patientIds.add(patient.getPatientId());
		// Only values created after the current admission (start of the active visit) are used. Patients
		// without an active visit fall back to the optional maximum age.
		Map<Integer, Date> admissions = admissionTimes(patientIds);
		
		// concept -> {field, position * 1000, alternative}. Positions are comma-separated; alternatives of
		// one position are joined with "|" and compete for a single line. ABNORMAL_LABS marks where
		// abnormal laboratory results are listed.
		Map<Concept, Object[]> mapping = new LinkedHashMap<Concept, Object[]>();
		String labField = null;
		int labOrder = 0;
		String diagnosisField = null;
		int diagnosisOrder = 0;
		for (String field : SbarSuggestionBuilder.FIELDS) {
			String configured = Context.getAdministrationService().getGlobalProperty(
			    "patienthandover.sbar." + field + "Concepts", "");
			int position = 0;
			for (String entry : configured.split(",")) {
				if (entry.trim().isEmpty())
					continue;
				int alternative = 0;
				for (String reference : entry.split("\\|")) {
					reference = reference.trim();
					if (ABNORMAL_LABS.equalsIgnoreCase(reference)) {
						labField = field;
						labOrder = position * 1000;
						continue;
					}
					if (DIAGNOSES.equalsIgnoreCase(reference)) {
						diagnosisField = field;
						diagnosisOrder = position * 1000;
						continue;
					}
					Concept concept = resolveConcept(reference);
					if (concept == null && !reference.isEmpty())
						log.warn("SBAR pre-fill: concept '" + reference + "' configured for " + field
						        + " was not found and is ignored");
					if (concept != null && !mapping.containsKey(concept))
						mapping.put(concept, new Object[] { field, position * 1000, alternative });
					alternative++;
				}
				position++;
			}
		}
		
		Locale locale = Context.getLocale();
		List<SbarSuggestionBuilder.Observation> observations = new ArrayList<SbarSuggestionBuilder.Observation>();
		if (!mapping.isEmpty())
			for (Obs obs : dao.getLatestObs(patientIds, new ArrayList<Concept>(mapping.keySet()), since)) {
				Object[] target = mapping.get(obs.getConcept());
				if (target == null || obs.isObsGrouping()
				        || !createdAfter(obs.getDateCreated(), cutoff(admissions.get(obs.getPersonId()), since)))
					continue;
				String value;
				try {
					value = obs.getValueAsString(locale);
				}
				catch (RuntimeException e) {
					continue;
				}
				observations.add(new SbarSuggestionBuilder.Observation(obs.getPersonId(), (String) target[0],
				        (Integer) target[1], (Integer) target[2], label(obs.getConcept(), locale), value, obs
				                .getDateCreated()));
			}
		if (labField != null)
			observations.addAll(abnormalLabResults(patientIds, admissions, since, labField, labOrder, locale));
		if (diagnosisField != null)
			observations.addAll(diagnosisData(patientIds, admissions, since, mapping.keySet(), diagnosisField,
			    diagnosisOrder, locale));
		
		Map<Integer, Map<String, SbarSuggestionBuilder.PreviousText>> previous = new HashMap<Integer, Map<String, SbarSuggestionBuilder.PreviousText>>();
		for (PatientHandover h : dao.getRecentHandoversByPatients(patientIds, daysAgo(PREVIOUS_HANDOVER_DAYS))) {
			Integer patientId = h.getPatient().getPatientId();
			if (previous.containsKey(patientId))
				continue; // newest first: keep only the latest handover
			if (!createdAfter(h.getDateCreated(), cutoff(admissions.get(patientId), since)))
				continue; // a handover from before this admission is not carried forward
			Map<String, SbarSuggestionBuilder.PreviousText> fields = new HashMap<String, SbarSuggestionBuilder.PreviousText>();
			fields.put(SbarSuggestionBuilder.SITUATION,
			    new SbarSuggestionBuilder.PreviousText(h.getSituation(), h.getDateCreated()));
			fields.put(SbarSuggestionBuilder.BACKGROUND,
			    new SbarSuggestionBuilder.PreviousText(h.getBackground(), h.getDateCreated()));
			fields.put(SbarSuggestionBuilder.ASSESSMENT,
			    new SbarSuggestionBuilder.PreviousText(h.getAssessment(), h.getDateCreated()));
			fields.put(SbarSuggestionBuilder.RECOMMENDATION,
			    new SbarSuggestionBuilder.PreviousText(h.getRecommendation(), h.getDateCreated()));
			previous.put(patientId, fields);
		}
		return new SbarSuggestionBuilder().build(observations, previous, admissions);
	}
	
	static final String GP_ADMISSION_ENCOUNTER_TYPES = "patienthandover.sbar.admissionEncounterTypes";
	
	static final String GP_EPISODE_GAP_HOURS = "patienthandover.sbar.episodeGapHours";
	
	/** How far back encounters are read when looking for the start of the current stretch of care. */
	static final int EPISODE_SEARCH_DAYS = 365;
	
	/**
	 * Admission time of each patient, used as the cut-off for SBAR values. See
	 * {@link #admissionAnchor(Date, Date, List, long)}. Patients with no encounters and no active
	 * visit are absent.
	 */
	private List<String> admissionEncounterTypes() {
		List<String> types = new ArrayList<String>();
		for (String uuid : Context.getAdministrationService().getGlobalProperty(GP_ADMISSION_ENCOUNTER_TYPES, "").split(","))
			if (!uuid.trim().isEmpty())
				types.add(uuid.trim());
		return types;
	}
	
	private long episodeGapMillis() {
		int gapHours = 72;
		try {
			gapHours = Integer.parseInt(Context.getAdministrationService().getGlobalProperty(GP_EPISODE_GAP_HOURS, "72")
			        .trim());
		}
		catch (NumberFormatException ignored) {}
		return Math.max(1, gapHours) * 3600L * 1000L;
	}
	
	private Map<Integer, Date> admissionTimes(List<Integer> patientIds) {
		List<String> types = admissionEncounterTypes();
		long gap = episodeGapMillis();
		Map<Integer, Date> visits = dao.getActiveVisitStarts(patientIds);
		Map<Integer, Date> admissionsByForm = dao.getLatestEncounterDatetimes(patientIds, types);
		Map<Integer, List<Date>> encounters = dao.getEncounterDatetimes(patientIds, daysAgo(EPISODE_SEARCH_DAYS));
		Map<Integer, Date> result = new HashMap<Integer, Date>();
		for (Integer patientId : patientIds) {
			Date anchor = admissionAnchor(visits.get(patientId), admissionsByForm.get(patientId), encounters.get(patientId),
			    gap);
			if (anchor != null)
				result.put(patientId, anchor);
		}
		return result;
	}
	
	/**
	 * The admission time used as the cut-off, robust against visits that are never closed: the
	 * latest of
	 * <ul>
	 * <li>the start of the active visit,</li>
	 * <li>the latest admission encounter (admission form), and</li>
	 * <li>the start of the current stretch of care: walking back from the latest encounter, the
	 * stretch ends at the first gap longer than {@code gapMillis} without encounters (the patient
	 * left and came back).</li>
	 * </ul>
	 * The time elapsed since then (today's date) plays no role: values entered after the last
	 * admission are always used, however long ago it was. Returns null only when nothing is known
	 * about the patient.
	 * 
	 * @param encountersNewestFirst encounter date-times, newest first (may be null)
	 */
	static Date admissionAnchor(Date visitStart, Date admissionEncounter, List<Date> encountersNewestFirst, long gapMillis) {
		Date episodeStart = null;
		if (encountersNewestFirst != null && !encountersNewestFirst.isEmpty()) {
			episodeStart = encountersNewestFirst.get(0);
			for (int i = 1; i < encountersNewestFirst.size(); i++) {
				Date earlier = encountersNewestFirst.get(i);
				if (episodeStart.getTime() - earlier.getTime() > gapMillis)
					break;
				episodeStart = earlier;
			}
		}
		Date anchor = null;
		for (Date candidate : new Date[] { visitStart, admissionEncounter, episodeStart })
			if (candidate != null && (anchor == null || candidate.after(anchor)))
				anchor = candidate;
		return anchor;
	}
	
	@Override
	@Transactional(readOnly = true)
	public Map<String, Boolean> getSbarTitles() {
		Map<String, Boolean> titles = new LinkedHashMap<String, Boolean>();
		Locale locale = Context.getLocale();
		for (String field : SbarSuggestionBuilder.FIELDS) {
			String configured = Context.getAdministrationService().getGlobalProperty(
			    "patienthandover.sbar." + field + "Concepts", "");
			for (String reference : configured.split("[,|]")) {
				reference = reference.trim();
				if (reference.isEmpty() || ABNORMAL_LABS.equalsIgnoreCase(reference)
				        || DIAGNOSES.equalsIgnoreCase(reference))
					continue;
				Concept concept = resolveConcept(reference);
				if (concept != null) {
					String title = label(concept, locale);
					boolean removable = !concept.isNumeric(); // "Pulse: 68.0" must keep "Pulse"
					titles.put(title, titles.containsKey(title) ? titles.get(title) && removable : removable);
				}
			}
		}
		return titles;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<String> explainSbarSuggestions(Patient patient) {
		List<String> out = new ArrayList<String>();
		if (patient == null || patient.getPatientId() == null)
			return out;
		Integer patientId = patient.getPatientId();
		List<Integer> ids = java.util.Collections.singletonList(patientId);
		long gap = episodeGapMillis();
		Date visit = dao.getActiveVisitStarts(ids).get(patientId);
		Date form = dao.getLatestEncounterDatetimes(ids, admissionEncounterTypes()).get(patientId);
		List<Date> encounters = dao.getEncounterDatetimes(ids, daysAgo(EPISODE_SEARCH_DAYS)).get(patientId);
		Date anchor = admissionAnchor(visit, form, encounters, gap);
		int maxAgeDays = 0;
		try {
			maxAgeDays = Integer.parseInt(Context.getAdministrationService().getGlobalProperty(GP_SBAR_MAX_AGE, "0").trim());
		}
		catch (NumberFormatException ignored) {}
		Date cutoff = cutoff(anchor, maxAgeDays > 0 ? daysAgo(maxAgeDays) : null);
		
		out.add("# Admission time");
		out.add("Open visit started: " + describe(visit));
		out.add("Latest admission form: " + describe(form));
		out.add("Latest encounter: "
		        + (encounters == null || encounters.isEmpty() ? "none in the last " + EPISODE_SEARCH_DAYS + " days"
		                : describe(encounters.get(0))));
		out.add(anchor == null ? "Admission time used: none. No open visit, admission form or encounter is known, "
		        + "so nothing is filled in from records." : "Admission time used: " + describe(anchor)
		        + ". Values saved at or after this time (including those saved together with the admission) are used.");
		if (maxAgeDays > 0)
			out.add("Maximum age (patienthandover.sbar.maxAgeDays): " + maxAgeDays + " days");
		
		Locale locale = Context.getLocale();
		for (String field : SbarSuggestionBuilder.FIELDS) {
			String property = "patienthandover.sbar." + field + "Concepts";
			String configured = Context.getAdministrationService().getGlobalProperty(property, "");
			out.add("# " + Character.toUpperCase(field.charAt(0)) + field.substring(1));
			out.add("Setting " + property + " = " + (configured.trim().isEmpty() ? "(empty)" : configured));
			if (org.openmrs.module.patienthandover.sbar.ConceptReferenceConverter.hasNumericIds(configured))
				out.add("Same setting with UUIDs (valid on every server): "
				        + org.openmrs.module.patienthandover.sbar.ConceptReferenceConverter.toUuids(configured,
				            new org.openmrs.module.patienthandover.sbar.ConceptReferenceConverter.Lookup() {
					            
					            @Override
					            public String uuidOf(int conceptId) {
						            Concept concept = Context.getConceptService().getConcept(conceptId);
						            return concept == null ? null : concept.getUuid();
					            }
				            }));
			for (String entry : configured.split(",")) {
				if (entry.trim().isEmpty())
					continue;
				String[] alternatives = entry.split("\\|");
				String prefix = alternatives.length > 1 ? "Either/or (newest wins): " : "";
				for (String reference : alternatives) {
					reference = reference.trim();
					if (ABNORMAL_LABS.equalsIgnoreCase(reference)) {
						String type = Context.getAdministrationService().getGlobalProperty(GP_LAB_ORDER_TYPE, "").trim();
						out.add(prefix
						        + "ABNORMAL_LABS: "
						        + (type.isEmpty() ? "not available, " + GP_LAB_ORDER_TYPE + " is not set"
						                : "results of order type " + type + " outside the reference range"));
						continue;
					}
					if (DIAGNOSES.equalsIgnoreCase(reference)) {
						out.add(prefix + "DIAGNOSES: coded diagnoses and text in diagnosis fields saved after admission");
						continue;
					}
					Concept concept = resolveConcept(reference);
					if (concept == null) {
						out.add(prefix + "'" + reference + "': NOT FOUND in the concept dictionary, ignored");
						continue;
					}
					String name = label(concept, locale) + " [" + concept.getUuid() + "]";
					List<Obs> latest = dao.getLatestObs(ids, java.util.Collections.singletonList(concept), null);
					if (latest.isEmpty()) {
						out.add(prefix + name + " (" + reference + "): nothing recorded for this patient "
						        + "(entries such as '.' or '-' are ignored)");
						continue;
					}
					Obs obs = latest.get(0);
					Date created = obs.getDateCreated();
					String status;
					if (cutoff == null)
						status = "not used: no current admission";
					else if (created != null && Math.abs(created.getTime() - cutoff.getTime()) <= SAME_SAVE_TOLERANCE_MILLIS)
						status = "used (saved together with the admission)";
					else if (createdAfter(created, cutoff))
						status = "used (saved after admission)";
					else
						status = "NOT USED: saved before the admission time";
					String value;
					try {
						value = obs.getValueAsString(locale);
					}
					catch (RuntimeException e) {
						value = "?";
					}
					if (value != null && value.length() > 80)
						value = value.substring(0, 79) + "...";
					out.add(prefix + name + ": latest saved " + describe(created) + ", value '" + value + "' -> " + status);
				}
			}
		}
		return out;
	}
	
	private static String describe(Date date) {
		return date == null ? "none" : new java.text.SimpleDateFormat("dd MMM yyyy HH:mm:ss").format(date);
	}
	
	/** Marker in an SBAR concept list for the position of all other diagnosis data. */
	static final String DIAGNOSES = "DIAGNOSES";
	
	static final int MAX_DIAGNOSIS_LINES = 8;
	
	/**
	 * Diagnosis data recorded after each patient's admission time, one line per diagnosis field
	 * (its latest value; several diagnoses saved together are joined). Fields already configured
	 * explicitly are skipped so they are not shown twice.
	 */
	private List<SbarSuggestionBuilder.Observation> diagnosisData(List<Integer> patientIds, Map<Integer, Date> admissions,
	        Date since, Set<Concept> explicit, String field, int order, Locale locale) {
		List<SbarSuggestionBuilder.Observation> out = new ArrayList<SbarSuggestionBuilder.Observation>();
		Date earliest = null;
		List<Integer> admitted = new ArrayList<Integer>();
		for (Integer patientId : patientIds) {
			Date admission = admissions.get(patientId);
			if (admission == null)
				continue;
			admitted.add(patientId);
			if (earliest == null || admission.before(earliest))
				earliest = admission;
		}
		if (admitted.isEmpty())
			return out;
		// patient -> question concept -> {line position, date of the latest recording}
		Map<String, Object[]> lines = new HashMap<String, Object[]>();
		Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
		for (Obs obs : dao.getDiagnosisObs(admitted, queryBound(earliest))) {
			Integer patientId = obs.getPersonId();
			if (explicit.contains(obs.getConcept()) || obs.isObsGrouping()
			        || !createdAfter(obs.getDateCreated(), cutoff(admissions.get(patientId), since)))
				continue;
			String key = patientId + "|" + obs.getConcept().getConceptId();
			Object[] line = lines.get(key);
			if (line == null) {
				int count = counts.containsKey(patientId) ? counts.get(patientId) : 0;
				if (count >= MAX_DIAGNOSIS_LINES)
					continue;
				counts.put(patientId, count + 1);
				line = new Object[] { order + count + 1, obs.getDateCreated() };
				lines.put(key, line);
			} else if (!line[1].equals(obs.getDateCreated())) {
				continue; // newest first: only the latest recording of this field (answers saved together are kept)
			}
			String value;
			try {
				value = obs.getValueAsString(locale);
			}
			catch (RuntimeException e) {
				continue;
			}
			out.add(new SbarSuggestionBuilder.Observation(patientId, field, (Integer) line[0], 0, label(obs.getConcept(),
			    locale), value, obs.getDateCreated()));
		}
		return out;
	}
	
	/** Marker in an SBAR concept list for the position of abnormal laboratory results. */
	static final String ABNORMAL_LABS = "ABNORMAL_LABS";
	
	static final String GP_LAB_ORDER_TYPE = "laboratorymanagement.orderType.labOrderTypeId";
	
	static final int MAX_LAB_LINES = 10;
	
	/**
	 * The later of the admission time and the optional maximum-age limit. Null when the patient has
	 * no active visit: then nothing from the records is used, because every value must belong to
	 * the current admission.
	 */
	static Date cutoff(Date admission, Date since) {
		if (admission == null)
			return null;
		return since != null && since.after(admission) ? since : admission;
	}
	
	/**
	 * Values saved together with the admission count as part of it: the admission form (clinical
	 * summary, final diagnosis, investigation summary) is saved in the same second as the admission
	 * time. One second of tolerance covers database time rounding.
	 */
	static final long SAME_SAVE_TOLERANCE_MILLIS = 1000L;
	
	/** True when the value was created at or after the cut-off (the current admission), see above. */
	static boolean createdAfter(Date created, Date cutoff) {
		return cutoff != null && created != null && created.getTime() >= cutoff.getTime() - SAME_SAVE_TOLERANCE_MILLIS;
	}
	
	/** Lower bound for database queries, matching {@link #createdAfter(Date, Date)}. */
	static Date queryBound(Date cutoff) {
		return cutoff == null ? null : new Date(cutoff.getTime() - SAME_SAVE_TOLERANCE_MILLIS - 1);
	}
	
	/**
	 * Latest result of each laboratory test (results attached to orders of the configured
	 * laboratory order type) created after the patient's admission, kept only when abnormal.
	 * Patients without an active visit are skipped.
	 */
	private List<SbarSuggestionBuilder.Observation> abnormalLabResults(List<Integer> patientIds,
	        Map<Integer, Date> admissions, Date since, String field, int order, Locale locale) {
		List<SbarSuggestionBuilder.Observation> out = new ArrayList<SbarSuggestionBuilder.Observation>();
		Integer orderTypeId = null;
		try {
			String value = Context.getAdministrationService().getGlobalProperty(GP_LAB_ORDER_TYPE, "").trim();
			orderTypeId = value.isEmpty() ? null : Integer.valueOf(value);
		}
		catch (NumberFormatException ignored) {}
		if (orderTypeId == null || admissions.isEmpty())
			return out;
		Date earliest = null;
		List<Integer> admitted = new ArrayList<Integer>();
		for (Integer patientId : patientIds) {
			Date admission = admissions.get(patientId);
			if (admission == null)
				continue;
			admitted.add(patientId);
			if (earliest == null || admission.before(earliest))
				earliest = admission;
		}
		Set<String> seen = new java.util.HashSet<String>();
		Map<Integer, Integer> lines = new HashMap<Integer, Integer>();
		for (Obs obs : dao.getLabResultObs(admitted, orderTypeId, queryBound(earliest))) {
			Integer patientId = obs.getPersonId();
			if (!seen.add(patientId + "|" + obs.getConcept().getConceptId()))
				continue; // newest first: only the latest result of each test
			if (!createdAfter(obs.getDateCreated(), cutoff(admissions.get(patientId), since)))
				continue;
			String flag = abnormalFlag(obs, locale);
			if (flag == null)
				continue;
			int count = lines.containsKey(patientId) ? lines.get(patientId) : 0;
			if (count >= MAX_LAB_LINES)
				continue;
			lines.put(patientId, count + 1);
			String value = obs.getValueAsString(locale);
			ConceptNumeric numeric = obs.getValueNumeric() == null ? null : Context.getConceptService().getConceptNumeric(
			    obs.getConcept().getConceptId());
			if (numeric != null && numeric.getUnits() != null && !numeric.getUnits().trim().isEmpty())
				value += " " + numeric.getUnits().trim();
			out.add(new SbarSuggestionBuilder.Observation(patientId, field, order + count + 1, 0, "Abnormal lab - "
			        + label(obs.getConcept(), locale), value + " " + flagMarker(flag), obs.getDateCreated()));
		}
		return out;
	}
	
	/**
	 * Visible marker of an abnormal result, e.g. "[\u2193 LOW]". The page highlights lines that
	 * contain it, and it survives removing titles and dates.
	 */
	static String flagMarker(String flag) {
		if ("critical high".equals(flag))
			return "[\u2191\u2191 CRITICAL HIGH]";
		if ("critical low".equals(flag))
			return "[\u2193\u2193 CRITICAL LOW]";
		if ("high".equals(flag))
			return "[\u2191 HIGH]";
		if ("low".equals(flag))
			return "[\u2193 LOW]";
		return "[" + flag.toUpperCase() + "]";
	}
	
	/**
	 * "critical high", "high", "low", "critical low" or "abnormal" when a result is outside the
	 * test's reference range or coded Positive/Abnormal/Reactive/Detected; null when normal or
	 * unknown.
	 */
	static String abnormalFlag(Obs obs, Locale locale) {
		if (obs.getValueNumeric() != null) {
			ConceptNumeric range = Context.getConceptService().getConceptNumeric(obs.getConcept().getConceptId());
			return range == null ? null : numericFlag(obs.getValueNumeric(), range.getLowCritical(), range.getLowNormal(),
			    range.getHiNormal(), range.getHiCritical());
		}
		if (obs.getValueCoded() != null) {
			ConceptName name = obs.getValueCoded().getName(locale);
			if (name == null)
				name = obs.getValueCoded().getName();
			return name != null && isAbnormalAnswer(name.getName()) ? "abnormal" : null;
		}
		return null;
	}
	
	static String numericFlag(double value, Double lowCritical, Double lowNormal, Double hiNormal, Double hiCritical) {
		if (hiCritical != null && value >= hiCritical)
			return "critical high";
		if (lowCritical != null && value <= lowCritical)
			return "critical low";
		if (hiNormal != null && value > hiNormal)
			return "high";
		if (lowNormal != null && value < lowNormal)
			return "low";
		return null;
	}
	
	static boolean isAbnormalAnswer(String answer) {
		if (answer == null)
			return false;
		String a = answer.trim().toUpperCase();
		return a.equals("POSITIVE") || a.startsWith("POSITIVE ") || a.equals("ABNORMAL") || a.equals("REACTIVE")
		        || a.equals("DETECTED");
	}
	
	/**
	 * Accepts a concept UUID, a numeric concept ID, a SOURCE:CODE mapping or a fully specified
	 * name.
	 */
	private Concept resolveConcept(String reference) {
		if (reference == null || reference.isEmpty())
			return null;
		try {
			if (reference.matches("\\d+"))
				return Context.getConceptService().getConcept(Integer.valueOf(reference));
			int colon = reference.indexOf(':');
			if (colon > 0)
				return Context.getConceptService().getConceptByMapping(reference.substring(colon + 1).trim(),
				    reference.substring(0, colon).trim());
			Concept concept = Context.getConceptService().getConceptByUuid(reference);
			return concept != null ? concept : Context.getConceptService().getConcept(reference);
		}
		catch (RuntimeException e) {
			return null;
		}
	}
	
	private static String label(Concept concept, Locale locale) {
		ConceptName name = concept.getName(locale);
		if (name == null)
			name = concept.getName();
		return name == null ? "Concept " + concept.getConceptId() : SbarSuggestionBuilder.readableLabel(name.getName());
	}
	
	private static Date daysAgo(int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -days);
		return calendar.getTime();
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PatientHandover> getRecentHandoversByLocation(Location location, String careSetting, int limit)
	        throws APIException {
		return dao.getRecentHandoversByLocation(location, careSetting, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PatientHandover> getHandoversByLocationAndDateRange(Location location, Date fromDate, Date toDate)
	        throws APIException {
		return dao.getHandoversByLocationAndDateRange(location, fromDate, toDate);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Integer> getActivePatientIdsByEncounterLocations(List<Integer> locationIds, Date minimumEncounterDate)
	        throws APIException {
		return dao.getActivePatientIdsByEncounterLocations(locationIds, minimumEncounterDate, null);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Integer> getActivePatientIdsByEncounterLocations(List<Integer> locationIds, Date from, Date toExclusive) {
		return dao.getActivePatientIdsByEncounterLocations(locationIds, from, toExclusive);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Integer> searchActivePatientIds(String query, int limit) throws APIException {
		return dao.searchActivePatientIds(query, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<EmergencyTrolleyCatalogItem> getActiveTrolleyCatalog(Location location) {
		return dao.getActiveTrolleyCatalog(location);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<EmergencyTrolleyUnit> getActiveTrolleyUnits(Location location) {
		return dao.getActiveTrolleyUnits(location);
	}
	
	@Transactional(readOnly = true)
	public EmergencyTrolleyUnit getTrolleyUnit(Integer id) {
		return dao.getTrolleyUnit(id);
	}
	
	@Override
	@Transactional
	public EmergencyTrolleyUnit saveTrolleyUnit(EmergencyTrolleyUnit trolley) {
		return dao.saveTrolleyUnit(trolley);
	}
	
	@Transactional(readOnly = true)
	public List<EmergencyTrolleyCatalogItem> getActiveTrolleyCatalog(EmergencyTrolleyUnit trolley) {
		return dao.getActiveTrolleyCatalog(trolley);
	}
	
	@Override
	@Transactional(readOnly = true)
	public EmergencyTrolleyCatalogItem getTrolleyCatalogItem(Integer id) {
		return dao.getTrolleyCatalogItem(id);
	}
	
	@Override
	@Transactional
	public EmergencyTrolleyCatalogItem saveTrolleyCatalogItem(EmergencyTrolleyCatalogItem item) {
		return dao.saveTrolleyCatalogItem(item);
	}
	
	@Override
	@Transactional(readOnly = true)
	public EmergencyTrolleyCatalogItem findTrolleyCatalogItem(Location location, String name) {
		return dao.findTrolleyCatalogItem(location, name);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<EmergencyTrolleyHandover> getTrolleyHandovers(Location location, int limit) {
		return dao.getTrolleyHandovers(location, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public EmergencyTrolleyHandover getTrolleyHandoverByUuid(String uuid) {
		return dao.getTrolleyHandoverByUuid(uuid);
	}
	
	@Override
	@Transactional
	public EmergencyTrolleyHandover saveTrolleyHandover(EmergencyTrolleyHandover handover) {
		return dao.saveTrolleyHandover(handover);
	}
	
	@Override
	@Transactional
	public EmergencyTrolleyHandover saveTrolleyHandover(EmergencyTrolleyHandover handover, List<HandoverTeamMember> team) {
		dao.saveTrolleyHandover(handover);
		saveTeam(team);
		return handover;
	}
	
	@Override
	@Transactional
	public EmergencyTrolleyHandover receiveTrolleyHandover(EmergencyTrolleyHandover handover) {
		if (handover == null || handover.getId() == null)
			throw new APIException("Only a saved trolley handover can be received.");
		String currentStatus = dao.lockTrolleyHandoverStatus(handover.getId());
		if (!STATUS_PENDING.equals(currentStatus))
			throw new APIException("This trolley handover is no longer pending and cannot be received.");
		User user = Context.getAuthenticatedUser();
		Date now = new Date();
		handover.setStatus(STATUS_RECEIVED);
		handover.setDateReceived(now);
		handover.setReceivedBy(user);
		handover.setChangedBy(user);
		handover.setDateChanged(now);
		for (EmergencyTrolleyHandoverItem snapshot : handover.getItems()) {
			EmergencyTrolleyCatalogItem item = snapshot.getCatalogItem();
			item.setCurrentQuantity(snapshot.getHandedQuantity());
			dao.saveTrolleyCatalogItem(item);
		}
		return dao.saveTrolleyHandover(handover);
	}
	
	@Override
	@Transactional
	public EmergencyTrolleyImport saveTrolleyImport(EmergencyTrolleyImport value) {
		return dao.saveTrolleyImport(value);
	}
	
	@Override
	@Transactional
	public PharmacyHandover savePharmacyHandover(PharmacyHandover h) {
		return dao.savePharmacyHandover(h);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PharmacyHandover> getPharmacyHandovers(Location l, int limit) {
		return dao.getPharmacyHandovers(l, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PharmacyHandover> getPendingPharmacyHandovers(Location location, Person receivingPerson, int limit) {
		return dao.getPendingPharmacyHandovers(location, receivingPerson, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PharmacyHandover> getPendingPharmacyTeamHandovers(Location location, Person person, int limit) {
		return dao.getPendingPharmacyTeamHandovers(location, person, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public PharmacyHandover getPharmacyHandoverByUuid(String u) {
		return dao.getPharmacyHandoverByUuid(u);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<EquipmentItem> getActiveEquipment(Location location) {
		return dao.getActiveEquipment(location);
	}
	
	@Override
	@Transactional
	public EquipmentItem saveEquipmentItem(EquipmentItem item) {
		return dao.saveEquipmentItem(item);
	}
	
	@Override
	@Transactional(readOnly = true)
	public EquipmentHandover getEquipmentHandoverByUuid(String uuid) {
		return dao.getEquipmentHandoverByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<EquipmentHandover> getEquipmentHandovers(Location location, int limit) {
		return dao.getEquipmentHandovers(location, limit);
	}
	
	@Override
	@Transactional
	public EquipmentHandover saveEquipmentHandover(EquipmentHandover handover) {
		return dao.saveEquipmentHandover(handover);
	}
	
	@Override
	@Transactional
	public EquipmentHandover saveEquipmentHandover(EquipmentHandover handover, List<HandoverTeamMember> team) {
		dao.saveEquipmentHandover(handover);
		saveTeam(team);
		return handover;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<PendingLaboratoryPatient> getPendingLaboratoryPatients(List<Integer> locationIds, Integer orderTypeId,
	        Integer patientId, String search, Date fromDate, Date toDate, int limit) {
		return dao.getPendingLaboratoryPatients(locationIds, orderTypeId, patientId, search, fromDate, toDate, limit);
	}
	
	@Override
	@Transactional
	public LaboratoryHandover saveLaboratoryHandover(LaboratoryHandover handover) {
		return dao.saveLaboratoryHandover(handover);
	}
	
	@Override
	@Transactional
	public LaboratoryHandover saveLaboratoryHandover(LaboratoryHandover handover, List<HandoverTeamMember> team) {
		dao.saveLaboratoryHandover(handover);
		saveTeam(team);
		return handover;
	}
	
	@Override
	@Transactional(readOnly = true)
	public LaboratoryHandover getLaboratoryHandoverByUuid(String uuid) {
		return dao.getLaboratoryHandoverByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<LaboratoryHandover> getLaboratoryHandovers(Location location, int limit) {
		return dao.getLaboratoryHandovers(location, limit);
	}
}
