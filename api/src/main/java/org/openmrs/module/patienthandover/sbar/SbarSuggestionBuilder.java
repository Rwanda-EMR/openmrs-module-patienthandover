package org.openmrs.module.patienthandover.sbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a patient's recent observations into suggested text for the four SBAR fields. Pure logic
 * with no OpenMRS context access, so it can be unit tested; the service resolves concepts, loads
 * observations and renders values.
 * <p>
 * Rules: for every configured concept only the most recent recording is used (several answers
 * recorded at that same time, e.g. checkbox answers, are joined). Lines follow the configured
 * concept order. The text of a field is kept below {@link #MAX_FIELD_LENGTH} so that it always fits
 * the database column. When a field has no observations, the same field of the patient's previous
 * handover is offered instead.
 */
public class SbarSuggestionBuilder {
	
	public static final String SITUATION = "situation";
	
	public static final String BACKGROUND = "background";
	
	public static final String ASSESSMENT = "assessment";
	
	public static final String RECOMMENDATION = "recommendation";
	
	public static final List<String> FIELDS = Collections.unmodifiableList(java.util.Arrays.asList(SITUATION, BACKGROUND,
	    ASSESSMENT, RECOMMENDATION));
	
	/** SBAR columns are varchar(1000); stay well below so the user can still add text. */
	public static final int MAX_FIELD_LENGTH = 900;
	
	static final int MAX_VALUE_LENGTH = 300;
	
	/** One rendered observation, already mapped to an SBAR field. */
	public static class Observation {
		
		final Integer patientId;
		
		final String field;
		
		final int order;
		
		final String label;
		
		final String value;
		
		final Date date;
		
		/**
		 * @param order position of the concept in the field's configured list (lower first)
		 * @param label concept display name
		 */
		public Observation(Integer patientId, String field, int order, String label, String value, Date date) {
			this.patientId = patientId;
			this.field = field;
			this.order = order;
			this.label = label;
			this.value = value;
			this.date = date;
		}
	}
	
	/** A previous handover's text for one field, used when no observation is available. */
	public static class PreviousText {
		
		final String text;
		
		final Date date;
		
		public PreviousText(String text, Date date) {
			this.text = text;
			this.date = date;
		}
	}
	
	/** Suggested text for one field plus a human-readable description of where it came from. */
	public static class Suggestion {
		
		private final String text;
		
		private final String source;
		
		public Suggestion(String text, String source) {
			this.text = text;
			this.source = source;
		}
		
		public String getText() {
			return text;
		}
		
		public String getSource() {
			return source;
		}
	}
	
	/**
	 * @param observations rendered observations of any number of patients
	 * @param previous per patient, per field text of the patient's last handover (may be empty)
	 * @return per patient, per field suggestion; fields without any source are absent
	 */
	public Map<Integer, Map<String, Suggestion>> build(Collection<Observation> observations,
	        Map<Integer, Map<String, PreviousText>> previous) {
		Map<Integer, Map<String, List<Observation>>> byPatient = new LinkedHashMap<Integer, Map<String, List<Observation>>>();
		for (Observation o : observations) {
			if (o.patientId == null || o.field == null || !isMeaningful(o.value))
				continue;
			Map<String, List<Observation>> fields = byPatient.get(o.patientId);
			if (fields == null) {
				fields = new LinkedHashMap<String, List<Observation>>();
				byPatient.put(o.patientId, fields);
			}
			List<Observation> list = fields.get(o.field);
			if (list == null) {
				list = new ArrayList<Observation>();
				fields.put(o.field, list);
			}
			list.add(o);
		}
		Set<Integer> patients = new LinkedHashSet<Integer>(byPatient.keySet());
		if (previous != null)
			patients.addAll(previous.keySet());
		
		Map<Integer, Map<String, Suggestion>> result = new LinkedHashMap<Integer, Map<String, Suggestion>>();
		for (Integer patientId : patients) {
			Map<String, Suggestion> suggestions = new LinkedHashMap<String, Suggestion>();
			Map<String, List<Observation>> fields = byPatient.get(patientId);
			for (String field : FIELDS) {
				Suggestion s = fromObservations(fields == null ? null : fields.get(field));
				if (s == null && previous != null && previous.get(patientId) != null)
					s = fromPrevious(previous.get(patientId).get(field));
				if (s != null)
					suggestions.put(field, s);
			}
			if (!suggestions.isEmpty())
				result.put(patientId, suggestions);
		}
		return result;
	}
	
	private Suggestion fromObservations(List<Observation> list) {
		if (list == null || list.isEmpty())
			return null;
		// Group by concept (order + label), keeping only the latest recording time of each concept.
		Map<String, List<Observation>> byConcept = new LinkedHashMap<String, List<Observation>>();
		for (Observation o : list) {
			String key = o.order + "|" + o.label;
			List<Observation> group = byConcept.get(key);
			if (group == null) {
				group = new ArrayList<Observation>();
				byConcept.put(key, group);
			}
			group.add(o);
		}
		List<List<Observation>> groups = new ArrayList<List<Observation>>(byConcept.values());
		Collections.sort(groups, new Comparator<List<Observation>>() {
			
			@Override
			public int compare(List<Observation> a, List<Observation> b) {
				return Integer.valueOf(a.get(0).order).compareTo(b.get(0).order);
			}
		});
		StringBuilder text = new StringBuilder();
		Set<String> sources = new LinkedHashSet<String>();
		for (List<Observation> group : groups) {
			Date latest = null;
			for (Observation o : group)
				if (o.date != null && (latest == null || o.date.after(latest)))
					latest = o.date;
			Set<String> values = new LinkedHashSet<String>();
			for (Observation o : group)
				if (latest == null ? o.date == null : latest.equals(o.date))
					values.add(truncate(o.value.trim().replaceAll("\\s+", " "), MAX_VALUE_LENGTH));
			String line = group.get(0).label + ": " + join(values, ", ")
			        + (latest == null ? "" : " (" + format(latest) + ")");
			int needed = line.length() + (text.length() == 0 ? 0 : 1);
			if (text.length() + needed > MAX_FIELD_LENGTH) {
				if (text.length() == 0)
					text.append(truncate(line, MAX_FIELD_LENGTH));
				break;
			}
			if (text.length() > 0)
				text.append('\n');
			text.append(line);
			sources.add(group.get(0).label);
		}
		return text.length() == 0 ? null : new Suggestion(text.toString(), "Recent records: " + join(sources, ", "));
	}
	
	private Suggestion fromPrevious(PreviousText previous) {
		if (previous == null || isBlank(previous.text))
			return null;
		String when = previous.date == null ? "" : " of " + format(previous.date);
		return new Suggestion(truncate(previous.text.trim(), MAX_FIELD_LENGTH), "Previous handover" + when);
	}
	
	/**
	 * False for empty values and placeholders such as ".", "-" or "..." that contain no letter or
	 * digit.
	 */
	static boolean isMeaningful(String value) {
		if (value == null)
			return false;
		for (int i = 0; i < value.length(); i++)
			if (Character.isLetterOrDigit(value.charAt(i)))
				return true;
		return false;
	}
	
	/**
	 * Makes coded concept names readable: "progress_plan" becomes "Progress plan" and
	 * "KNOWN ALLERGIES" becomes "Known allergies". Names already in mixed case are kept as they
	 * are.
	 */
	public static String readableLabel(String name) {
		if (name == null || name.trim().isEmpty())
			return name;
		String label = name.trim().replace('_', ' ').replaceAll("\\s+", " ");
		boolean hasLower = !label.equals(label.toUpperCase());
		boolean hasUpper = !label.equals(label.toLowerCase());
		if (!hasLower || !hasUpper || !name.equals(label))
			label = label.toLowerCase();
		return Character.toUpperCase(label.charAt(0)) + label.substring(1);
	}
	
	static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}
	
	private static String join(Collection<String> values, String separator) {
		StringBuilder out = new StringBuilder();
		for (String v : values) {
			if (out.length() > 0)
				out.append(separator);
			out.append(v);
		}
		return out.toString();
	}
	
	private static String format(Date date) {
		return new SimpleDateFormat("dd MMM HH:mm").format(date);
	}
	
	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
