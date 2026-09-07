package org.openmrs.module.patienthandover.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.openmrs.BaseOpenmrsData;

public class PharmacyHandoverItem extends BaseOpenmrsData {
	
	private Integer id, inventoryBalanceId;
	
	private PharmacyHandover handover;
	
	private String itemName, itemCode, batchNumber;
	
	private Date expiryDate;
	
	private BigDecimal availableQuantity;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer v) {
		id = v;
	}
	
	public PharmacyHandover getHandover() {
		return handover;
	}
	
	public void setHandover(PharmacyHandover v) {
		handover = v;
	}
	
	public Integer getInventoryBalanceId() {
		return inventoryBalanceId;
	}
	
	public void setInventoryBalanceId(Integer v) {
		inventoryBalanceId = v;
	}
	
	public String getItemName() {
		return itemName;
	}
	
	public void setItemName(String v) {
		itemName = v;
	}
	
	public String getItemCode() {
		return itemCode;
	}
	
	public void setItemCode(String v) {
		itemCode = v;
	}
	
	public String getBatchNumber() {
		return batchNumber;
	}
	
	public void setBatchNumber(String v) {
		batchNumber = v;
	}
	
	public Date getExpiryDate() {
		return expiryDate;
	}
	
	public void setExpiryDate(Date v) {
		expiryDate = v;
	}
	
	public BigDecimal getAvailableQuantity() {
		return availableQuantity;
	}
	
	public void setAvailableQuantity(BigDecimal v) {
		availableQuantity = v;
	}
}
