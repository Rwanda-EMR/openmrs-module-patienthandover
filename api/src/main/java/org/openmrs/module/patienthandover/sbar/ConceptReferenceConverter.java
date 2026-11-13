package org.openmrs.module.patienthandover.sbar;

/**
 * Rewrites numeric concept IDs in an SBAR concept list (positions separated by commas, alternatives
 * by "|") to concept UUIDs. Concept IDs differ between servers; UUIDs do not. Entries that are not
 * numeric (UUIDs, SOURCE:CODE mappings, names and markers such as ABNORMAL_LABS) are kept as they
 * are, and so are IDs the lookup cannot resolve.
 */
public final class ConceptReferenceConverter {
	
	/** Finds the UUID of a concept ID on this server, or null when it does not exist. */
	public interface Lookup {
		
		String uuidOf(int conceptId);
	}
	
	private ConceptReferenceConverter() {
	}
	
	public static String toUuids(String value, Lookup lookup) {
		if (value == null || value.trim().isEmpty())
			return value;
		StringBuilder out = new StringBuilder();
		for (String entry : value.split(",")) {
			if (entry.trim().isEmpty())
				continue;
			if (out.length() > 0)
				out.append(',');
			StringBuilder alternatives = new StringBuilder();
			for (String reference : entry.split("\\|")) {
				String token = reference.trim();
				if (token.isEmpty())
					continue;
				if (token.matches("\\d+")) {
					String uuid = null;
					try {
						uuid = lookup.uuidOf(Integer.parseInt(token));
					}
					catch (NumberFormatException ignored) {}
					if (uuid != null && !uuid.trim().isEmpty())
						token = uuid.trim();
				}
				// After conversion an ID and its UUID may name the same concept twice: keep it once.
				if (("|" + alternatives + "|").contains("|" + token + "|"))
					continue;
				if (alternatives.length() > 0)
					alternatives.append('|');
				alternatives.append(token);
			}
			out.append(alternatives);
		}
		return out.toString();
	}
	
	/** True when the list still contains a numeric concept ID. */
	public static boolean hasNumericIds(String value) {
		if (value == null)
			return false;
		for (String token : value.split("[,|]"))
			if (token.trim().matches("\\d+"))
				return true;
		return false;
	}
}
