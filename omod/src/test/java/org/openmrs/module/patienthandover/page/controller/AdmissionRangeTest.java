package org.openmrs.module.patienthandover.page.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;

import org.junit.Test;

public class AdmissionRangeTest {
	
	private final SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	
	@Test
	public void parse_shouldIncludeTheWholeLastDay() {
		LocationHandoverPageController.AdmissionRange range = LocationHandoverPageController.AdmissionRange.parse(
		    "2026-11-01", "2026-11-10");
		assertEquals("2026-11-01 00:00", day.format(range.from));
		assertEquals("2026-11-11 00:00", day.format(range.toExclusive));
		assertEquals("2026-11-10", range.toText);
	}
	
	@Test
	public void parse_shouldSwapReversedDates() {
		LocationHandoverPageController.AdmissionRange range = LocationHandoverPageController.AdmissionRange.parse(
		    "2026-11-10", "2026-11-01");
		assertEquals("2026-11-01", range.fromText);
		assertEquals("2026-11-10", range.toText);
	}
	
	@Test
	public void parse_shouldDefaultToTheLastTwoDaysForMissingOrInvalidDates() {
		LocationHandoverPageController.AdmissionRange range = LocationHandoverPageController.AdmissionRange.parse(null,
		    "not-a-date");
		long days = (range.toExclusive.getTime() - range.from.getTime()) / (24L * 3600 * 1000);
		assertTrue("expected 3 calendar days (2 back + today) but was " + days, days >= 2 && days <= 3);
	}
}
