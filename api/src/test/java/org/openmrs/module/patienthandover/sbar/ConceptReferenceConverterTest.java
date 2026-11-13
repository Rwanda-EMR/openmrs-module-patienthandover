package org.openmrs.module.patienthandover.sbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ConceptReferenceConverterTest {
	
	private static final Map<Integer, String> UUIDS = new HashMap<Integer, String>();
	static {
		UUIDS.put(13651, "aaaa1111-0000-0000-0000-000000013651");
		UUIDS.put(158541, "bbbb2222-0000-0000-0000-000000158541");
		UUIDS.put(12933, "cccc3333-0000-0000-0000-000000012933");
	}
	
	private static final ConceptReferenceConverter.Lookup LOOKUP = new ConceptReferenceConverter.Lookup() {
		
		@Override
		public String uuidOf(int conceptId) {
			return UUIDS.get(conceptId);
		}
	};
	
	@Test
	public void toUuids_shouldReplaceIdsAndKeepAlternativesMarkersAndUuids() {
		String converted = ConceptReferenceConverter.toUuids(
		    "13651|3cda0160-26fe-102b-80cb-0017a47871b2|158541, 12933 ,ABNORMAL_LABS,CIEL:1284", LOOKUP);
		assertEquals("aaaa1111-0000-0000-0000-000000013651|3cda0160-26fe-102b-80cb-0017a47871b2|"
		        + "bbbb2222-0000-0000-0000-000000158541,cccc3333-0000-0000-0000-000000012933,ABNORMAL_LABS,CIEL:1284",
		    converted);
		assertFalse(ConceptReferenceConverter.hasNumericIds(converted));
	}
	
	@Test
	public void toUuids_shouldKeepIdsThatDoNotExistOnThisServer() {
		String converted = ConceptReferenceConverter.toUuids("99999,12933", LOOKUP);
		assertEquals("99999,cccc3333-0000-0000-0000-000000012933", converted);
		assertTrue(ConceptReferenceConverter.hasNumericIds(converted));
	}
	
	@Test
	public void hasNumericIds_shouldIgnoreDigitsInsideUuids() {
		assertFalse(ConceptReferenceConverter.hasNumericIds("3ce93824-26fe-102b-80cb-0017a47871b2|DIAGNOSES"));
		assertTrue(ConceptReferenceConverter.hasNumericIds("3ce93824-26fe-102b-80cb-0017a47871b2|3516"));
	}
	
	@Test
	public void toUuids_shouldKeepAConceptOnlyOnceWhenItsIdAndUuidWereBothListed() {
		assertEquals("cccc3333-0000-0000-0000-000000012933,x",
		    ConceptReferenceConverter.toUuids("12933|cccc3333-0000-0000-0000-000000012933,x", LOOKUP));
	}
}
