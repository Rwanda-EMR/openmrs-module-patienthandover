package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;

public class EmergencyTrolleyImport extends BaseOpenmrsData {
	
	private Integer id;
	
	private Location location;
	
	private String fileName;
	
	private Integer totalRows;
	
	private Integer itemsAdded;
	
	private Integer itemsSkipped;
	
	private Integer itemsFailed;
	
	private String status;
	
	private String errorSummary;
	
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
	
	public String getFileName() {
		return fileName;
	}
	
	public void setFileName(String v) {
		fileName = v;
	}
	
	public Integer getTotalRows() {
		return totalRows;
	}
	
	public void setTotalRows(Integer v) {
		totalRows = v;
	}
	
	public Integer getItemsAdded() {
		return itemsAdded;
	}
	
	public void setItemsAdded(Integer v) {
		itemsAdded = v;
	}
	
	public Integer getItemsSkipped() {
		return itemsSkipped;
	}
	
	public void setItemsSkipped(Integer v) {
		itemsSkipped = v;
	}
	
	public Integer getItemsFailed() {
		return itemsFailed;
	}
	
	public void setItemsFailed(Integer v) {
		itemsFailed = v;
	}
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String v) {
		status = v;
	}
	
	public String getErrorSummary() {
		return errorSummary;
	}
	
	public void setErrorSummary(String v) {
		errorSummary = v;
	}
}
