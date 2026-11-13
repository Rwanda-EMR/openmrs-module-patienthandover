package org.openmrs.module.patienthandover.page.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class LocationHandoverDraftLiteralTest {
	
	@Test
	public void jsStringLiteral_shouldNotAllowClinicalTextToCloseTheScriptElement() {
		String draft = "{\"fields\":[{\"value\":\"</script><script>alert(1)</script>\\nline2 'q'\"}]}";
		String literal = LocationHandoverPageController.jsStringLiteral(draft);
		assertFalse(literal.contains("<"));
		assertFalse(literal.contains(">"));
		assertFalse(literal.contains("'"));
		assertFalse(literal.contains("\n"));
		assertEquals('"', literal.charAt(0));
	}
	
	@Test
	public void jsStringLiteral_shouldRenderMissingDraftAsNull() {
		assertEquals("null", LocationHandoverPageController.jsStringLiteral(null));
	}
	
	@Test
	public void toJsonArray_shouldListTitlesSafely() {
		assertEquals("[\"Pulse\",\"Current complaints or symptoms\"]",
		    LocationHandoverPageController.toJsonArray(java.util.Arrays.asList("Pulse", "Current complaints or symptoms")));
		assertEquals("[\"a\\\"b\"]", LocationHandoverPageController.toJsonArray(java.util.Arrays.asList("a\"b")));
		assertEquals("[]", LocationHandoverPageController.toJsonArray(java.util.Collections.<String> emptyList()));
	}
}
