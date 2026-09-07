package org.openmrs.module.patienthandover.api.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.SQLQuery;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Location;
import org.openmrs.Person;
import org.openmrs.Patient;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.PharmacyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyCatalogItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyImport;
import org.openmrs.module.patienthandover.domain.PatientHandoverAuditEvent;
import org.openmrs.module.patienthandover.domain.HandoverTeamMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository("patienthandover.PatienthandoverDao")
public class PatienthandoverDao {
	
	@Autowired
	private DbSessionFactory sessionFactory;
	
	private DbSession getSession() {
		return sessionFactory.getCurrentSession();
	}
	
	public HandoverTeamMember saveHandoverTeamMember(HandoverTeamMember member) {
		getSession().saveOrUpdate(member);
		return member;
	}
	
	@SuppressWarnings("unchecked")
	public List<HandoverTeamMember> getHandoverTeam(String type, String key) {
		return getSession().createCriteria(HandoverTeamMember.class).add(Restrictions.eq("handoverType", type))
		        .add(Restrictions.eq("transactionKey", key)).add(Restrictions.eq("voided", false))
		        .addOrder(Order.asc("role")).addOrder(Order.asc("id")).list();
	}
	
	public PatientHandoverAuditEvent saveAuditEvent(PatientHandoverAuditEvent event) {
		getSession().save(event);
		return event;
	}
	
	@SuppressWarnings("unchecked")
	public List<PatientHandoverAuditEvent> getAuditEventsByLocationAndDateRange(Location location, Date fromDate, Date toDate) {
		return getSession().createCriteria(PatientHandoverAuditEvent.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.ge("eventDate", fromDate)).add(Restrictions.lt("eventDate", toDate))
		        .addOrder(Order.desc("eventDate")).list();
	}
	
	public PatientHandover getPatientHandoverByUuid(String uuid) {
		return (PatientHandover) getSession().createCriteria(PatientHandover.class).add(Restrictions.eq("uuid", uuid))
		        .uniqueResult();
	}
	
	public PatientHandover savePatientHandover(PatientHandover handover) {
		getSession().saveOrUpdate(handover);
		return handover;
	}
	
	public EmergencyTrolleyItem saveTrolleyItem(EmergencyTrolleyItem item) {
		getSession().saveOrUpdate(item);
		return item;
	}
	
