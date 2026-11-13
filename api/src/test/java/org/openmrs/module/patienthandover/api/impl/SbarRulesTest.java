package org.openmrs.module.patienthandover.api.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

public class SbarRulesTest {
	
	private static final Date ADMISSION = new Date(1_000_000L);
	
	@Test
	public void createdAfter_shouldCountValuesSavedTogetherWithTheAdmission() {
		Date cutoff = PatienthandoverServiceImpl.cutoff(ADMISSION, null);
		assertFalse("before admission", PatienthandoverServiceImpl.createdAfter(new Date(998_000L), cutoff));
		assertTrue("saved with the admission form", PatienthandoverServiceImpl.createdAfter(ADMISSION, cutoff));
		assertTrue("database rounding", PatienthandoverServiceImpl.createdAfter(new Date(999_500L), cutoff));
		assertTrue(PatienthandoverServiceImpl.createdAfter(new Date(1_000_001L), cutoff));
		assertFalse("unknown creation time", PatienthandoverServiceImpl.createdAfter(null, cutoff));
		assertTrue("query bound includes the tolerance",
		    PatienthandoverServiceImpl.queryBound(ADMISSION).getTime() < 999_000L);
	}
	
	@Test
	public void cutoff_shouldRejectEverythingWhenThePatientHasNoActiveVisit() {
		assertNull(PatienthandoverServiceImpl.cutoff(null, null));
		assertNull(PatienthandoverServiceImpl.cutoff(null, new Date(10L)));
		assertFalse(PatienthandoverServiceImpl.createdAfter(new Date(), null));
	}
	
	@Test
	public void cutoff_shouldUseTheLaterOfAdmissionAndMaximumAge() {
		Date later = new Date(2_000_000L);
		assertSame(later, PatienthandoverServiceImpl.cutoff(ADMISSION, later));
		assertSame(ADMISSION, PatienthandoverServiceImpl.cutoff(ADMISSION, new Date(10L)));
	}
	
	@Test
	public void numericFlag_shouldClassifyAgainstReferenceRanges() {
		assertEquals("low", PatienthandoverServiceImpl.numericFlag(9.0, 5.0, 12.0, 16.0, 20.0));
		assertEquals("critical low", PatienthandoverServiceImpl.numericFlag(4.5, 5.0, 12.0, 16.0, 20.0));
		assertEquals("high", PatienthandoverServiceImpl.numericFlag(17.0, 5.0, 12.0, 16.0, 20.0));
		assertEquals("critical high", PatienthandoverServiceImpl.numericFlag(25.0, 5.0, 12.0, 16.0, 20.0));
		assertNull(PatienthandoverServiceImpl.numericFlag(14.0, 5.0, 12.0, 16.0, 20.0));
		assertNull("no range configured", PatienthandoverServiceImpl.numericFlag(14.0, null, null, null, null));
	}
	
	@Test
	public void isAbnormalAnswer_shouldRecogniseAbnormalCodedResultsOnly() {
		assertTrue(PatienthandoverServiceImpl.isAbnormalAnswer("POSITIVE"));
		assertTrue(PatienthandoverServiceImpl.isAbnormalAnswer("Reactive"));
		assertTrue(PatienthandoverServiceImpl.isAbnormalAnswer("Positive ++"));
		assertFalse(PatienthandoverServiceImpl.isAbnormalAnswer("NEGATIVE"));
		assertFalse(PatienthandoverServiceImpl.isAbnormalAnswer("Not detected"));
		assertFalse(PatienthandoverServiceImpl.isAbnormalAnswer("Non-reactive"));
	}
	
	@Test
	public void flagMarker_shouldShowDirectionAndSeverity() {
		assertEquals("[\u2193 LOW]", PatienthandoverServiceImpl.flagMarker("low"));
		assertEquals("[\u2191 HIGH]", PatienthandoverServiceImpl.flagMarker("high"));
		assertEquals("[\u2191\u2191 CRITICAL HIGH]", PatienthandoverServiceImpl.flagMarker("critical high"));
		assertEquals("[\u2193\u2193 CRITICAL LOW]", PatienthandoverServiceImpl.flagMarker("critical low"));
		assertEquals("[ABNORMAL]", PatienthandoverServiceImpl.flagMarker("abnormal"));
	}
}
