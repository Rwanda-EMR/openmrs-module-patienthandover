package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;

public class EmergencyTrolleyHandoverItem extends BaseOpenmrsData {
	
	private Integer id;
	
	private EmergencyTrolleyHandover handover;
	
	private EmergencyTrolleyCatalogItem catalogItem;
	
	private String itemNameSnapshot;
	
	private Double previousQuantity;
	
	private Double handedQuantity;
	
	private Double minimumQuantitySnapshot;
	
	private Double maximumQuantitySnapshot;
	
	private java.util.Date expirationDateSnapshot;
	
	private String note;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public EmergencyTrolleyHandover getHandover() {
		return handover;
	}
	
	public void setHandover(EmergencyTrolleyHandover v) {
		handover = v;
	}
	
	public EmergencyTrolleyCatalogItem getCatalogItem() {
		return catalogItem;
	}
	
	public void setCatalogItem(EmergencyTrolleyCatalogItem v) {
		catalogItem = v;
	}
	
	public String getItemNameSnapshot() {
		return itemNameSnapshot;
	}
	
	public void setItemNameSnapshot(String v) {
		itemNameSnapshot = v;
	}
	
	public Double getPreviousQuantity() {
		return previousQuantity;
	}
	
	public void setPreviousQuantity(Double v) {
		previousQuantity = v;
	}
	
	public Double getHandedQuantity() {
		return handedQuantity;
	}
	
	public void setHandedQuantity(Double v) {
		handedQuantity = v;
	}
	
	public Double getMinimumQuantitySnapshot() {
		return minimumQuantitySnapshot;
	}
	
	public void setMinimumQuantitySnapshot(Double v) {
		minimumQuantitySnapshot = v;
	}
	
	public Double getMaximumQuantitySnapshot() {
		return maximumQuantitySnapshot;
	}
	
	public void setMaximumQuantitySnapshot(Double v) {
		maximumQuantitySnapshot = v;
	}
	
	public java.util.Date getExpirationDateSnapshot() {
		return expirationDateSnapshot;
	}
	
	public void setExpirationDateSnapshot(java.util.Date v) {
		expirationDateSnapshot = v;
	}
	
	public String getNote() {
		return note;
	}
	
	public void setNote(String v) {
		note = v;
	}
}
