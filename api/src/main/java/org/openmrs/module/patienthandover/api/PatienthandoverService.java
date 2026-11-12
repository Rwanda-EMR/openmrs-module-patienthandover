package org.openmrs.module.patienthandover.api;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.openmrs.Location;
import org.openmrs.User;
import org.openmrs.Person;
import org.openmrs.Patient;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.PharmacyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyCatalogItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
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

public interface PatienthandoverService extends OpenmrsService {
	
	@Authorized(PatienthandoverConfig.MODULE_PRIVILEGE)
	HandoverTeamMember saveHandoverTeamMember(HandoverTeamMember member) throws APIException;
	
	@Authorized()
	List<HandoverTeamMember> getHandoverTeam(String handoverType, String transactionKey) throws APIException;
	
	/**
	 * Loads the teams of several transactions in one query.
	 * 
	 * @return a map from transaction key to its non-voided members; every requested key is present
	 */
	@Authorized()
	Map<String, List<HandoverTeamMember>> getHandoverTeams(String handoverType, Collection<String> transactionKeys)
	        throws APIException;
	
	/**
	 * Records the signature of an unsigned team member. The team rows are locked for the duration
	 * of the transaction so that concurrent signers are serialized and exactly one of them observes
	 * the completed team.
	 * 
	 * @return true when, after this signature, every outgoing and every receiving member has signed
	 * @throws APIException if the member is unknown, voided or already signed
	 */
	@Authorized()
	boolean signHandoverTeamMember(HandoverTeamMember member) throws APIException;
	
	@Authorized()
	PatientHandoverAuditEvent saveAuditEvent(PatientHandoverAuditEvent event) throws APIException;
	
	@Authorized()
	List<PatientHandoverAuditEvent> getAuditEventsByLocationAndDateRange(Location location, Date fromDate, Date toDate)
	        throws APIException;
	
	@Authorized()
	PatientHandover getPatientHandoverByUuid(String uuid) throws APIException;
	
	@Authorized(PatienthandoverConfig.MODULE_PRIVILEGE)
	PatientHandover savePatientHandover(PatientHandover handover) throws APIException;
	
	@Authorized(PatienthandoverConfig.MODULE_PRIVILEGE)
	List<PatientHandover> savePatientHandovers(List<PatientHandover> handovers) throws APIException;
	
	@Authorized(PatienthandoverConfig.MODULE_PRIVILEGE)
	List<PatientHandover> savePatientHandovers(List<PatientHandover> handovers, List<EmergencyTrolleyItem> trolleyItems)
	        throws APIException;
	
	@Authorized()
	List<EmergencyTrolleyItem> getTrolleyItemsByBatchUuid(String batchUuid) throws APIException;
	
	@Authorized()
	List<PatientHandover> getHandoversByBatchUuid(String batchUuid) throws APIException;
	
	@Authorized()
	List<PatientHandover> getPatientHandoversByPatient(Patient patient) throws APIException;
	
	/** The unsubmitted Make Handover form of a user at a location and care setting, or null. */
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	PatientHandoverDraft getPatientHandoverDraft(User user, Location location, String careSetting) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	PatientHandoverDraft savePatientHandoverDraft(PatientHandoverDraft draft) throws APIException;
	
	/** Removes the user's draft for a location and care setting, if any (after submit or discard). */
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	void deletePatientHandoverDraft(User user, Location location, String careSetting) throws APIException;
	
	/**
	 * Suggested Situation/Background/Assessment/Recommendation text for each patient, built from
	 * the concepts configured in the patienthandover.sbar.* global properties (latest value of each
	 * within the look-back window), falling back to the patient's previous handover. Returns an
	 * empty map when pre-filling is disabled.
	 * 
	 * @return patientId -> field name (situation, background, assessment, recommendation) ->
	 *         suggestion
	 */
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	Map<Integer, Map<String, SbarSuggestionBuilder.Suggestion>> getSbarSuggestions(List<Patient> patients)
	        throws APIException;
	
	@Authorized()
	List<PatientHandover> getRecentHandoversByLocation(Location location, String careSetting, int limit) throws APIException;
	
	@Authorized()
	List<PatientHandover> getHandoversByLocationAndDateRange(Location location, Date fromDate, Date toDate)
	        throws APIException;
	
	@Authorized()
	List<Integer> getActivePatientIdsByEncounterLocations(List<Integer> locationIds, Date minimumEncounterDate)
	        throws APIException;
	
	/**
	 * Patients with an active visit at the given locations whose visit started in [from,
	 * toExclusive). Either bound may be null.
	 */
	@Authorized()
	List<Integer> getActivePatientIdsByEncounterLocations(List<Integer> locationIds, Date from, Date toExclusive)
	        throws APIException;
	
