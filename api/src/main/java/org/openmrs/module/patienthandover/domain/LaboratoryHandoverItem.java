package org.openmrs.module.patienthandover.domain;

import java.util.Date;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;

public class LaboratoryHandoverItem extends BaseOpenmrsData {
	
	private Integer id;
	
	private LaboratoryHandover handover;
	
	private Patient patient;
	
	private String patientName;
	
	private String patientIdentifier;
	
	private String labCode;
	
	private String orderIds;
	
	private Date orderedOn;
	
	private String comment;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer v) {
		id = v;
	}
	
	public LaboratoryHandover getHandover() {
		return handover;
	}
	
	public void setHandover(LaboratoryHandover v) {
		handover = v;
	}
	
	public Patient getPatient() {
		return patient;
	}
	
	public void setPatient(Patient v) {
		patient = v;
	}
	
	public String getPatientName() {
		return patientName;
	}
	
	public void setPatientName(String v) {
		patientName = v;
	}
	
	public String getPatientIdentifier() {
		return patientIdentifier;
	}
	
	public void setPatientIdentifier(String v) {
		patientIdentifier = v;
	}
	
	public String getLabCode() {
		return labCode;
	}
	
	public void setLabCode(String v) {
		labCode = v;
	}
	
	public String getOrderIds() {
		return orderIds;
	}
	
	public void setOrderIds(String v) {
		orderIds = v;
	}
	
	public Date getOrderedOn() {
		return orderedOn;
	}
	
	public void setOrderedOn(Date v) {
		orderedOn = v;
	}
	
	public String getComment() {
		return comment;
	}
	
	public void setComment(String v) {
		comment = v;
	}
}
