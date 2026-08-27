package org.openmrs.module.patienthandover.page.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.PatienthandoverConfig;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.module.patienthandover.domain.PatientHandoverTask;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

public class BatchHandoverPageController {
	
	public String controller(@RequestParam("batchKey") String batchKey,
	        @RequestParam(value = "page", defaultValue = "1") int page, HttpServletRequest request, PageModel model,
	        Session session, @SpringBean PatienthandoverService service) {
		if (!Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW)) {
			PatientHandoverAuditSupport.record(service, "UNAUTHORIZED_ATTEMPT", batchKey, null, null, false,
			    "Attempted to view a handover transaction without View privilege.");
		}
		Context.requirePrivilege(PatienthandoverConfig.PRIVILEGE_VIEW);
		List<PatientHandover> handovers = loadBatch(batchKey, service);
		if (handovers.isEmpty()) {
			session.setAttribute("dashboardError", "The requested handover transaction was not found.");
			return "redirect:patienthandover/handoverDashboard.page";
		}
		PatientHandover first = handovers.get(0);
		User user = Context.getAuthenticatedUser();
		if (!canViewBatch(first, user)) {
			PatientHandoverAuditSupport.record(service, "UNAUTHORIZED_ATTEMPT", batchKey, first.getUuid(), null, false,
			    "Attempted to view a transaction outside the permitted scope.");
			session.setAttribute("dashboardError", "You are not allowed to view that handover transaction.");
			return "redirect:patienthandover/handoverDashboard.page";
		}
		Map<String, Boolean> overdueTasks = new LinkedHashMap<String, Boolean>();
		Date now = new Date();
		for (PatientHandover handover : handovers) {
			for (PatientHandoverTask task : handover.getTasks()) {
				overdueTasks.put(task.getUuid(), !task.isCompleted() && task.getDueDate() != null
				        && task.getDueDate().before(now));
			}
		}
		model.addAttribute("batchKey", batchKey);
		model.addAttribute("openmrsContextPath", request.getContextPath());
		model.addAttribute("handovers", PaginationSupport.list(handovers, page, model, "patient"));
		model.addAttribute("firstHandover", first);
		model.addAttribute("providers", Context.getProviderService().getAllProviders(false));
		model.addAttribute("overdueTasks", overdueTasks);
		model.addAttribute("canAcknowledge", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_RECEIVE)
		        && canAcknowledge(first, user));
		model.addAttribute("canCompleteTasks", Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_COMPLETE_TASKS));
		model.addAttribute(
		    "canCancel",
		    !first.isAcknowledged()
		            && !first.isCancelled()
		            && sameLocation(first)
		            && ((Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_CANCEL) && first.getCreator() != null
		                    && user != null && first.getCreator().getUserId().equals(user.getUserId())) || Context
		                        .hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE)));
		model.addAttribute(
		    "canEdit",
		    !first.isAcknowledged()
		            && !first.isCancelled()
		            && sameLocation(first)
		            && ((Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_EDIT) && first.getCreator() != null
		                    && user != null && first.getCreator().getUserId().equals(user.getUserId())) || Context
		                        .hasPrivilege(PatienthandoverConfig.PRIVILEGE_MANAGE)));
		model.addAttribute("currentUserPersonId", user == null || user.getPerson() == null ? null : user.getPerson()
		        .getPersonId());
		PatientHandoverAuditSupport.record(service, "VIEWED", batchKey, first.getUuid(), null, true,
		    "Viewed handover transaction details.");
		return null;
	}
	
	private List<PatientHandover> loadBatch(String batchKey, PatienthandoverService service) {
		if (batchKey == null || batchKey.trim().isEmpty())
			return new ArrayList<PatientHandover>();
		if (batchKey.startsWith("single:")) {
			PatientHandover handover = service.getPatientHandoverByUuid(batchKey.substring(7));
			List<PatientHandover> result = new ArrayList<PatientHandover>();
			if (handover != null && !handover.getVoided())
				result.add(handover);
			return result;
		}
		return service.getHandoversByBatchUuid(batchKey);
	}
	
	private boolean canAcknowledge(PatientHandover handover, User user) {
		Provider provider = handover.getReceivingProvider();
		return sameLocation(handover) && provider != null && provider.getPerson() != null && user != null
		        && user.getPerson() != null && provider.getPerson().getPersonId().equals(user.getPerson().getPersonId());
	}
	
	private boolean canViewBatch(PatientHandover handover, User user) {
		if (!sameLocation(handover))
			return false;
		if (Context.hasPrivilege(PatienthandoverConfig.PRIVILEGE_VIEW_ALL_PROVIDERS))
			return true;
		boolean created = user != null && handover.getCreator() != null
		        && user.getUserId().equals(handover.getCreator().getUserId());
		return created || canAcknowledge(handover, user);
	}
	
	private boolean sameLocation(PatientHandover handover) {
		return handover.getLocation() != null && Context.getUserContext().getLocation() != null
		        && handover.getLocation().getLocationId().equals(Context.getUserContext().getLocation().getLocationId());
	}
}
