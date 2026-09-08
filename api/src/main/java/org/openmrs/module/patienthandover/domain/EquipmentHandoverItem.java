package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;

public class EquipmentHandoverItem extends BaseOpenmrsData {
	
	private Integer id;
	
	private EquipmentHandover handover;
	
	private EquipmentItem equipmentItem;
	
	private String equipmentName;
	
	private Double quantity;
	
	private String equipmentStatus;
	
	private String observation;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer v) {
		id = v;
	}
	
	public EquipmentHandover getHandover() {
		return handover;
	}
	
	public void setHandover(EquipmentHandover v) {
		handover = v;
	}
	
	public EquipmentItem getEquipmentItem() {
		return equipmentItem;
	}
	
	public void setEquipmentItem(EquipmentItem v) {
		equipmentItem = v;
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
}
