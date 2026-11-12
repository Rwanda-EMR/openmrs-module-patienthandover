package org.openmrs.module.patienthandover.domain;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.Location;
import org.openmrs.User;

/**
 * An unsubmitted Make Handover form, stored per user, login location and care setting so that the
 * user can continue from another browser or after the browser's storage is cleared. The form state
 * is kept as the JSON document produced by the page.
 */
public class PatientHandoverDraft extends BaseOpenmrsObject {
	
	private Integer id;
	
	private User user;
	
	private Location location;
	
	private String careSetting;
	
	private String draftData;
	
	private Date dateCreated;
	
	private Date dateChanged;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
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
	
	public String getCareSetting() {
		return careSetting;
	}
	
	public void setCareSetting(String careSetting) {
		this.careSetting = careSetting;
	}
	
	public String getDraftData() {
		return draftData;
	}
	
	public void setDraftData(String draftData) {
		this.draftData = draftData;
	}
	
	public Date getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	public Date getDateChanged() {
		return dateChanged;
	}
	
	public void setDateChanged(Date dateChanged) {
		this.dateChanged = dateChanged;
	}
}
