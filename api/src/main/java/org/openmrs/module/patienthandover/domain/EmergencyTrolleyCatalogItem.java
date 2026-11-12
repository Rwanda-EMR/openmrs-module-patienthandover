package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;

public class EmergencyTrolleyCatalogItem extends BaseOpenmrsData {
	
	private Integer id;
	
	private Location location;
	
	private EmergencyTrolleyUnit trolley;
	
	private Double minimumQuantity = 0.0;
	
	private Double maximumQuantity;
	
	private java.util.Date expirationDate;
	
	private String itemName;
	
	private Double currentQuantity;
	
	private String standardNote;
	
	private boolean active = true;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location v) {
		location = v;
	}
	
	public EmergencyTrolleyUnit getTrolley() {
		return trolley;
	}
	
	public void setTrolley(EmergencyTrolleyUnit v) {
		trolley = v;
	}
	
	public Double getMinimumQuantity() {
		return minimumQuantity;
	}
	
	public void setMinimumQuantity(Double v) {
		minimumQuantity = v;
	}
	
	public boolean isBelowMinimum() {
		return currentQuantity != null && minimumQuantity != null && currentQuantity < minimumQuantity;
	}
	
	public Double getMaximumQuantity() {
		return maximumQuantity;
	}
	
	public void setMaximumQuantity(Double v) {
		maximumQuantity = v;
	}
	
	public java.util.Date getExpirationDate() {
		return expirationDate;
	}
	
	public void setExpirationDate(java.util.Date v) {
		expirationDate = v;
	}
	
	public boolean isAboveMaximum() {
		return currentQuantity != null && maximumQuantity != null && currentQuantity > maximumQuantity;
	}
	
	public boolean isExpired() {
		return expirationDate != null && expirationDate.before(new java.util.Date());
	}
	
	public String getItemName() {
		return itemName;
	}
	
	public void setItemName(String v) {
		itemName = v;
	}
	
	public Double getCurrentQuantity() {
		return currentQuantity;
	}
	
	public void setCurrentQuantity(Double v) {
		currentQuantity = v;
	}
	
	public String getStandardNote() {
		return standardNote;
	}
	
	public void setStandardNote(String v) {
		standardNote = v;
	}
	
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean v) {
		active = v;
	}
}
