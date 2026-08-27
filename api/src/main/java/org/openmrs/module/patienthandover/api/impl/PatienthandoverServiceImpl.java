package org.openmrs.module.patienthandover.api.impl;

import java.util.Date;
import java.util.List;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.api.dao.PatienthandoverDao;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyCatalogItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandoverItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyImport;
import org.openmrs.module.patienthandover.domain.PatientHandoverAuditEvent;
import org.springframework.transaction.annotation.Transactional;

public class PatienthandoverServiceImpl extends BaseOpenmrsService implements PatienthandoverService {
	
	private PatienthandoverDao dao;
	
	/**
	 * Injected in moduleApplicationContext.xml
	 */
	public void setDao(PatienthandoverDao dao) {
		this.dao = dao;
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
		for (PatientHandover handover : handovers) {
			dao.savePatientHandover(handover);
		}
		return handovers;
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
		return dao.getActivePatientIdsByEncounterLocations(locationIds, minimumEncounterDate);
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
	public EmergencyTrolleyHandover receiveTrolleyHandover(EmergencyTrolleyHandover handover) {
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
}
