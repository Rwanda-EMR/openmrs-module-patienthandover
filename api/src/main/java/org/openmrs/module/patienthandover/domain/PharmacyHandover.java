package org.openmrs.module.patienthandover.domain;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.User;

public class PharmacyHandover extends BaseOpenmrsData {
	
	private Integer id, pharmacyLocationId;
	
	private String pharmacyName, shift;
	
	private Location location;
	
	private Provider receivingProvider;
	
	private Date dateSubmitted, dateAcknowledged, dateCancelled;
	
	private boolean acknowledged;
	
	private User acknowledgedBy, cancelledBy;
	
	private String cancellationReason;
	
	private Set<PharmacyHandoverItem> items = new LinkedHashSet<PharmacyHandoverItem>();
	
	private Set<PharmacyHandoverParticipant> participants = new LinkedHashSet<PharmacyHandoverParticipant>();
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer v) {
		id = v;
	}
	
	public Integer getPharmacyLocationId() {
		return pharmacyLocationId;
	}
	
	public void setPharmacyLocationId(Integer v) {
		pharmacyLocationId = v;
	}
	
	public String getPharmacyName() {
		return pharmacyName;
	}
	
	public void setPharmacyName(String v) {
		pharmacyName = v;
	}
	
	public String getShift() {
		return shift;
	}
	
	public void setShift(String v) {
		shift = v;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location v) {
		location = v;
	}
	
	public Provider getReceivingProvider() {
		return receivingProvider;
	}
	
	public void setReceivingProvider(Provider v) {
		receivingProvider = v;
	}
	
	public Date getDateSubmitted() {
		return dateSubmitted;
	}
	
	public void setDateSubmitted(Date v) {
		dateSubmitted = v;
	}
	
	public boolean isAcknowledged() {
		return acknowledged;
	}
	
	public void setAcknowledged(boolean v) {
		acknowledged = v;
	}
	
	public User getAcknowledgedBy() {
		return acknowledgedBy;
	}
	
	public void setAcknowledgedBy(User v) {
		acknowledgedBy = v;
	}
	
	public Date getDateAcknowledged() {
		return dateAcknowledged;
	}
	
	public void setDateAcknowledged(Date v) {
		dateAcknowledged = v;
	}
	
	public Date getDateCancelled() {
		return dateCancelled;
	}
	
	public void setDateCancelled(Date value) {
		dateCancelled = value;
	}
	
	public User getCancelledBy() {
		return cancelledBy;
	}
	
	public void setCancelledBy(User value) {
		cancelledBy = value;
	}
	
	public String getCancellationReason() {
		return cancellationReason;
	}
	
	public void setCancellationReason(String value) {
		cancellationReason = value;
	}
	
	public boolean isCancelled() {
		return dateCancelled != null;
	}
	
	public Set<PharmacyHandoverItem> getItems() {
		return items;
	}
	
	public void setItems(Set<PharmacyHandoverItem> v) {
		items = v;
	}
	
	public void addItem(PharmacyHandoverItem v) {
		v.setHandover(this);
		items.add(v);
	}
	
	public Set<PharmacyHandoverParticipant> getParticipants() {
		return participants;
	}
	
	public void setParticipants(Set<PharmacyHandoverParticipant> value) {
		participants = value;
	}
	
	public void addParticipant(PharmacyHandoverParticipant value) {
		value.setHandover(this);
		participants.add(value);
	}
	
	public boolean allSigned(String role) {
		for (PharmacyHandoverParticipant p : participants)
			if (role.equals(p.getRole()) && !p.isSigned())
				return false;
		return true;
	}
	
	public String getStatus() {
		return isCancelled() ? "CANCELLED" : (acknowledged ? "RECEIVED" : "PENDING");
	}
}
