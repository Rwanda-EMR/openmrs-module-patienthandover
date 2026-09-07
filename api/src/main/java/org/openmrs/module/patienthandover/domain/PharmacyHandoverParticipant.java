package org.openmrs.module.patienthandover.domain;

import java.util.Date;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Person;
import org.openmrs.User;

public class PharmacyHandoverParticipant extends BaseOpenmrsData {
	
	private Integer id;
	
	private PharmacyHandover handover;
	
	private Person person;
	
	private String role;
	
	private boolean signed;
	
	private User signedBy;
	
	private Date dateSigned;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer value) {
		id = value;
	}
	
	public PharmacyHandover getHandover() {
		return handover;
	}
	
	public void setHandover(PharmacyHandover value) {
		handover = value;
	}
	
	public Person getPerson() {
		return person;
	}
	
	public void setPerson(Person value) {
		person = value;
	}
	
	public String getRole() {
		return role;
	}
	
	public void setRole(String value) {
		role = value;
	}
	
	public boolean isSigned() {
		return signed;
	}
	
	public void setSigned(boolean value) {
		signed = value;
	}
	
	public User getSignedBy() {
		return signedBy;
	}
	
	public void setSignedBy(User value) {
		signedBy = value;
	}
	
	public Date getDateSigned() {
		return dateSigned;
	}
	
	public void setDateSigned(Date value) {
		dateSigned = value;
	}
}
