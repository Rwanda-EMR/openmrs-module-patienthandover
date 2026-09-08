package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;

public class EquipmentItem extends BaseOpenmrsData {
	
	private Integer id;
	
	private Location location;
	
	private String equipmentName;
	
	private Double quantity;
	
	private String equipmentStatus;
	
	private String observation;
	
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
	
	public String getEquipmentName() {
		return equipmentName;
	}
	
	public void setEquipmentName(String v) {
		equipmentName = v;
	}
	
	public Double getQuantity() {
		return quantity;
	}
	
	public void setQuantity(Double v) {
		quantity = v;
	}
	
	public String getEquipmentStatus() {
		return equipmentStatus;
	}
	
	public void setEquipmentStatus(String v) {
		equipmentStatus = v;
	}
	
	public String getObservation() {
		return observation;
	}
	
	public void setObservation(String v) {
		observation = v;
	}
	
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean v) {
		active = v;
	}
}
