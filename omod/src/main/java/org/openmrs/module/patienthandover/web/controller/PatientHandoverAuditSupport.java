package org.openmrs.module.patienthandover.web.controller;

import java.util.Date;
import java.util.UUID;

import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.PatientHandoverAuditEvent;

final class PatientHandoverAuditSupport {
	
	private PatientHandoverAuditSupport() {
	}
	
	static void record(PatienthandoverService service, String type, String batchKey, String handoverUuid, String taskUuid,
	        boolean successful, String details) {
		try {
			PatientHandoverAuditEvent event = new PatientHandoverAuditEvent();
			event.setUuid(UUID.randomUUID().toString());
			event.setEventType(type);
			event.setBatchKey(batchKey);
			event.setHandoverUuid(handoverUuid);
			event.setTaskUuid(taskUuid);
			event.setUser(Context.getAuthenticatedUser());
			event.setLocation(Context.getUserContext() == null ? null : Context.getUserContext().getLocation());
			event.setEventDate(new Date());
			event.setSuccessful(successful);
			event.setDetails(details == null ? null : details.substring(0, Math.min(1000, details.length())));
			service.saveAuditEvent(event);
		}
		catch (RuntimeException ignored) {
			// Audit failure must not block urgent clinical handover work.
		}
	}
}
