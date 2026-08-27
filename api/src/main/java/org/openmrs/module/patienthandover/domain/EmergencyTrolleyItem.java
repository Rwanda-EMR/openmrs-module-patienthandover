package org.openmrs.module.patienthandover.domain;

import java.util.Date;
import org.openmrs.BaseOpenmrsData;

/** A remaining emergency-trolley item transferred with one handover batch. */
public class EmergencyTrolleyItem extends BaseOpenmrsData {
	
	private Integer id;
	
	private String batchUuid;
	
	private String itemName;
	
	private Double remainingQuantity;
	
	private String unit;
	
	private Date expiryDate;
	
	private String remarks;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getBatchUuid() {
		return batchUuid;
	}
	
	public void setBatchUuid(String batchUuid) {
		this.batchUuid = batchUuid;
	}
	
	public String getItemName() {
		return itemName;
	}
	
	public void setItemName(String itemName) {
		this.itemName = itemName;
	}
	
	public Double getRemainingQuantity() {
		return remainingQuantity;
	}
	
	public void setRemainingQuantity(Double remainingQuantity) {
		this.remainingQuantity = remainingQuantity;
	}
	
	public String getUnit() {
		return unit;
	}
	
	public void setUnit(String unit) {
		this.unit = unit;
	}
	
	public Date getExpiryDate() {
		return expiryDate;
	}
	
	public void setExpiryDate(Date expiryDate) {
		this.expiryDate = expiryDate;
	}
	
	public String getRemarks() {
		return remarks;
	}
	
	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}
}
