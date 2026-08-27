package org.openmrs.module.patienthandover.api;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Patient;
import org.openmrs.module.patienthandover.api.dao.PatienthandoverDao;
import org.openmrs.module.patienthandover.api.impl.PatienthandoverServiceImpl;
import org.openmrs.module.patienthandover.domain.PatientHandover;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PatienthandoverServiceTest {
	
	@InjectMocks
	private PatienthandoverServiceImpl patienthandoverService;
	
	@Mock
	private PatienthandoverDao dao;
	
	@Before
	public void setupMocks() {
		MockitoAnnotations.initMocks(this);
	}
	
	@Test
	public void savePatientHandover_shouldDelegateToDao() {
		
		Patient patient = new Patient();
		
		PatientHandover handover = new PatientHandover();
		handover.setPatient(patient);
		handover.setPriority("URGENT");
		handover.setSituation("Patient requires monitoring");
		
		when(dao.savePatientHandover(handover)).thenReturn(handover);
		
		PatientHandover saved = patienthandoverService.savePatientHandover(handover);
		
		assertSame(handover, saved);
		
		verify(dao).savePatientHandover(handover);
	}
}
