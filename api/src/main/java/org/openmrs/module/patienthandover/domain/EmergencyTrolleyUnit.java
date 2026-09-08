package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;

/** A named physical emergency trolley within an OpenMRS location. */
public class EmergencyTrolleyUnit extends BaseOpenmrsData {
	
	private Integer id;
	
	private Location location;
	
	private String trolleyName;
	
	private String trolleyCode;
	
	private String description;
	
	private boolean active = true;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer v) {
		id = v;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location v) {
		location = v;
	}
	
	public String getTrolleyName() {
		return trolleyName;
	}
	
	public void setTrolleyName(String v) {
		trolleyName = v;
	}
	
	public String getTrolleyCode() {
		return trolleyCode;
	}
	
	public void setTrolleyCode(String v) {
		trolleyCode = v;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String v) {
		description = v;
	}
	
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean v) {
		active = v;
	}
	
	@Override
	public String toString() {
		return trolleyName + (trolleyCode == null || trolleyCode.isEmpty() ? "" : " (" + trolleyCode + ")");
	}
}
