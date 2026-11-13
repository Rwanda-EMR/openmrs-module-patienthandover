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

import org.springframework.stereotype.Component;

/**
 * Contains module's config.
 */
@Component("patienthandover.PatienthandoverConfig")
public class PatienthandoverConfig {
	
	public final static String MODULE_PRIVILEGE = "Patienthandover Privilege";
	
	public final static String PRIVILEGE_VIEW = "Patient Handover: View";
	
	public final static String PRIVILEGE_CREATE = "Patient Handover: Create";
	
	public final static String PRIVILEGE_RECEIVE = "Patient Handover: Receive";
	
	public final static String PRIVILEGE_COMPLETE_TASKS = "Patient Handover: Complete Tasks";
	
	public final static String PRIVILEGE_VIEW_REPORTS = "Patient Handover: View Reports";
	
	public final static String PRIVILEGE_EXPORT_REPORTS = "Patient Handover: Export Reports";
	
	public final static String PRIVILEGE_VIEW_ALL_PROVIDERS = "Patient Handover: View All Providers";
	
	public final static String PRIVILEGE_MANAGE = "Patient Handover: Manage";
	
	public final static String PRIVILEGE_CANCEL = "Patient Handover: Cancel";
	
	public final static String PRIVILEGE_EDIT = "Patient Handover: Edit";
	
	/**
	 * Hours an unsent handover draft is kept. A draft only protects what the user is entering
	 * against an accidental reload, crash or session timeout; after this it is discarded.
	 */
	public final static String GP_DRAFT_MAX_AGE_HOURS = "patienthandover.draft.maxAgeHours";
	
	public final static int DEFAULT_DRAFT_MAX_AGE_HOURS = 12;
	
	/**
	 * True when the current user may use the SBAR "Why?" explanation: only the System Developer
	 * role (OpenMRS super user), because it shows settings and concept UUIDs.
	 */
	public static boolean canExplainSbar() {
		org.openmrs.User user = org.openmrs.api.context.Context.getAuthenticatedUser();
		return user != null && user.isSuperUser();
	}
	
	/** The configured draft lifetime in hours (at least 1). */
	public static int draftMaxAgeHours() {
		try {
			String value = org.openmrs.api.context.Context.getAdministrationService().getGlobalProperty(
			    GP_DRAFT_MAX_AGE_HOURS, String.valueOf(DEFAULT_DRAFT_MAX_AGE_HOURS));
			return Math.max(1, Integer.parseInt(value.trim()));
		}
		catch (RuntimeException e) {
			return DEFAULT_DRAFT_MAX_AGE_HOURS;
		}
	}
}
