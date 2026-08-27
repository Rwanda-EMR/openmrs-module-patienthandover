package org.openmrs.module.patienthandover.domain;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.User;

public class EmergencyTrolleyHandover extends BaseOpenmrsData {
	
	private Integer id;
	
	private Location location;
	
	private String shift;
	
	private Provider receivingProvider;
	
	private String status;
	
	private Date dateSubmitted;
	
	private Date dateReceived;
	
	private User receivedBy;
	
	private Date dateCancelled;
	
	private User cancelledBy;
	
	private String cancellationReason;
	
	private Set<EmergencyTrolleyHandoverItem> items = new LinkedHashSet<EmergencyTrolleyHandoverItem>();
	
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
	
	public String getShift() {
		return shift;
	}
	
	public void setShift(String v) {
		shift = v;
	}
	
	public Provider getReceivingProvider() {
		return receivingProvider;
	}
	
	public void setReceivingProvider(Provider v) {
		receivingProvider = v;
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
	
	public Date getDateCancelled() {
		return dateCancelled;
	}
	
	public void setDateCancelled(Date v) {
		dateCancelled = v;
	}
	
	public User getCancelledBy() {
		return cancelledBy;
	}
	
	public void setCancelledBy(User v) {
		cancelledBy = v;
	}
	
	public String getCancellationReason() {
		return cancellationReason;
	}
	
	public void setCancellationReason(String v) {
		cancellationReason = v;
	}
	
	public Set<EmergencyTrolleyHandoverItem> getItems() {
		return items;
	}
	
	public void setItems(Set<EmergencyTrolleyHandoverItem> v) {
		items = v;
	}
	
	public void addItem(EmergencyTrolleyHandoverItem v) {
		v.setHandover(this);
		items.add(v);
	}
	
	public String getTransactionReference() {
		return "ET-" + (getUuid() == null ? "PENDING" : getUuid().replace("-", "").substring(0, 8).toUpperCase());
	}
}
