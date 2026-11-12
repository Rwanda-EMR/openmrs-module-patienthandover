package org.openmrs.module.patienthandover.domain;

import java.util.Date;

/** Read-only laboratory worklist row backed by coded orders that have no result observation. */
public class PendingLaboratoryPatient {
	
	private Integer patientId;
	
	private String labCode;
	
	private String orderIds;
	
	private Date orderedOn;
	
	private String patientName;
	
	private String patientIdentifier;
	
	public PendingLaboratoryPatient(Integer patientId, String labCode, String orderIds, Date orderedOn, String patientName,
	    String patientIdentifier) {
		this.patientId = patientId;
		this.labCode = labCode;
		this.orderIds = orderIds;
		this.orderedOn = orderedOn;
		this.patientName = patientName;
		this.patientIdentifier = patientIdentifier;
	}
	
	public Integer getPatientId() {
		return patientId;
	}
	
	public String getLabCode() {
		return labCode;
	}
	
	public String getOrderIds() {
		return orderIds;
	}
	
	public Date getOrderedOn() {
		return orderedOn;
	}
	
	public String getPatientName() {
		return patientName;
	}
	
	public String getPatientIdentifier() {
		return patientIdentifier;
	}
}
