package org.openmrs.module.patienthandover.domain;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

//import javax.persistence.Basic;
//import javax.persistence.Column;
//import javax.persistence.Entity;
//import javax.persistence.GeneratedValue;
//import javax.persistence.Id;
//import javax.persistence.JoinColumn;
//import javax.persistence.ManyToOne;
//mport javax.persistence.Table;

//@Entity(name = "patienthandover.PatientHandover")
//@Table(name = "patient_handover")
public class PatientHandover extends BaseOpenmrsData {
	
	//@Id
	//@GeneratedValue
	//@Column(name = "patient_handover_id")
	private Integer id;
	
	//@ManyToOne
	//@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;
	
	//@Basic
	//@Column(name = "priority", length = 20, nullable = false)
	private String priority;
	
	private String shift;
	
	private String careSetting;
	
	private Location location;
	
	private Provider receivingProvider;
	
	private String batchUuid;
	
	private boolean acknowledged;
	
	private User acknowledgedBy;
	
	private Date dateAcknowledged;
	
	private boolean cancelled;
	
	private User cancelledBy;
	
	private Date dateCancelled;
	
	private String cancellationReason;
	
	//@Basic
	//@Column(name = "situation", length = 1000)
	private String situation;
	
	//@Basic
	//@Column(name = "background", length = 1000)
	private String background;
	
	//@Basic
	//@Column(name = "assessment", length = 1000)
	private String assessment;
	
	//@Basic
	//@Column(name = "recommendation", length = 1000)
	private String recommendation;
	
	private String pendingTasks;
	
	private Date taskDueDate;
	
	private boolean tasksCompleted;
	
	private User tasksCompletedBy;
	
	private Date dateTasksCompleted;
	
	private Set<PatientHandoverTask> tasks = new LinkedHashSet<PatientHandoverTask>();
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public Patient getPatient() {
		return patient;
	}
	
	public void setPatient(Patient patient) {
		this.patient = patient;
	}
	
	public String getPriority() {
		return priority;
	}
	
	public void setPriority(String priority) {
		this.priority = priority;
	}
	
	public String getShift() {
		return shift;
	}
	
	public void setShift(String shift) {
		this.shift = shift;
	}
	
	public String getCareSetting() {
		return careSetting;
	}
	
	public void setCareSetting(String careSetting) {
		this.careSetting = careSetting;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location location) {
		this.location = location;
	}
	
	public Provider getReceivingProvider() {
		return receivingProvider;
	}
	
	public void setReceivingProvider(Provider receivingProvider) {
		this.receivingProvider = receivingProvider;
	}
	
	public String getBatchUuid() {
		return batchUuid;
	}
	
	public void setBatchUuid(String batchUuid) {
		this.batchUuid = batchUuid;
	}
	
	public String getTransactionReference() {
		String source = batchUuid == null || batchUuid.trim().isEmpty() ? getUuid() : batchUuid;
		if (source == null) {
			return "HX-PENDING";
		}
		source = source.replace("-", "").toUpperCase();
		return "HX-" + source.substring(0, Math.min(8, source.length()));
	}
	
	public boolean isAcknowledged() {
		return acknowledged;
	}
	
	public void setAcknowledged(boolean acknowledged) {
		this.acknowledged = acknowledged;
	}
	
	public User getAcknowledgedBy() {
		return acknowledgedBy;
	}
	
	public void setAcknowledgedBy(User acknowledgedBy) {
		this.acknowledgedBy = acknowledgedBy;
	}
	
	public Date getDateAcknowledged() {
		return dateAcknowledged;
	}
	
	public void setDateAcknowledged(Date dateAcknowledged) {
		this.dateAcknowledged = dateAcknowledged;
	}
	
	public boolean isCancelled() {
		return cancelled;
	}
	
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}
	
	public User getCancelledBy() {
		return cancelledBy;
	}
	
	public void setCancelledBy(User cancelledBy) {
		this.cancelledBy = cancelledBy;
	}
	
	public Date getDateCancelled() {
		return dateCancelled;
	}
	
	public void setDateCancelled(Date dateCancelled) {
		this.dateCancelled = dateCancelled;
	}
	
	public String getCancellationReason() {
		return cancellationReason;
	}
	
	public void setCancellationReason(String cancellationReason) {
		this.cancellationReason = cancellationReason;
	}
	
	public String getStatus() {
		return cancelled ? "CANCELLED" : (acknowledged ? "RECEIVED" : "PENDING");
	}
	
	public String getSituation() {
		return situation;
	}
	
	public void setSituation(String situation) {
		this.situation = situation;
	}
	
	public String getBackground() {
		return background;
	}
	
	public void setBackground(String background) {
		this.background = background;
	}
	
	public String getAssessment() {
		return assessment;
	}
	
	public void setAssessment(String assessment) {
		this.assessment = assessment;
	}
	
	public String getRecommendation() {
		return recommendation;
	}
	
	public void setRecommendation(String recommendation) {
		this.recommendation = recommendation;
	}
	
	public String getPendingTasks() {
		return pendingTasks;
	}
	
	public void setPendingTasks(String pendingTasks) {
		this.pendingTasks = pendingTasks;
	}
	
	public Date getTaskDueDate() {
		return taskDueDate;
	}
	
	public void setTaskDueDate(Date taskDueDate) {
		this.taskDueDate = taskDueDate;
	}
	
	public Set<PatientHandoverTask> getTasks() {
		return tasks;
	}
	
	public void setTasks(Set<PatientHandoverTask> tasks) {
		this.tasks = tasks;
	}
	
	public void addTask(PatientHandoverTask task) {
		task.setHandover(this);
		tasks.add(task);
	}
	
	public boolean isTasksCompleted() {
		return tasksCompleted;
	}
	
	public void setTasksCompleted(boolean tasksCompleted) {
		this.tasksCompleted = tasksCompleted;
	}
	
	public User getTasksCompletedBy() {
		return tasksCompletedBy;
	}
	
	public void setTasksCompletedBy(User tasksCompletedBy) {
		this.tasksCompletedBy = tasksCompletedBy;
	}
	
	public Date getDateTasksCompleted() {
		return dateTasksCompleted;
	}
	
	public void setDateTasksCompleted(Date dateTasksCompleted) {
		this.dateTasksCompleted = dateTasksCompleted;
	}
}
