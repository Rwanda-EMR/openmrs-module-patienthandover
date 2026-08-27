package org.openmrs.module.patienthandover.domain;

import java.util.Date;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Provider;
import org.openmrs.User;

public class PatientHandoverTask extends BaseOpenmrsData {
	
	private Integer id;
	
	private PatientHandover handover;
	
	private String description;
	
	private Date dueDate;
	
	private Provider assignee;
	
	private boolean completed;
	
	private User completedBy;
	
	private Date dateCompleted;
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public PatientHandover getHandover() {
		return handover;
	}
	
	public void setHandover(PatientHandover handover) {
		this.handover = handover;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public Date getDueDate() {
		return dueDate;
	}
	
	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}
	
	public Provider getAssignee() {
		return assignee;
	}
	
	public void setAssignee(Provider assignee) {
		this.assignee = assignee;
	}
	
	public boolean isCompleted() {
		return completed;
	}
	
	public void setCompleted(boolean completed) {
		this.completed = completed;
	}
	
	public User getCompletedBy() {
		return completedBy;
	}
	
	public void setCompletedBy(User completedBy) {
		this.completedBy = completedBy;
	}
	
	public Date getDateCompleted() {
		return dateCompleted;
	}
	
	public void setDateCompleted(Date dateCompleted) {
		this.dateCompleted = dateCompleted;
	}
}