	@Authorized()
	List<Integer> searchActivePatientIds(String query, int limit) throws APIException;
	
	@Authorized()
	List<EmergencyTrolleyCatalogItem> getActiveTrolleyCatalog(Location location) throws APIException;
	
	@Authorized()
	List<EmergencyTrolleyUnit> getActiveTrolleyUnits(Location location) throws APIException;
	
	@Authorized()
	EmergencyTrolleyUnit getTrolleyUnit(Integer id) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_MANAGE)
	EmergencyTrolleyUnit saveTrolleyUnit(EmergencyTrolleyUnit trolley) throws APIException;
	
	@Authorized()
	List<EmergencyTrolleyCatalogItem> getActiveTrolleyCatalog(EmergencyTrolleyUnit trolley) throws APIException;
	
	@Authorized()
	EmergencyTrolleyCatalogItem getTrolleyCatalogItem(Integer id) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_MANAGE)
	EmergencyTrolleyCatalogItem saveTrolleyCatalogItem(EmergencyTrolleyCatalogItem item) throws APIException;
	
	@Authorized()
	EmergencyTrolleyCatalogItem findTrolleyCatalogItem(Location location, String itemName) throws APIException;
	
	@Authorized()
	List<EmergencyTrolleyHandover> getTrolleyHandovers(Location location, int limit) throws APIException;
	
	@Authorized()
	EmergencyTrolleyHandover getTrolleyHandoverByUuid(String uuid) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	EmergencyTrolleyHandover saveTrolleyHandover(EmergencyTrolleyHandover handover) throws APIException;
	
	/** Saves a new trolley handover together with its team in a single transaction. */
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	EmergencyTrolleyHandover saveTrolleyHandover(EmergencyTrolleyHandover handover, List<HandoverTeamMember> team)
	        throws APIException;
	
	/**
	 * Marks a pending trolley handover as received and copies the handed quantities to the trolley
	 * catalogue. The handover row is locked first; a handover that is no longer pending (already
	 * received or cancelled) is rejected.
	 * 
	 * @throws APIException if the handover is not pending
	 */
	@Authorized(PatienthandoverConfig.PRIVILEGE_RECEIVE)
	EmergencyTrolleyHandover receiveTrolleyHandover(EmergencyTrolleyHandover handover) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_MANAGE)
	EmergencyTrolleyImport saveTrolleyImport(EmergencyTrolleyImport trolleyImport) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	PharmacyHandover savePharmacyHandover(PharmacyHandover handover) throws APIException;
	
	@Authorized()
	List<PharmacyHandover> getPharmacyHandovers(Location location, int limit) throws APIException;
	
	@Authorized()
	List<PharmacyHandover> getPendingPharmacyHandovers(Location location, Person receivingPerson, int limit)
	        throws APIException;
	
	@Authorized()
	List<PharmacyHandover> getPendingPharmacyTeamHandovers(Location location, Person person, int limit) throws APIException;
	
	@Authorized()
	PharmacyHandover getPharmacyHandoverByUuid(String uuid) throws APIException;
	
	@Authorized()
	List<EquipmentItem> getActiveEquipment(Location location) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	EquipmentItem saveEquipmentItem(EquipmentItem item) throws APIException;
	
	@Authorized()
	EquipmentHandover getEquipmentHandoverByUuid(String uuid) throws APIException;
	
	@Authorized()
	List<EquipmentHandover> getEquipmentHandovers(Location location, int limit) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	EquipmentHandover saveEquipmentHandover(EquipmentHandover handover) throws APIException;
	
	/** Saves a new equipment handover together with its team in a single transaction. */
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	EquipmentHandover saveEquipmentHandover(EquipmentHandover handover, List<HandoverTeamMember> team) throws APIException;
	
	@Authorized()
	List<PendingLaboratoryPatient> getPendingLaboratoryPatients(List<Integer> locationIds, Integer orderTypeId,
	        Integer patientId, String search, Date fromDate, Date toDate, int limit) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	LaboratoryHandover saveLaboratoryHandover(LaboratoryHandover handover) throws APIException;
	
	/** Saves a new laboratory handover together with its team in a single transaction. */
	@Authorized(PatienthandoverConfig.PRIVILEGE_CREATE)
	LaboratoryHandover saveLaboratoryHandover(LaboratoryHandover handover, List<HandoverTeamMember> team)
	        throws APIException;
	
	@Authorized()
	LaboratoryHandover getLaboratoryHandoverByUuid(String uuid) throws APIException;
	
	@Authorized()
	List<LaboratoryHandover> getLaboratoryHandovers(Location location, int limit) throws APIException;
}
