package org.openmrs.module.patienthandover.domain;

import java.util.Date;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Person;
import org.openmrs.User;

public class HandoverTeamMember extends BaseOpenmrsData {
	
	private Integer id;
	
	private String handoverType;
	
	private String transactionKey;
	
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
	
	public String getHandoverType() {
		return handoverType;
	}
	
	public void setHandoverType(String value) {
		handoverType = value;
	}
	
	public String getTransactionKey() {
		return transactionKey;
	}
	
	public void setTransactionKey(String value) {
		transactionKey = value;
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
