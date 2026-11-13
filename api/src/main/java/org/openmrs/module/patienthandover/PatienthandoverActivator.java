/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.patienthandover;

import org.apache.commons.logging.Log;
import org.openmrs.Concept;
import org.openmrs.GlobalProperty;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.sbar.ConceptReferenceConverter;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class PatienthandoverActivator extends BaseModuleActivator {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	/**
	 * @see #started()
	 */
	public void started() {
		log.info("Started Patienthandover");
		try {
			convertSbarConceptIdsToUuids();
		}
		catch (Exception e) {
			log.warn("SBAR pre-fill: could not convert concept IDs to UUIDs in the settings", e);
		}
	}
	
	/** SBAR concept-list settings, see patienthandover.sbar.* in config.xml. */
	static final String[] SBAR_CONCEPT_SETTINGS = { "patienthandover.sbar.situationConcepts",
	        "patienthandover.sbar.backgroundConcepts", "patienthandover.sbar.assessmentConcepts",
	        "patienthandover.sbar.recommendationConcepts" };
	
	/**
	 * Concept IDs differ between servers, UUIDs do not. On start-up, numeric concept IDs in the
	 * SBAR settings are replaced by the UUID of that concept on this server (where the ID is
	 * valid), so the settings can be copied to other servers. Settings are saved only when
	 * something changed; unknown IDs are kept and reported.
	 */
	private void convertSbarConceptIdsToUuids() {
		String[] privileges = { "Get Global Properties", "Manage Global Properties", "Get Concepts" };
		for (String privilege : privileges)
			Context.addProxyPrivilege(privilege);
		try {
			final ConceptService concepts = Context.getConceptService();
			AdministrationService administration = Context.getAdministrationService();
			for (String property : SBAR_CONCEPT_SETTINGS) {
				GlobalProperty setting = administration.getGlobalPropertyObject(property);
				if (setting == null || !ConceptReferenceConverter.hasNumericIds(setting.getPropertyValue()))
					continue;
				String converted = ConceptReferenceConverter.toUuids(setting.getPropertyValue(),
				    new ConceptReferenceConverter.Lookup() {
					    
					    @Override
					    public String uuidOf(int conceptId) {
						    Concept concept = concepts.getConcept(conceptId);
						    return concept == null ? null : concept.getUuid();
					    }
				    });
				if (!converted.equals(setting.getPropertyValue())) {
					log.info("SBAR pre-fill: " + property + " now uses concept UUIDs instead of concept IDs");
					setting.setPropertyValue(converted);
					administration.saveGlobalProperty(setting);
				}
				if (ConceptReferenceConverter.hasNumericIds(converted))
					log.warn("SBAR pre-fill: " + property + " still contains concept IDs that do not exist here: "
					        + converted);
			}
		}
		finally {
			for (String privilege : privileges)
				Context.removeProxyPrivilege(privilege);
		}
	}
	
	/**
	 * @see #shutdown()
	 */
	public void shutdown() {
		log.info("Shutdown Patienthandover");
	}
	
}
