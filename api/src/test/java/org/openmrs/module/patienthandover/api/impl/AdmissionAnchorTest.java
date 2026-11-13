package org.openmrs.module.patienthandover.api.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.Test;

public class AdmissionAnchorTest {
	
	private static final long HOUR = 3600L * 1000L;
	
	private static final long GAP = 72 * HOUR;
	
	private static final Date NOW = new Date(1000 * HOUR);
	
	private static Date hoursAgo(long hours) {
		return new Date(NOW.getTime() - hours * HOUR);
	}
	
	private static List<Date> encounters(long... hoursAgo) {
		Date[] dates = new Date[hoursAgo.length];
		for (int i = 0; i < hoursAgo.length; i++)
			dates[i] = hoursAgo(hoursAgo[i]);
		return Arrays.asList(dates);
	}
	
	@Test
	public void shouldStartAtTheReturnWhenThePatientLeftAndCameBackOnTheSameOpenVisit() {
		// Visit opened 40 days ago and never closed; care 40-38 days ago, then nothing until 20 hours ago.
		Date anchor = PatienthandoverServiceImpl.admissionAnchor(hoursAgo(960), null, encounters(2, 10, 20, 912, 940, 960),
		    GAP);
		assertEquals(hoursAgo(20), anchor);
	}
	
	@Test
	public void shouldUseTheAdmissionFormWhenItIsLaterThanTheStartOfCare() {
		// Triage 30 hours ago, admitted (admission form) 26 hours ago, ward care since.
		Date anchor = PatienthandoverServiceImpl.admissionAnchor(hoursAgo(30), hoursAgo(26), encounters(1, 12, 26, 30), GAP);
		assertEquals(hoursAgo(26), anchor);
	}
	
	@Test
	public void shouldIgnoreAnOldAdmissionFormFromAnEarlierStay() {
		// Admission form 30 days ago, patient left, came back 5 hours ago on the open visit.
		Date anchor = PatienthandoverServiceImpl.admissionAnchor(hoursAgo(720), hoursAgo(720), encounters(1, 5, 700, 720),
		    GAP);
		assertEquals(hoursAgo(5), anchor);
	}
	
	@Test
	public void shouldKeepTheAdmissionAcrossAWeekendWithoutNotes() {
		// 60 hours without encounters is shorter than the 72-hour gap: still the same stay.
		Date anchor = PatienthandoverServiceImpl.admissionAnchor(hoursAgo(200), hoursAgo(150), encounters(3, 63, 120, 150),
		    GAP);
		assertEquals(hoursAgo(150), anchor);
	}
	
	@Test
	public void shouldUseTheLastAdmissionHoweverLongAgoItWas() {
		// Last care 10 days ago on a visit that was never closed: today's date plays no role, the values
		// entered after that admission are still used.
		assertEquals(hoursAgo(400),
		    PatienthandoverServiceImpl.admissionAnchor(hoursAgo(400), hoursAgo(400), encounters(240, 300, 350, 400), GAP));
	}
	
	@Test
	public void shouldUseTheLastStretchOfCareMonthsAgo() {
		// Open visit from 4 months ago; two stretches of care, the latest 117 days ago.
		assertEquals(hoursAgo(2810),
		    PatienthandoverServiceImpl.admissionAnchor(hoursAgo(3500), null, encounters(2808, 2810, 3400, 3500), GAP));
	}
	
	@Test
	public void shouldUseANewlyOpenedVisitEvenBeforeItsFirstEncounter() {
		assertEquals(hoursAgo(1), PatienthandoverServiceImpl.admissionAnchor(hoursAgo(1), null, encounters(240, 400), GAP));
	}
	
	@Test
	public void shouldFallBackToTheVisitStartWithoutAnyEncounter() {
		assertEquals(hoursAgo(5),
		    PatienthandoverServiceImpl.admissionAnchor(hoursAgo(5), null, Collections.<Date> emptyList(), GAP));
		assertNull(PatienthandoverServiceImpl.admissionAnchor(null, null, null, GAP));
	}
}
