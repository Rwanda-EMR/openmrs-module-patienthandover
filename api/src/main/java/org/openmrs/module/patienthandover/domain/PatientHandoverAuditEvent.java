package org.openmrs.module.patienthandover.domain;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.Location;
import org.openmrs.User;

public class PatientHandoverAuditEvent extends BaseOpenmrsObject {
	
	private Integer id;
	
	private String eventType;
	
	private String batchKey;
	
	private String handoverUuid;
	
	private String taskUuid;
	
	private User user;
	
	private Location location;
	
	private Date eventDate;
	
	private boolean successful;
	
	private String details;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getEventType() {
		return eventType;
	}
	
	public void setEventType(String eventType) {
		this.eventType = eventType;
	}
	
	public String getBatchKey() {
		return batchKey;
	}
	
	public void setBatchKey(String batchKey) {
		this.batchKey = batchKey;
	}
	
	public String getHandoverUuid() {
		return handoverUuid;
	}
	
	public void setHandoverUuid(String handoverUuid) {
		this.handoverUuid = handoverUuid;
	}
	
	public String getTaskUuid() {
		return taskUuid;
	}
	
	public void setTaskUuid(String taskUuid) {
		this.taskUuid = taskUuid;
	}
	
	public User getUser() {
		return user;
	}
	
	public void setUser(User user) {
		this.user = user;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location location) {
		this.location = location;
	}
	
	public Date getEventDate() {
		return eventDate;
	}
	
	public void setEventDate(Date eventDate) {
		this.eventDate = eventDate;
	}
	
	public boolean isSuccessful() {
		return successful;
	}
	
	public void setSuccessful(boolean successful) {
		this.successful = successful;
	}
	
	public String getDetails() {
		return details;
	}
	
	public void setDetails(String details) {
		this.details = details;
	}
}
