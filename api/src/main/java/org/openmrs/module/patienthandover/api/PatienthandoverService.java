package org.openmrs.module.patienthandover.api;

import java.util.Date;
import java.util.List;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyCatalogItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyImport;
import org.openmrs.module.patienthandover.domain.PatientHandoverAuditEvent;

public interface PatienthandoverService extends OpenmrsService {
	
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
	
	@Authorized()
	List<PatientHandover> getRecentHandoversByLocation(Location location, String careSetting, int limit) throws APIException;
	
	@Authorized()
	List<PatientHandover> getHandoversByLocationAndDateRange(Location location, Date fromDate, Date toDate)
	        throws APIException;
	
	@Authorized()
	List<Integer> getActivePatientIdsByEncounterLocations(List<Integer> locationIds, Date minimumEncounterDate)
	        throws APIException;
	
	@Authorized()
	List<Integer> searchActivePatientIds(String query, int limit) throws APIException;
	
	@Authorized()
	List<EmergencyTrolleyCatalogItem> getActiveTrolleyCatalog(Location location) throws APIException;
	
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
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_RECEIVE)
	EmergencyTrolleyHandover receiveTrolleyHandover(EmergencyTrolleyHandover handover) throws APIException;
	
	@Authorized(PatienthandoverConfig.PRIVILEGE_MANAGE)
	EmergencyTrolleyImport saveTrolleyImport(EmergencyTrolleyImport trolleyImport) throws APIException;
}
