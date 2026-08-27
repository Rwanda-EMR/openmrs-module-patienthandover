package org.openmrs.module.patienthandover.api.dao;

import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.domain.PatientHandover;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Ignore("DAO integration test postponed while module uses legacy OpenMRS 2.0.0 test dependencies")
public class PatienthandoverDaoTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private PatienthandoverDao dao;
	
	@Test
	public void savePatientHandover_shouldSaveHandoverInDatabase() {
		
		Patient patient = new Patient(2);
		
		PatientHandover handover = new PatientHandover();
		handover.setPatient(patient);
		handover.setPriority("URGENT");
		handover.setSituation("Patient requires close monitoring");
		handover.setBackground("Known clinical history");
		handover.setAssessment("Patient currently stable");
		handover.setRecommendation("Continue monitoring");
		
		dao.savePatientHandover(handover);
		
		Context.flushSession();
		Context.clearSession();
		
		PatientHandover saved = dao.getPatientHandoverByUuid(handover.getUuid());
		
		assertNotNull(saved);
		assertEquals("URGENT", saved.getPriority());
		assertEquals("Patient requires close monitoring", saved.getSituation());
		assertEquals(Integer.valueOf(2), saved.getPatient().getPatientId());
	}
}
