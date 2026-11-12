package org.openmrs.module.patienthandover.page.controller;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LaboratoryHandoverKeyTest {
	
	@Test
	public void safe_shouldProduceDistinctFieldNamesForKeysThatDifferOnlyInPunctuation() {
		String dash = LaboratoryHandoverPageController.safe("12|AB-1");
		String underscore = LaboratoryHandoverPageController.safe("12|AB_1");
		assertNotEquals(dash, underscore);
		assertTrue(dash.matches("[A-Za-z0-9]+"));
		assertTrue(LaboratoryHandoverPageController.safe("7|Ünïcode").matches("[A-Za-z0-9]+"));
	}
}
