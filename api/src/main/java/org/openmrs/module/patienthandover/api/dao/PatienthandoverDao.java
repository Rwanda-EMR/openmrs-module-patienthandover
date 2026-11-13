package org.openmrs.module.patienthandover.api.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.SQLQuery;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.User;
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
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyUnit;
import org.openmrs.module.patienthandover.domain.PatientHandoverAuditEvent;
import org.openmrs.module.patienthandover.domain.HandoverTeamMember;
import org.openmrs.module.patienthandover.domain.EquipmentItem;
import org.openmrs.module.patienthandover.domain.EquipmentHandover;
import org.openmrs.module.patienthandover.domain.LaboratoryHandover;
import org.openmrs.module.patienthandover.domain.PendingLaboratoryPatient;
import org.openmrs.module.patienthandover.domain.PatientHandoverDraft;
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
	
	@SuppressWarnings("unchecked")
	public List<HandoverTeamMember> getHandoverTeams(String type, List<String> keys) {
		if (keys == null || keys.isEmpty())
			return new ArrayList<HandoverTeamMember>();
		List<HandoverTeamMember> result = new ArrayList<HandoverTeamMember>();
		// Chunked to keep the IN list well below database parameter limits.
		for (int from = 0; from < keys.size(); from += 500) {
			List<String> chunk = keys.subList(from, Math.min(keys.size(), from + 500));
			result.addAll(getSession().createCriteria(HandoverTeamMember.class).add(Restrictions.eq("handoverType", type))
			        .add(Restrictions.in("transactionKey", chunk)).add(Restrictions.eq("voided", false))
			        .addOrder(Order.asc("role")).addOrder(Order.asc("id")).list());
		}
		return result;
	}
	
	/**
	 * Locks the non-voided team rows of one transaction (SELECT ... FOR UPDATE) and returns their
	 * latest committed state as {id, role, signed}.
	 */
	@SuppressWarnings("unchecked")
	public List<Object[]> lockHandoverTeam(String type, String key) {
		return getSession()
		        .createSQLQuery(
		            "select handover_team_member_id, participant_role, signed from handover_team_member "
		                    + "where handover_type = :type and transaction_key = :key and voided = 0 "
		                    + "order by handover_team_member_id for update").setString("type", type).setString("key", key)
		        .list();
	}
	
	/**
	 * Locks one trolley handover row (SELECT ... FOR UPDATE) and returns its latest committed
	 * status, or CANCELLED / VOIDED when it has been cancelled or voided, or null when it does not
	 * exist.
	 */
	public String lockTrolleyHandoverStatus(Integer handoverId) {
		Object[] row = (Object[]) getSession()
		        .createSQLQuery(
		            "select status, date_cancelled, voided from emergency_trolley_handover where handover_id = :id for update")
		        .setInteger("id", handoverId).uniqueResult();
		if (row == null)
			return null;
		if (row[1] != null)
			return "CANCELLED";
		if (row[2] instanceof Boolean ? (Boolean) row[2] : row[2] instanceof Number && ((Number) row[2]).intValue() != 0)
			return "VOIDED";
		return (String) row[0];
	}
	
	public PatientHandoverDraft getPatientHandoverDraft(User user, Location location, String careSetting) {
		return (PatientHandoverDraft) getSession().createCriteria(PatientHandoverDraft.class)
		        .add(Restrictions.eq("user", user)).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("careSetting", careSetting)).setMaxResults(1).uniqueResult();
	}
	
	public PatientHandoverDraft savePatientHandoverDraft(PatientHandoverDraft draft) {
		getSession().saveOrUpdate(draft);
		return draft;
	}
	
	public void deletePatientHandoverDraft(PatientHandoverDraft draft) {
		getSession().delete(draft);
	}
	
	/**
	 * For each person and concept, the non-voided observation(s) created most recently (several
	 * when answers were saved together). Placeholder free text is ignored so the latest meaningful
	 * value is returned. {@code createdAfter} optionally ignores values created earlier; null means
	 * no limit.
	 */
	@SuppressWarnings("unchecked")
	public List<Obs> getLatestObs(List<Integer> personIds, List<Concept> concepts, Date createdAfter) {
		List<Obs> result = new ArrayList<Obs>();
		if (personIds == null || personIds.isEmpty() || concepts == null || concepts.isEmpty())
			return result;
		for (int from = 0; from < personIds.size(); from += 500) {
			List<Integer> chunk = personIds.subList(from, Math.min(personIds.size(), from + 500));
			org.hibernate.Query query = getSession().createQuery(
			    "from Obs o where o.personId in (:people) and o.concept in (:concepts) and o.voided = false "
			            + (createdAfter == null ? "" : "and o.dateCreated > :after ") + meaningful("o")
			            + " and o.dateCreated = (select max(n.dateCreated) from Obs n where n.personId = o.personId "
			            + "and n.concept = o.concept and n.voided = false" + meaningful("n")
			            + ") order by o.dateCreated desc");
			query.setParameterList("people", chunk).setParameterList("concepts", concepts);
			if (createdAfter != null)
				query.setTimestamp("after", createdAfter);
			result.addAll(query.list());
		}
		return result;
	}
	
	/**
	 * Start date and time of each patient's current visit (started, and not stopped or stopping in
	 * the future, as on the worklist), i.e. the current admission. Patients without an active visit
	 * are absent from the map.
	 */
	@SuppressWarnings("unchecked")
	public Map<Integer, Date> getActiveVisitStarts(List<Integer> patientIds) {
		Map<Integer, Date> starts = new HashMap<Integer, Date>();
		if (patientIds == null || patientIds.isEmpty())
			return starts;
		for (int from = 0; from < patientIds.size(); from += 500) {
			List<Integer> chunk = patientIds.subList(from, Math.min(patientIds.size(), from + 500));
			List<Object[]> rows = getSession()
			        .createQuery(
			            "select v.patient.patientId, max(v.startDatetime) from Visit v where v.patient.patientId in (:ids) "
			                    + "and v.voided = false and v.startDatetime <= :now "
			                    + "and (v.stopDatetime is null or v.stopDatetime > :now) group by v.patient.patientId")
			        .setParameterList("ids", chunk).setTimestamp("now", new Date()).list();
			for (Object[] row : rows)
				if (row[0] != null && row[1] != null)
					starts.put(((Number) row[0]).intValue(), (Date) row[1]);
		}
		return starts;
	}
	
	/**
	 * Date and time of each patient's latest non-voided encounter of the given types (for example
	 * the admission form). Patients without such an encounter are absent from the map.
	 */
	@SuppressWarnings("unchecked")
	public Map<Integer, Date> getLatestEncounterDatetimes(List<Integer> patientIds, List<String> encounterTypeUuids) {
		Map<Integer, Date> latest = new HashMap<Integer, Date>();
		if (patientIds == null || patientIds.isEmpty() || encounterTypeUuids == null || encounterTypeUuids.isEmpty())
			return latest;
		for (int from = 0; from < patientIds.size(); from += 500) {
			List<Integer> chunk = patientIds.subList(from, Math.min(patientIds.size(), from + 500));
			List<Object[]> rows = getSession()
			        .createQuery(
			            "select e.patient.patientId, max(e.encounterDatetime) from Encounter e where e.patient.patientId in (:ids) "
			                    + "and e.voided = false and e.encounterType.uuid in (:types) and e.encounterDatetime <= :now "
			                    + "group by e.patient.patientId").setParameterList("ids", chunk)
			        .setParameterList("types", encounterTypeUuids).setTimestamp("now", new Date()).list();
			for (Object[] row : rows)
				if (row[0] != null && row[1] != null)
					latest.put(((Number) row[0]).intValue(), (Date) row[1]);
		}
		return latest;
	}
	
	/**
	 * Date and time of every non-voided encounter of each patient between {@code since} and now,
	 * newest first. Used to find where the patient's current stretch of care starts.
	 */
	@SuppressWarnings("unchecked")
	public Map<Integer, List<Date>> getEncounterDatetimes(List<Integer> patientIds, Date since) {
		Map<Integer, List<Date>> times = new HashMap<Integer, List<Date>>();
		if (patientIds == null || patientIds.isEmpty())
			return times;
		for (int from = 0; from < patientIds.size(); from += 500) {
			List<Integer> chunk = patientIds.subList(from, Math.min(patientIds.size(), from + 500));
			List<Object[]> rows = getSession()
			        .createQuery(
			            "select e.patient.patientId, e.encounterDatetime from Encounter e where e.patient.patientId in (:ids) "
			                    + "and e.voided = false and e.encounterDatetime >= :since and e.encounterDatetime <= :now "
			                    + "order by e.patient.patientId, e.encounterDatetime desc").setParameterList("ids", chunk)
			        .setTimestamp("since", since).setTimestamp("now", new Date()).list();
			for (Object[] row : rows) {
				Integer patientId = ((Number) row[0]).intValue();
				List<Date> list = times.get(patientId);
				if (list == null) {
					list = new ArrayList<Date>();
					times.put(patientId, list);
				}
				list.add((Date) row[1]);
			}
		}
		return times;
	}
	
	/** UUID of the standard OpenMRS concept class "Diagnosis". */
	static final String DIAGNOSIS_CLASS_UUID = "8d4918b0-c2cc-11de-8d13-0010c6dffd0f";
	
	/**
	 * Diagnosis data created after {@code createdAfter}, newest first: coded answers whose concept
	 * class is Diagnosis (e.g. primary/secondary ICD, presumptive or chronic-care diagnoses), and
	 * meaningful free text recorded in a field whose name contains "diagnos" (non-coded diagnoses).
	 */
	@SuppressWarnings("unchecked")
	public List<Obs> getDiagnosisObs(List<Integer> personIds, Date createdAfter) {
		List<Obs> result = new ArrayList<Obs>();
		if (personIds == null || personIds.isEmpty() || createdAfter == null)
			return result;
		for (int from = 0; from < personIds.size(); from += 500) {
			List<Integer> chunk = personIds.subList(from, Math.min(personIds.size(), from + 500));
			result.addAll(getSession()
			        .createQuery(
			            "select o from Obs o left join o.valueCoded answer left join answer.conceptClass answerClass "
			                    + "where o.personId in (:people) and o.voided = false and o.dateCreated > :after and ("
			                    + "answerClass.uuid = :diagnosisClass or (o.valueText is not null" + meaningful("o")
			                    + " and exists (select n.conceptNameId from ConceptName n where n.concept = o.concept "
			                    + "and n.voided = false and lower(n.name) like '%diagnos%'))) order by o.dateCreated desc")
			        .setParameterList("people", chunk).setTimestamp("after", createdAfter)
			        .setString("diagnosisClass", DIAGNOSIS_CLASS_UUID).list());
		}
		return result;
	}
	
	/**
	 * Non-voided results (numeric or coded) attached to orders of the given order type and created
	 * after {@code createdAfter}, newest first.
	 */
	@SuppressWarnings("unchecked")
	public List<Obs> getLabResultObs(List<Integer> personIds, Integer orderTypeId, Date createdAfter) {
		List<Obs> result = new ArrayList<Obs>();
		if (personIds == null || personIds.isEmpty() || orderTypeId == null || createdAfter == null)
			return result;
		for (int from = 0; from < personIds.size(); from += 500) {
			List<Integer> chunk = personIds.subList(from, Math.min(personIds.size(), from + 500));
			result.addAll(getSession()
			        .createQuery(
			            "from Obs o where o.personId in (:people) and o.voided = false and o.order is not null "
			                    + "and o.order.orderType.orderTypeId = :orderType and o.dateCreated > :after "
			                    + "and (o.valueNumeric is not null or o.valueCoded is not null) order by o.dateCreated desc")
			        .setParameterList("people", chunk).setInteger("orderType", orderTypeId)
			        .setTimestamp("after", createdAfter).list());
		}
		return result;
	}
	
	/** HQL condition excluding observations whose free text is empty or a placeholder. */
	static String meaningful(String alias) {
		String text = "lower(trim(" + alias + ".valueText))";
		return " and (" + alias + ".valueText is null or (length(trim(" + alias + ".valueText)) > 1 and " + text
		        + " not in ('..', '...', '--', '---', '-.', '.-', 'n/a', 'na', 'nil', 'n.a', 'n.a.', 'xx', 'xxx', '00')))";
	}
	
	/**
	 * Non-voided, non-cancelled handovers of the given patients created on or after {@code since},
	 * newest first.
	 */
	@SuppressWarnings("unchecked")
	public List<PatientHandover> getRecentHandoversByPatients(List<Integer> patientIds, Date since) {
		List<PatientHandover> result = new ArrayList<PatientHandover>();
		if (patientIds == null || patientIds.isEmpty())
			return result;
		for (int from = 0; from < patientIds.size(); from += 500) {
			List<Integer> chunk = patientIds.subList(from, Math.min(patientIds.size(), from + 500));
			result.addAll(getSession()
			        .createQuery(
			            "from PatientHandover h where h.patient.patientId in (:patients) and h.voided = false "
			                    + "and h.cancelled = false and h.dateCreated >= :since order by h.dateCreated desc")
			        .setParameterList("patients", chunk).setTimestamp("since", since).list());
		}
		return result;
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
	
	public List<PatientHandover> savePatientHandovers(List<PatientHandover> handovers) {
		DbSession session = getSession();
		for (PatientHandover handover : handovers) {
			if (handover.getId() == null) {
				session.save(handover);
			} else {
				session.saveOrUpdate(handover);
			}
		}
		return handovers;
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
		return getSession()
		        .createCriteria(PatientHandover.class)
		        .add(
		            Restrictions.or(Restrictions.eq("location", location), Restrictions.eq("destinationLocation", location)))
		        .add(Restrictions.eq("careSetting", careSetting)).add(Restrictions.eq("voided", false))
		        .addOrder(Order.desc("dateCreated")).setMaxResults(limit).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<PatientHandover> getHandoversByLocationAndDateRange(Location location, Date fromDate, Date toDate) {
		return getSession()
		        .createCriteria(PatientHandover.class)
		        .add(
		            Restrictions.or(Restrictions.eq("location", location), Restrictions.eq("destinationLocation", location)))
		        .add(Restrictions.ge("dateCreated", fromDate)).add(Restrictions.lt("dateCreated", toDate))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.desc("dateCreated")).list();
	}
	
	@SuppressWarnings("unchecked")
	public List<Integer> getActivePatientIdsByEncounterLocations(List<Integer> locationIds, Date minimumAdmissionDate,
	        Date admissionBefore) {
		if (locationIds == null || locationIds.isEmpty()) {
			return new ArrayList<Integer>();
		}
		String sql = "select distinct e.patient_id from encounter e "
		        + "inner join patient p on p.patient_id = e.patient_id and p.voided = 0 "
		        + "inner join visit v on v.visit_id = e.visit_id and v.patient_id = e.patient_id "
		        + "        and v.voided = 0 and v.date_started <= now() "
		        + "        and (v.date_stopped is null or v.date_stopped > now()) "
		        + "where e.voided = 0 and (e.location_id in (:locationIds) or v.location_id in (:locationIds)) "
		        + "and not exists (select 1 from encounter newer "
		        + "                where newer.patient_id = e.patient_id and newer.visit_id = e.visit_id and newer.voided = 0 "
		        + "                  and (newer.encounter_datetime > e.encounter_datetime "
		        + "                       or (newer.encounter_datetime = e.encounter_datetime "
		        + "                           and newer.encounter_id > e.encounter_id))) ";
		if (minimumAdmissionDate != null) {
			sql += "and v.date_started >= :minimumAdmissionDate ";
		}
		if (admissionBefore != null) {
			sql += "and v.date_started < :admissionBefore ";
		}
		sql += "order by e.encounter_datetime desc";
		SQLQuery query = getSession().createSQLQuery(sql);
		query.setParameterList("locationIds", locationIds);
		if (minimumAdmissionDate != null) {
			query.setTimestamp("minimumAdmissionDate", minimumAdmissionDate);
		}
		if (admissionBefore != null) {
			query.setTimestamp("admissionBefore", admissionBefore);
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
	
	@SuppressWarnings("unchecked")
	public List<EmergencyTrolleyUnit> getActiveTrolleyUnits(Location location) {
		return getSession().createCriteria(EmergencyTrolleyUnit.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("active", true)).add(Restrictions.eq("voided", false))
		        .addOrder(Order.asc("trolleyName")).list();
	}
	
	public EmergencyTrolleyUnit getTrolleyUnit(Integer id) {
		return (EmergencyTrolleyUnit) getSession().get(EmergencyTrolleyUnit.class, id);
	}
	
	public EmergencyTrolleyUnit saveTrolleyUnit(EmergencyTrolleyUnit trolley) {
		getSession().saveOrUpdate(trolley);
		return trolley;
	}
	
	@SuppressWarnings("unchecked")
	public List<EmergencyTrolleyCatalogItem> getActiveTrolleyCatalog(EmergencyTrolleyUnit trolley) {
		return getSession().createCriteria(EmergencyTrolleyCatalogItem.class).add(Restrictions.eq("trolley", trolley))
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
	
	@SuppressWarnings("unchecked")
	public List<EquipmentItem> getActiveEquipment(Location location) {
		return getSession().createCriteria(EquipmentItem.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("active", true)).add(Restrictions.eq("voided", false))
		        .addOrder(Order.asc("equipmentName")).list();
	}
	
	public EquipmentItem saveEquipmentItem(EquipmentItem item) {
		getSession().saveOrUpdate(item);
		return item;
	}
	
	public EquipmentHandover getEquipmentHandoverByUuid(String uuid) {
		return (EquipmentHandover) getSession().createCriteria(EquipmentHandover.class).add(Restrictions.eq("uuid", uuid))
		        .add(Restrictions.eq("voided", false)).uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<EquipmentHandover> getEquipmentHandovers(Location location, int limit) {
		return getSession().createCriteria(EquipmentHandover.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.desc("dateSubmitted")).setMaxResults(limit).list();
	}
	
	public EquipmentHandover saveEquipmentHandover(EquipmentHandover handover) {
		getSession().saveOrUpdate(handover);
		return handover;
	}
	
	@SuppressWarnings("unchecked")
	public List<PendingLaboratoryPatient> getPendingLaboratoryPatients(List<Integer> locationIds, Integer orderTypeId,
	        Integer patientId, String search, Date fromDate, Date toDate, int limit) {
		if (locationIds == null || locationIds.isEmpty() || orderTypeId == null)
			return new ArrayList<PendingLaboratoryPatient>();
		String sql = "select o.patient_id, trim(o.accession_number), "
		        + "group_concat(distinct o.order_id order by o.order_id separator ','), "
		        + "min(coalesce(o.date_activated,o.date_created)), "
		        + "(select concat_ws(' ',n.given_name,n.middle_name,n.family_name,n.family_name2) from person_name n where n.person_id=o.patient_id and n.voided=0 order by n.preferred desc,n.person_name_id limit 1), "
		        + "(select i.identifier from patient_identifier i where i.patient_id=o.patient_id and i.voided=0 order by i.preferred desc,i.patient_identifier_id limit 1) "
		        + "from orders o join encounter e on e.encounter_id=o.encounter_id and e.voided=0 "
		        + "join patient p on p.patient_id=o.patient_id and p.voided=0 "
		        + "where o.voided=0 and o.order_type_id=:orderTypeId and e.location_id in (:locationIds) "
		        + "and o.accession_number is not null and trim(o.accession_number)<>'' "
		        + "and not exists (select 1 from obs result where result.order_id=o.order_id and result.voided=0) ";
		if (patientId != null)
			sql += "and o.patient_id=:patientId ";
		if (fromDate != null)
			sql += "and o.date_created>=:fromDate ";
		if (toDate != null)
			sql += "and o.date_created<:toDate ";
		if (search != null && !search.trim().isEmpty())
			sql += "and lower(o.accession_number) like :search ";
		sql += "group by o.patient_id,trim(o.accession_number) order by min(coalesce(o.date_activated,o.date_created)) asc";
		SQLQuery q = getSession().createSQLQuery(sql);
		q.setInteger("orderTypeId", orderTypeId);
		q.setParameterList("locationIds", locationIds);
		if (patientId != null)
			q.setInteger("patientId", patientId);
		if (fromDate != null)
			q.setTimestamp("fromDate", fromDate);
		if (toDate != null)
			q.setTimestamp("toDate", toDate);
		if (search != null && !search.trim().isEmpty())
			q.setString("search", "%" + search.trim().toLowerCase() + "%");
		q.setMaxResults(Math.max(1, Math.min(limit, 5000)));
		List<Object[]> rows = q.list();
		List<PendingLaboratoryPatient> result = new ArrayList<PendingLaboratoryPatient>();
		for (Object[] row : rows)
			result.add(new PendingLaboratoryPatient(((Number) row[0]).intValue(), String.valueOf(row[1]), String
			        .valueOf(row[2]), (Date) row[3], row[4] == null ? "" : String.valueOf(row[4]), row[5] == null ? ""
			        : String.valueOf(row[5])));
		return result;
	}
	
	public LaboratoryHandover saveLaboratoryHandover(LaboratoryHandover handover) {
		getSession().saveOrUpdate(handover);
		return handover;
	}
	
	public LaboratoryHandover getLaboratoryHandoverByUuid(String uuid) {
		return (LaboratoryHandover) getSession().createCriteria(LaboratoryHandover.class).add(Restrictions.eq("uuid", uuid))
		        .add(Restrictions.eq("voided", false)).uniqueResult();
	}
	
	@SuppressWarnings("unchecked")
	public List<LaboratoryHandover> getLaboratoryHandovers(Location location, int limit) {
		return getSession().createCriteria(LaboratoryHandover.class).add(Restrictions.eq("location", location))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.desc("dateSubmitted")).setMaxResults(limit).list();
	}
}
