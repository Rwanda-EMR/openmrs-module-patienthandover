package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;

public class EmergencyTrolleyCatalogItem extends BaseOpenmrsData {
	
	private Integer id;
	
	private Location location;
	
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
