package org.openmrs.module.patienthandover.web.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.module.patienthandover.HandoverTeamSupport;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.domain.HandoverTeamMember;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.PatientHandoverTask;
import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("module/patienthandover/batchHandover.form")
public class BatchHandoverController {
	
	private static final String VIEW = "/module/patienthandover/batchHandover";
	
	@Autowired
	private PatienthandoverService patienthandoverService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String showBatch(@RequestParam("batchKey") String batchKey, Model model, HttpSession session) {
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW)) {
			PatientHandoverAuditSupport.record(patienthandoverService, "UNAUTHORIZED_ATTEMPT", batchKey, null, null, false,
			    "Attempted to view a handover transaction without View privilege.");
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		List<PatientHandover> handovers = loadBatch(batchKey);
		if (handovers.isEmpty()) {
			session.setAttribute("locationHandoverError", "The requested handover transaction was not found.");
			return "redirect:/patienthandover/handoverDashboard.page";
		}
		
		PatientHandover first = handovers.get(0);
		if (!canViewBatch(first, Context.getAuthenticatedUser())) {
			PatientHandoverAuditSupport.record(patienthandoverService, "UNAUTHORIZED_ATTEMPT", batchKey, first.getUuid(),
			    null, false, "Attempted to view a transaction outside the permitted scope.");
			session.setAttribute("dashboardError", "You are not allowed to view that handover transaction.");
			return "redirect:/patienthandover/handoverDashboard.page";
		}
		model.addAttribute("batchKey", batchKey);
		model.addAttribute("handovers", handovers);
		model.addAttribute("firstHandover", first);
		Map<String, Boolean> overdueTasks = new LinkedHashMap<String, Boolean>();
		Date now = new Date();
		for (PatientHandover handover : handovers) {
			for (PatientHandoverTask task : handover.getTasks()) {
				overdueTasks.put(task.getUuid(), !task.isCompleted() && task.getDueDate() != null
				        && task.getDueDate().before(now));
			}
		}
		model.addAttribute("overdueTasks", overdueTasks);
		model.addAttribute("canAcknowledge", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE)
		        && canAcknowledge(first, Context.getAuthenticatedUser()));
		model.addAttribute("canCompleteTasks", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_COMPLETE_TASKS));
		User currentUser = Context.getAuthenticatedUser();
		model.addAttribute("currentUserPersonId", currentUser == null || currentUser.getPerson() == null ? null
		        : currentUser.getPerson().getPersonId());
		PatientHandoverAuditSupport.record(patienthandoverService, "VIEWED", batchKey, first.getUuid(), null, true,
		    "Viewed handover transaction details.");
		return VIEW;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "acknowledgeBatch")
	public String acknowledgeBatch(@RequestParam("batchKey") String batchKey, HttpSession session) {
		List<PatientHandover> handovers = loadBatch(batchKey);
		if (handovers.isEmpty()) {
			session.setAttribute("locationHandoverError", "The requested handover transaction was not found.");
			return "redirect:/patienthandover/handoverDashboard.page";
		}
		PatientHandover first = handovers.get(0);
		String key = batchKey.startsWith("single:") ? first.getUuid() : batchKey;
		List<HandoverTeamMember> team = patienthandoverService.getHandoverTeam("PATIENT", key);
		User user = Context.getAuthenticatedUser();
		if (!team.isEmpty()) {
			HandoverTeamMember member = HandoverTeamSupport.assignment(team, user);
			boolean sender = member != null && HandoverTeamSupport.OUTGOING.equals(member.getRole());
			if (member == null || !HandoverTeamSupport.maySign(member, team)
			        || (sender && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CREATE))
			        || (!sender && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE))) {
				session.setAttribute("batchHandoverError",
				    member != null ? "Receiving team members can sign after every outgoing team member has signed."
				            : "You are not an unsigned member of this handover team.");
				return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
			}
			HandoverTeamSupport.sign(member, patienthandoverService);
			PatientHandoverAuditSupport.record(patienthandoverService, sender ? "SENDER_SIGNED" : "RECEIVER_SIGNED",
			    batchKey, first.getUuid(), null, true, "Team member signed the patient handover.");
			if (HandoverTeamSupport.allSigned(team, HandoverTeamSupport.OUTGOING)
			        && HandoverTeamSupport.allSigned(team, HandoverTeamSupport.RECEIVING)) {
				Date received = new Date();
				for (PatientHandover handover : handovers) {
					handover.setAcknowledged(true);
					handover.setAcknowledgedBy(user);
					handover.setDateAcknowledged(received);
				}
				patienthandoverService.savePatientHandovers(handovers);
				session.setAttribute("batchHandoverMessage",
				    "Your signature was recorded. All team members have signed; the handover is received.");
			} else {
				session.setAttribute("batchHandoverMessage", "Your handover signature was recorded.");
			}
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE);
		for (PatientHandover handover : handovers)
			if (handover.isCancelled() || !canAcknowledge(handover, user)) {
				session.setAttribute("batchHandoverError",
				    "Only the designated receiving provider may mark this handover as received.");
				return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
			}
		Date received = new Date();
		for (PatientHandover handover : handovers) {
			handover.setAcknowledged(true);
			handover.setAcknowledgedBy(user);
			handover.setDateAcknowledged(received);
		}
		patienthandoverService.savePatientHandovers(handovers);
		session.setAttribute("batchHandoverMessage", handovers.size() + " patient handover(s) marked as received.");
		return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "reassignHandover")
	public String reassignHandover(@RequestParam("batchKey") String batchKey,
	        @RequestParam("receivingProviderId") Integer providerId, @RequestParam("editReason") String editReason,
	        HttpSession session) {
		List<PatientHandover> handovers = loadBatch(batchKey);
		User user = Context.getAuthenticatedUser();
		PatientHandover first = handovers.isEmpty() ? null : handovers.get(0);
		boolean creatorCanEdit = first != null && first.getCreator() != null && user != null
		        && first.getCreator().getUserId().equals(user.getUserId());
		if (first == null || first.isAcknowledged() || first.isCancelled() || !sameLocation(first)
		        || (!creatorCanEdit && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE))) {
			session.setAttribute("batchHandoverError",
			    "Only authorized outgoing staff or an administrator may reassign a pending handover.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		Provider provider = Context.getProviderService().getProvider(providerId);
		String reason = editReason == null ? "" : editReason.trim();
		if (provider == null || provider.isRetired() || reason.isEmpty()) {
			session.setAttribute("batchHandoverError",
			    "Select an active receiving provider and enter a reassignment reason.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		String previous = first.getReceivingProvider() == null ? "Unassigned" : first.getReceivingProvider().getName();
		Date now = new Date();
		for (PatientHandover handover : handovers) {
			handover.setReceivingProvider(provider);
			handover.setChangedBy(user);
			handover.setDateChanged(now);
			for (PatientHandoverTask task : handover.getTasks()) {
				if (!task.isCompleted()) {
					task.setAssignee(provider);
					task.setChangedBy(user);
					task.setDateChanged(now);
				}
			}
		}
		patienthandoverService.savePatientHandovers(handovers);
		PatientHandoverAuditSupport.record(patienthandoverService, "REASSIGNED", batchKey, first.getUuid(), null, true,
		    "Receiving provider changed from " + previous + " to " + provider.getName() + ". Reason: " + reason);
		session.setAttribute("batchHandoverMessage", "Handover reassigned to " + provider.getName() + ".");
		return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "editTeam")
	public String editTeam(@RequestParam("batchKey") String batchKey,
	        @RequestParam(value = "outgoingProviderIds", required = false) String[] outgoingIds,
	        @RequestParam(value = "receivingProviderIds", required = false) String[] receivingIds,
	        @RequestParam("editReason") String editReason, HttpSession session) {
		List<PatientHandover> handovers = loadBatch(batchKey);
		User user = Context.getAuthenticatedUser();
		PatientHandover first = handovers.isEmpty() ? null : handovers.get(0);
		if (!canManagePending(first, user)) {
			session.setAttribute("batchHandoverError", "Only the handover creator or a manager may edit a pending team.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		String reason = editReason == null ? "" : editReason.trim();
		if (reason.isEmpty()) {
			session.setAttribute("batchHandoverError", "Enter a reason for changing the team.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		String key = batchKey.startsWith("single:") ? first.getUuid() : batchKey;
		List<HandoverTeamMember> team = patienthandoverService.getHandoverTeam("PATIENT", key);
		List<Provider> outgoing = selectedProviders(outgoingIds);
		List<Provider> receiving = selectedProviders(receivingIds);
		if (!hasSignedRole(team, HandoverTeamSupport.OUTGOING) && outgoing.isEmpty()) {
			session.setAttribute("batchHandoverError", "The sending team must contain at least one member.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		if (!hasSignedRole(team, HandoverTeamSupport.RECEIVING) && receiving.isEmpty()) {
			session.setAttribute("batchHandoverError", "The receiving team must contain at least one member.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		Date now = new Date();
		for (HandoverTeamMember member : team)
			if (!member.isSigned() && !Boolean.TRUE.equals(member.getVoided())) {
				member.setVoided(true);
				member.setVoidedBy(user);
				member.setDateVoided(now);
				member.setVoidReason("Team changed: " + reason);
				patienthandoverService.saveHandoverTeamMember(member);
			}
		for (Provider provider : outgoing)
			addTeamMember(team, key, provider, HandoverTeamSupport.OUTGOING);
		for (Provider provider : receiving)
			addTeamMember(team, key, provider, HandoverTeamSupport.RECEIVING);
		if (!receiving.isEmpty())
			for (PatientHandover handover : handovers) {
				handover.setReceivingProvider(receiving.get(0));
				handover.setChangedBy(user);
				handover.setDateChanged(now);
			}
		List<HandoverTeamMember> updated = patienthandoverService.getHandoverTeam("PATIENT", key);
		if (HandoverTeamSupport.allSigned(updated, HandoverTeamSupport.OUTGOING)
		        && HandoverTeamSupport.allSigned(updated, HandoverTeamSupport.RECEIVING)) {
			for (PatientHandover handover : handovers) {
				handover.setAcknowledged(true);
				handover.setAcknowledgedBy(user);
				handover.setDateAcknowledged(now);
			}
		}
		patienthandoverService.savePatientHandovers(handovers);
		PatientHandoverAuditSupport.record(patienthandoverService, "TEAM_AMENDED", batchKey, first.getUuid(), null, true,
		    "Patient handover team changed. Reason: " + reason);
		session.setAttribute("batchHandoverMessage",
		    "Team members updated. Signed members and signature times were retained.");
		return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "editHandover")
	public String editHandover(@RequestParam("batchKey") String batchKey, @RequestParam("handoverUuid") String handoverUuid,
	        @RequestParam("priority") String priority, @RequestParam("situation") String situation,
	        @RequestParam("background") String background, @RequestParam("assessment") String assessment,
	        @RequestParam("recommendation") String recommendation, @RequestParam("editReason") String editReason,
	        HttpSession session) {
		PatientHandover handover = patienthandoverService.getPatientHandoverByUuid(handoverUuid);
		User user = Context.getAuthenticatedUser();
		boolean creatorCanEdit = handover != null && handover.getCreator() != null && user != null
		        && handover.getCreator().getUserId().equals(user.getUserId());
		if (handover == null || !belongsToTransaction(handover, batchKey) || handover.isAcknowledged()
		        || handover.isCancelled() || !sameLocation(handover)
		        || (!creatorCanEdit && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE))) {
			PatientHandoverAuditSupport.record(patienthandoverService, "UNAUTHORIZED_ATTEMPT", batchKey, handoverUuid, null,
			    false, "Attempted to edit an ineligible patient handover.");
			session.setAttribute("batchHandoverError",
			    "Only authorized outgoing staff or an administrator may edit a pending handover.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		String reason = editReason == null ? "" : editReason.trim();
		if (reason.isEmpty() || situation.trim().isEmpty() || background.trim().isEmpty() || assessment.trim().isEmpty()
		        || recommendation.trim().isEmpty()
		        || !("STABLE".equals(priority) || "URGENT".equals(priority) || "CRITICAL".equals(priority))) {
			session.setAttribute("batchHandoverError", "Complete all edit fields and enter a valid edit reason.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		String previous = "Previous priority=" + handover.getPriority() + "; situation=" + handover.getSituation();
		handover.setPriority(priority);
		handover.setSituation(situation.trim());
		handover.setBackground(background.trim());
		handover.setAssessment(assessment.trim());
		handover.setRecommendation(recommendation.trim());
		handover.setChangedBy(user);
		handover.setDateChanged(new Date());
		patienthandoverService.savePatientHandover(handover);
		PatientHandoverAuditSupport.record(patienthandoverService, "AMENDED", batchKey, handoverUuid, null, true,
		    "Patient handover edited. Reason: " + reason + ". " + previous);
		session.setAttribute("batchHandoverMessage", "Patient handover information updated. The amendment was audited.");
		return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
	}
	
	private boolean belongsToTransaction(PatientHandover handover, String batchKey) {
		return batchKey != null
		        && (batchKey.equals(handover.getBatchUuid()) || (batchKey.startsWith("single:") && batchKey.substring(7)
		                .equals(handover.getUuid())));
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "cancelHandover")
	public String cancelHandover(@RequestParam("batchKey") String batchKey,
	        @RequestParam("cancellationReason") String reason, HttpSession session) {
		List<PatientHandover> handovers = loadBatch(batchKey);
		if (handovers.isEmpty()) {
			session.setAttribute("dashboardError", "The requested handover transaction was not found.");
			return "redirect:/patienthandover/handoverDashboard.page";
		}
		User user = Context.getAuthenticatedUser();
		PatientHandover first = handovers.get(0);
		boolean creatorCanCancel = first.getCreator() != null && user != null
		        && first.getCreator().getUserId().equals(user.getUserId());
		if ((!creatorCanCancel && !Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE)) || !sameLocation(first)
		        || first.isAcknowledged() || first.isCancelled()) {
			PatientHandoverAuditSupport.record(patienthandoverService, "UNAUTHORIZED_ATTEMPT", batchKey, first.getUuid(),
			    null, false, "Attempted to cancel an ineligible handover transaction.");
			session.setAttribute("batchHandoverError",
			    "Only an authorized outgoing staff member or administrator may cancel a pending handover.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		String cancellationReason = reason == null ? "" : reason.trim();
		if (cancellationReason.isEmpty()) {
			session.setAttribute("batchHandoverError", "Enter a reason before cancelling this handover.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		Date now = new Date();
		for (PatientHandover handover : handovers) {
			handover.setCancelled(true);
			handover.setCancelledBy(user);
			handover.setDateCancelled(now);
			handover.setCancellationReason(cancellationReason);
			handover.setChangedBy(user);
			handover.setDateChanged(now);
		}
		patienthandoverService.savePatientHandovers(handovers);
		PatientHandoverAuditSupport.record(patienthandoverService, "CANCELLED", batchKey, first.getUuid(), null, true,
		    "Handover cancelled. Reason: " + cancellationReason);
		session.setAttribute("batchHandoverMessage", "Handover cancelled. It remains available in reports and history.");
		return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "completeTask")
	public String completeTask(@RequestParam("batchKey") String batchKey, @RequestParam("taskUuid") String taskUuid,
	        HttpSession session) {
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_COMPLETE_TASKS)) {
			PatientHandoverAuditSupport.record(patienthandoverService, "UNAUTHORIZED_ATTEMPT", batchKey, null, null, false,
			    "Attempted task completion without Complete Tasks privilege.");
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_COMPLETE_TASKS);
		List<PatientHandover> handovers = loadBatch(batchKey);
		PatientHandover owningHandover = null;
		PatientHandoverTask selectedTask = null;
		for (PatientHandover handover : handovers) {
			for (PatientHandoverTask task : handover.getTasks()) {
				if (task.getUuid().equals(taskUuid)) {
					owningHandover = handover;
					selectedTask = task;
					break;
				}
			}
		}
		User authenticatedUser = Context.getAuthenticatedUser();
		if (selectedTask == null || owningHandover == null || owningHandover.isCancelled()
		        || !owningHandover.isAcknowledged() || !canCompleteTask(owningHandover, selectedTask, authenticatedUser)) {
			PatientHandoverAuditSupport.record(patienthandoverService, "UNAUTHORIZED_ATTEMPT", batchKey,
			    owningHandover == null ? null : owningHandover.getUuid(), taskUuid, false,
			    "Attempted to complete an unreceived, missing, or differently assigned task.");
			session.setAttribute("batchHandoverError",
			    "Only the assigned provider may complete this task after the handover is received.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		if (!selectedTask.isCompleted()) {
			selectedTask.setCompleted(true);
			selectedTask.setCompletedBy(authenticatedUser);
			selectedTask.setDateCompleted(new Date());
			selectedTask.setChangedBy(authenticatedUser);
			selectedTask.setDateChanged(new Date());
			patienthandoverService.savePatientHandover(owningHandover);
			PatientHandoverAuditSupport.record(patienthandoverService, "TASK_COMPLETED", batchKey, owningHandover.getUuid(),
			    selectedTask.getUuid(), true, "Individual transferred task completed.");
		}
		session.setAttribute("batchHandoverMessage", "Task marked complete for "
		        + owningHandover.getPatient().getPersonName().getFullName() + ".");
		return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
	}
	
	@RequestMapping(method = RequestMethod.POST, params = "completeTasks")
	public String completeTasks(@RequestParam("batchKey") String batchKey,
	        @RequestParam("handoverUuid") String handoverUuid, HttpSession session) {
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_COMPLETE_TASKS);
		List<PatientHandover> handovers = loadBatch(batchKey);
		PatientHandover selected = null;
		for (PatientHandover handover : handovers) {
			if (handover.getUuid().equals(handoverUuid)) {
				selected = handover;
				break;
			}
		}
		if (selected == null) {
			session.setAttribute("batchHandoverError", "The selected patient task was not found in this transaction.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		User authenticatedUser = Context.getAuthenticatedUser();
		if (!selected.isAcknowledged() || !canAcknowledge(selected, authenticatedUser)) {
			session.setAttribute("batchHandoverError",
			    "Only the designated receiving provider may complete tasks after receiving the handover.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		if (selected.getPendingTasks() == null || selected.getPendingTasks().trim().isEmpty()) {
			session.setAttribute("batchHandoverError", "This patient has no transferred tasks to complete.");
			return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
		}
		if (!selected.isTasksCompleted()) {
			selected.setTasksCompleted(true);
			selected.setTasksCompletedBy(authenticatedUser);
			selected.setDateTasksCompleted(new Date());
			patienthandoverService.savePatientHandover(selected);
		}
		session.setAttribute("batchHandoverMessage", "Tasks completed for "
		        + selected.getPatient().getPersonName().getFullName() + ".");
		return "redirect:/patienthandover/batchHandover.page?batchKey=" + batchKey;
	}
	
	private boolean canManagePending(PatientHandover handover, User user) {
		return handover != null
		        && !handover.isAcknowledged()
		        && !handover.isCancelled()
		        && sameLocation(handover)
		        && (Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE) || (user != null
		                && handover.getCreator() != null && user.getUserId().equals(handover.getCreator().getUserId())));
	}
	
	private List<Provider> selectedProviders(String[] ids) {
		List<Provider> result = new ArrayList<Provider>();
		if (ids == null)
			return result;
		for (String id : ids) {
			Provider provider = null;
			try {
				provider = Context.getProviderService().getProvider(Integer.valueOf(id));
			}
			catch (Exception ignored) {}
			if (provider != null && !Boolean.TRUE.equals(provider.getRetired()) && provider.getPerson() != null) {
				boolean duplicate = false;
				for (Provider existing : result)
					if (existing.getPerson().getPersonId().equals(provider.getPerson().getPersonId()))
						duplicate = true;
				if (!duplicate)
					result.add(provider);
			}
		}
		return result;
	}
	
	private boolean hasSignedRole(List<HandoverTeamMember> team, String role) {
		for (HandoverTeamMember member : team)
			if (!Boolean.TRUE.equals(member.getVoided()) && member.isSigned() && role.equals(member.getRole()))
				return true;
		return false;
	}
	
	private void addTeamMember(List<HandoverTeamMember> existing, String key, Provider provider, String role) {
		for (HandoverTeamMember member : existing)
			if (!Boolean.TRUE.equals(member.getVoided()) && role.equals(member.getRole()) && member.getPerson() != null
			        && member.getPerson().getPersonId().equals(provider.getPerson().getPersonId()))
				return;
		HandoverTeamMember member = new HandoverTeamMember();
		member.setUuid(java.util.UUID.randomUUID().toString());
		member.setHandoverType("PATIENT");
		member.setTransactionKey(key);
		member.setPerson(provider.getPerson());
		member.setRole(role);
		member.setSigned(false);
		member.setCreator(Context.getAuthenticatedUser());
		member.setDateCreated(new Date());
		patienthandoverService.saveHandoverTeamMember(member);
	}
	
	private List<PatientHandover> loadBatch(String batchKey) {
		if (batchKey == null || batchKey.trim().isEmpty()) {
			return new ArrayList<PatientHandover>();
		}
		if (batchKey.startsWith("single:")) {
			PatientHandover handover = patienthandoverService.getPatientHandoverByUuid(batchKey.substring(7));
			List<PatientHandover> result = new ArrayList<PatientHandover>();
			if (handover != null && !handover.getVoided()) {
				result.add(handover);
			}
			return result;
		}
		return patienthandoverService.getHandoversByBatchUuid(batchKey);
	}
	
	private boolean canAcknowledge(PatientHandover handover, User user) {
		Provider receivingProvider = handover.getReceivingProvider();
		return handover.getLocation() != null && Context.getUserContext().getLocation() != null
		        && handover.getLocation().getLocationId().equals(Context.getUserContext().getLocation().getLocationId())
		        && receivingProvider != null && receivingProvider.getPerson() != null && user != null
		        && user.getPerson() != null
		        && receivingProvider.getPerson().getPersonId().equals(user.getPerson().getPersonId());
	}
	
	private boolean canCompleteTask(PatientHandover handover, PatientHandoverTask task, User user) {
		Provider assignee = task.getAssignee();
		return handover.getLocation() != null && Context.getUserContext().getLocation() != null
		        && handover.getLocation().getLocationId().equals(Context.getUserContext().getLocation().getLocationId())
		        && assignee != null && assignee.getPerson() != null && user != null && user.getPerson() != null
		        && assignee.getPerson().getPersonId().equals(user.getPerson().getPersonId());
	}
	
	private boolean sameLocation(PatientHandover handover) {
		return handover.getLocation() != null && Context.getUserContext().getLocation() != null
		        && handover.getLocation().getLocationId().equals(Context.getUserContext().getLocation().getLocationId());
	}
	
	private boolean canViewBatch(PatientHandover handover, User user) {
		if (handover.getLocation() == null || Context.getUserContext().getLocation() == null
		        || !handover.getLocation().getLocationId().equals(Context.getUserContext().getLocation().getLocationId())) {
			return false;
		}
		if (Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_ALL_PROVIDERS)) {
			return true;
		}
		boolean createdByUser = user != null && handover.getCreator() != null
		        && user.getUserId().equals(handover.getCreator().getUserId());
		return createdByUser || canAcknowledge(handover, user);
	}
}
