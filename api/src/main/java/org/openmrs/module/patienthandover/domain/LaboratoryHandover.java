package org.openmrs.module.patienthandover.domain;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;
import org.openmrs.User;

public class LaboratoryHandover extends BaseOpenmrsData {
	
	private Integer id;
	
	private Location location;
	
	private String shift;
	
	private String status;
	
	private Date dateSubmitted;
	
	private Date dateReceived;
	
	private User receivedBy;
	
	private Set<LaboratoryHandoverItem> items = new LinkedHashSet<LaboratoryHandoverItem>();
	
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
	
	public String getShift() {
		return shift;
	}
	
	public void setShift(String v) {
		shift = v;
	}
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String v) {
		status = v;
	}
	
	public Date getDateSubmitted() {
		return dateSubmitted;
	}
	
	public void setDateSubmitted(Date v) {
		dateSubmitted = v;
	}
	
	public Date getDateReceived() {
		return dateReceived;
	}
	
	public void setDateReceived(Date v) {
		dateReceived = v;
	}
	
	public User getReceivedBy() {
		return receivedBy;
	}
	
	public void setReceivedBy(User v) {
		receivedBy = v;
	}
	
	public Set<LaboratoryHandoverItem> getItems() {
		return items;
	}
	
	public void setItems(Set<LaboratoryHandoverItem> v) {
		items = v;
	}
	
	public void addItem(LaboratoryHandoverItem v) {
		v.setHandover(this);
		items.add(v);
	}
	
	public String getTransactionReference() {
		return "LAB-" + (getUuid() == null ? "PENDING" : getUuid().replace("-", "").substring(0, 8).toUpperCase());
	}
}