	@SuppressWarnings("unchecked")
	public List<EmergencyTrolleyItem> getTrolleyItemsByBatchUuid(String batchUuid) {
		return getSession().createCriteria(EmergencyTrolleyItem.class).add(Restrictions.eq("batchUuid", batchUuid))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.asc("id")).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<PatientHandover> getHandoversByBatchUuid(String batchUuid) {
		return getSession().createCriteria(PatientHandover.class).add(Restrictions.eq("batchUuid", batchUuid))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.asc("id")).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<PatientHandover> getPatientHandoversByPatient(Patient patient) {
		return getSession().createCriteria(PatientHandover.class).add(Restrictions.eq("patient", patient))
		        .addOrder(Order.desc("dateCreated")).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<PatientHandover> getRecentHandoversByLocation(Location location, String careSetting, int limit) {
		return getSession().createCriteria(PatientHandover.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("careSetting", careSetting)).add(Restrictions.eq("voided", false))
		        .addOrder(Order.desc("dateCreated")).setMaxResults(limit).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<PatientHandover> getHandoversByLocationAndDateRange(Location location, Date fromDate, Date toDate) {
		return getSession().createCriteria(PatientHandover.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.ge("dateCreated", fromDate)).add(Restrictions.lt("dateCreated", toDate))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.desc("dateCreated")).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<Integer> getActivePatientIdsByEncounterLocations(List<Integer> locationIds, Date minimumEncounterDate) {
		if (locationIds == null || locationIds.isEmpty()) {
			return new ArrayList<Integer>();
		}
		String sql = "select e.patient_id " + "from encounter e "
		        + "inner join (select patient_id, max(encounter_id) latest_encounter_id "
		        + "            from encounter where voided = 0 group by patient_id) latest "
		        + "        on latest.latest_encounter_id = e.encounter_id "
		        + "inner join visit v on v.visit_id = e.visit_id and v.patient_id = e.patient_id "
		        + "        and v.voided = 0 and v.date_started <= now() "
		        + "        and (v.date_stopped is null or v.date_stopped > now()) "
		        + "where e.voided = 0 and e.location_id in (:locationIds) ";
		if (minimumEncounterDate != null) {
			sql += "and e.encounter_datetime >= :minimumEncounterDate ";
		}
		sql += "order by e.encounter_datetime desc";
		SQLQuery query = getSession().createSQLQuery(sql);
		query.setParameterList("locationIds", locationIds);
		if (minimumEncounterDate != null) {
			query.setTimestamp("minimumEncounterDate", minimumEncounterDate);
		}
		List<Number> values = query.list();
		List<Integer> patientIds = new ArrayList<Integer>();
		for (Number value : values) {
			patientIds.add(value.intValue());
		}
		return patientIds;
	}
	
	@SuppressWarnings("unchecked")
	public List<Integer> searchActivePatientIds(String searchText, int limit) {
		if (searchText == null || searchText.trim().isEmpty()) {
			return new ArrayList<Integer>();
		}
		String sql = "select distinct p.patient_id " + "from patient p inner join person pe on pe.person_id = p.patient_id "
		        + "left join person_name pn on pn.person_id = p.patient_id and pn.voided = 0 "
		        + "left join patient_identifier pi on pi.patient_id = p.patient_id and pi.voided = 0 "
		        + "where p.voided = 0 and pe.voided = 0 and ("
		        + "lower(concat_ws(' ', pn.given_name, pn.middle_name, pn.family_name, pn.family_name2)) like :search "
		        + "or lower(pi.identifier) like :search) order by p.patient_id desc";
		SQLQuery query = getSession().createSQLQuery(sql);
		query.setString("search", "%" + searchText.trim().toLowerCase() + "%");
		query.setMaxResults(Math.max(1, Math.min(limit, 100)));
		List<Number> values = query.list();
		List<Integer> patientIds = new ArrayList<Integer>();
		for (Number value : values) {
			patientIds.add(value.intValue());
		}
		return patientIds;
	}
	
	@SuppressWarnings("unchecked")
	public List<EmergencyTrolleyCatalogItem> getActiveTrolleyCatalog(Location location) {
		return getSession().createCriteria(EmergencyTrolleyCatalogItem.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("active", true)).add(Restrictions.eq("voided", false)).addOrder(Order.asc("itemName"))
		        .list();
	}
	
	public EmergencyTrolleyCatalogItem getTrolleyCatalogItem(Integer id) {
		return (EmergencyTrolleyCatalogItem) getSession().get(EmergencyTrolleyCatalogItem.class, id);
	}
	
	public EmergencyTrolleyCatalogItem saveTrolleyCatalogItem(EmergencyTrolleyCatalogItem item) {
		getSession().saveOrUpdate(item);
		return item;
	}
	
	public EmergencyTrolleyCatalogItem findTrolleyCatalogItem(Location location, String name) {
		return (EmergencyTrolleyCatalogItem) getSession().createCriteria(EmergencyTrolleyCatalogItem.class)
		        .add(Restrictions.eq("location", location)).add(Restrictions.ilike("itemName", name))
		        .add(Restrictions.eq("voided", false)).setMaxResults(1).uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<EmergencyTrolleyHandover> getTrolleyHandovers(Location location, int limit) {
		return getSession().createCriteria(EmergencyTrolleyHandover.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("voided", false)).add(Restrictions.isNull("dateCancelled"))
		        .addOrder(Order.desc("dateSubmitted")).setMaxResults(limit).list();
	}
	
	public EmergencyTrolleyHandover getTrolleyHandoverByUuid(String uuid) {
		return (EmergencyTrolleyHandover) getSession().createCriteria(EmergencyTrolleyHandover.class)
		        .add(Restrictions.eq("uuid", uuid)).uniqueResult();
	}
	
	public EmergencyTrolleyHandover saveTrolleyHandover(EmergencyTrolleyHandover handover) {
		getSession().saveOrUpdate(handover);
		return handover;
	}
	
	public EmergencyTrolleyImport saveTrolleyImport(EmergencyTrolleyImport value) {
		getSession().saveOrUpdate(value);
		return value;
	}
	
	public PharmacyHandover savePharmacyHandover(PharmacyHandover h) {
		getSession().saveOrUpdate(h);
		return h;
	}
	
	@SuppressWarnings("unchecked")
	public List<PharmacyHandover> getPharmacyHandovers(Location l, int limit) {
		return getSession().createCriteria(PharmacyHandover.class).add(Restrictions.eq("location", l))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.desc("dateSubmitted")).setMaxResults(limit).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<PharmacyHandover> getPendingPharmacyHandovers(Location location, Person receivingPerson, int limit) {
		return getSession()
		        .createQuery(
		            "from PharmacyHandover h where h.location = :location "
		                    + "and h.receivingProvider.person = :person and h.acknowledged = false and h.voided = false and h.dateCancelled is null "
		                    + "order by h.dateSubmitted desc").setParameter("location", location)
		        .setParameter("person", receivingPerson).setMaxResults(limit).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<PharmacyHandover> getPendingPharmacyTeamHandovers(Location location, Person person, int limit) {
		return getSession()
		        .createQuery(
		            "select distinct h from PharmacyHandover h join h.participants p "
		                    + "where h.location = :location and p.person = :person and p.signed = false "
		                    + "and p.voided = false and h.acknowledged = false and h.voided = false and h.dateCancelled is null order by h.dateSubmitted desc")
		        .setParameter("location", location).setParameter("person", person).setMaxResults(limit).list();
	}
	
	public PharmacyHandover getPharmacyHandoverByUuid(String u) {
		return (PharmacyHandover) getSession().createCriteria(PharmacyHandover.class).add(Restrictions.eq("uuid", u))
		        .uniqueResult();
	}
}
