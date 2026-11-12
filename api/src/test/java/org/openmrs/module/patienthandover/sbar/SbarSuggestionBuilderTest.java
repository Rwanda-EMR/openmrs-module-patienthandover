package org.openmrs.module.patienthandover.sbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.module.patienthandover.sbar.SbarSuggestionBuilder.Observation;
import org.openmrs.module.patienthandover.sbar.SbarSuggestionBuilder.PreviousText;
import org.openmrs.module.patienthandover.sbar.SbarSuggestionBuilder.Suggestion;

public class SbarSuggestionBuilderTest {
	
	private final SbarSuggestionBuilder builder = new SbarSuggestionBuilder();
	
	private static Date at(int day, int hour) {
		Calendar c = Calendar.getInstance();
		c.set(2026, Calendar.NOVEMBER, day, hour, 0, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTime();
	}
	
	private static Map<Integer, Map<String, PreviousText>> noPrevious() {
		return Collections.emptyMap();
	}
	
	@Test
	public void build_shouldUseOnlyTheLatestValueOfEachConcept() {
		List<Observation> obs = Arrays.asList(new Observation(1, "assessment", 0, "Temperature", "38.5", at(9, 8)),
		    new Observation(1, "assessment", 0, "Temperature", "37.1", at(10, 8)));
		
		String text = builder.build(obs, noPrevious()).get(1).get("assessment").getText();
		
		assertTrue(text.startsWith("Temperature: 37.1 ("));
		assertFalse(text.contains("38.5"));
	}
	
	@Test
	public void build_shouldJoinAnswersRecordedTogetherAndOrderLinesByConfiguration() {
		List<Observation> obs = Arrays.asList(new Observation(1, "situation", 2, "Chief complaints", "Cough", at(10, 9)),
		    new Observation(1, "situation", 0, "Final diagnosis", "Malaria", at(10, 9)), new Observation(1, "situation", 0,
		            "Final diagnosis", "Anaemia", at(10, 9)));
		
		String text = builder.build(obs, noPrevious()).get(1).get("situation").getText();
		String[] lines = text.split("\n");
		
		assertEquals(2, lines.length);
		assertTrue(lines[0].startsWith("Final diagnosis: Malaria, Anaemia"));
		assertTrue(lines[1].startsWith("Chief complaints: Cough"));
	}
	
	@Test
	public void build_shouldKeepFieldTextWithinColumnLimit() {
		List<Observation> obs = new ArrayList<Observation>();
		StringBuilder longValue = new StringBuilder();
		for (int i = 0; i < 400; i++)
			longValue.append("x");
		for (int i = 0; i < 10; i++)
			obs.add(new Observation(1, "background", i, "Note " + i, longValue.toString(), at(10, 9)));
		
		Suggestion s = builder.build(obs, noPrevious()).get(1).get("background");
		
		assertTrue(s.getText().length() <= SbarSuggestionBuilder.MAX_FIELD_LENGTH);
		assertTrue(s.getText().contains("…")); // each value truncated
	}
	
	@Test
	public void build_shouldFallBackToPreviousHandoverOnlyForFieldsWithoutObservations() {
		List<Observation> obs = Collections.singletonList(new Observation(1, "situation", 0, "Final diagnosis", "Malaria",
		        at(10, 9)));
		Map<String, PreviousText> fields = new HashMap<String, PreviousText>();
		fields.put("situation", new PreviousText("old situation", at(9, 18)));
		fields.put("recommendation", new PreviousText("Continue IV fluids", at(9, 18)));
		Map<Integer, Map<String, PreviousText>> previous = new HashMap<Integer, Map<String, PreviousText>>();
		previous.put(1, fields);
		
		Map<String, Suggestion> result = builder.build(obs, previous).get(1);
		
		assertTrue(result.get("situation").getText().startsWith("Final diagnosis"));
		assertEquals("Continue IV fluids", result.get("recommendation").getText());
		assertTrue(result.get("recommendation").getSource().startsWith("Previous handover"));
		assertNull(result.get("background"));
	}
	
	@Test
	public void build_shouldIgnoreBlankValuesAndOmitPatientsWithoutSuggestions() {
		List<Observation> obs = Arrays.asList(new Observation(1, "assessment", 0, "Objective", "   ", at(10, 9)),
		    new Observation(2, "assessment", 0, "Objective", "Stable", at(10, 9)));
		
		Map<Integer, Map<String, Suggestion>> result = builder.build(obs, noPrevious());
		
		assertFalse(result.containsKey(1));
		assertEquals("Recent records: Objective", result.get(2).get("assessment").getSource());
	}
	
	@Test
	public void build_shouldSkipPlaceholderValuesTypedOnlyToPassARequiredField() {
		List<Observation> obs = Arrays.asList(new Observation(1, "recommendation", 0, "Progress plan", ".", at(10, 9)),
		    new Observation(1, "recommendation", 1, "Health education", " - ", at(10, 9)), new Observation(1,
		            "recommendation", 2, "Treatment", "Continue ceftriaxone", at(10, 9)));
		
		String text = builder.build(obs, noPrevious()).get(1).get("recommendation").getText();
		
		assertTrue(text.startsWith("Treatment: Continue ceftriaxone"));
		assertFalse(text.contains("Progress plan"));
		assertFalse(text.contains("Health education"));
	}
	
	@Test
	public void readableLabel_shouldTurnCodedNamesIntoSentenceCase() {
		assertEquals("Progress plan", SbarSuggestionBuilder.readableLabel("progress_plan"));
		assertEquals("Known allergies", SbarSuggestionBuilder.readableLabel("KNOWN ALLERGIES"));
		assertEquals("Identified Health Education needs",
		    SbarSuggestionBuilder.readableLabel("Identified Health Education needs"));
		assertEquals("HIV status", SbarSuggestionBuilder.readableLabel("HIV status"));
	}
}
