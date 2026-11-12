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

import org.openmrs.Concept;
import org.openmrs.ConceptName;
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
		
		// concept -> {field, position in that field's list}; a concept configured twice keeps its first use.
		Map<Concept, Object[]> mapping = new LinkedHashMap<Concept, Object[]>();
		for (String field : SbarSuggestionBuilder.FIELDS) {
			String configured = Context.getAdministrationService().getGlobalProperty(
			    "patienthandover.sbar." + field + "Concepts", "");
			int order = 0;
			for (String reference : configured.split(",")) {
				Concept concept = resolveConcept(reference.trim());
				if (concept == null && !reference.trim().isEmpty())
					log.warn("SBAR pre-fill: concept '" + reference.trim() + "' configured for " + field
					        + " was not found and is ignored");
				if (concept != null && !mapping.containsKey(concept))
					mapping.put(concept, new Object[] { field, order });
				order++;
			}
		}
		List<Integer> patientIds = new ArrayList<Integer>();
		for (Patient patient : patients)
			if (patient != null && patient.getPatientId() != null)
				patientIds.add(patient.getPatientId());
		
		Locale locale = Context.getLocale();
		List<SbarSuggestionBuilder.Observation> observations = new ArrayList<SbarSuggestionBuilder.Observation>();
		if (!mapping.isEmpty())
			for (Obs obs : dao.getLatestObs(patientIds, new ArrayList<Concept>(mapping.keySet()), since)) {
				Object[] target = mapping.get(obs.getConcept());
				if (target == null || obs.isObsGrouping())
					continue;
				String value;
				try {
					value = obs.getValueAsString(locale);
				}
				catch (RuntimeException e) {
					continue;
				}
				observations.add(new SbarSuggestionBuilder.Observation(obs.getPersonId(), (String) target[0],
				        (Integer) target[1], label(obs.getConcept(), locale), value, obs.getObsDatetime()));
			}
		
		Map<Integer, Map<String, SbarSuggestionBuilder.PreviousText>> previous = new HashMap<Integer, Map<String, SbarSuggestionBuilder.PreviousText>>();
		for (PatientHandover h : dao.getRecentHandoversByPatients(patientIds, daysAgo(PREVIOUS_HANDOVER_DAYS))) {
			Integer patientId = h.getPatient().getPatientId();
			if (previous.containsKey(patientId))
				continue; // newest first: keep only the latest handover
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
		return new SbarSuggestionBuilder().build(observations, previous);
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
