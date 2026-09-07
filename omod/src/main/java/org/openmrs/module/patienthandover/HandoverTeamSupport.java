package org.openmrs.module.patienthandover;

import java.util.Date;
import java.util.List;
import org.openmrs.Person;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.patienthandover.api.PatienthandoverService;
import org.openmrs.module.patienthandover.domain.HandoverTeamMember;

public final class HandoverTeamSupport {
	
	public static final String OUTGOING = "OUTGOING";
	
	public static final String RECEIVING = "RECEIVING";
	
	private HandoverTeamSupport() {
	}
	
	public static HandoverTeamMember assignment(List<HandoverTeamMember> team, User user) {
		if (user == null || user.getPerson() == null)
			return null;
		for (HandoverTeamMember member : team)
			if (!Boolean.TRUE.equals(member.getVoided()) && !member.isSigned()
			        && samePerson(member.getPerson(), user.getPerson()))
				return member;
		return null;
	}
	
	public static boolean isMember(List<HandoverTeamMember> team, User user) {
		if (user == null || user.getPerson() == null)
			return false;
		for (HandoverTeamMember member : team)
			if (!Boolean.TRUE.equals(member.getVoided()) && samePerson(member.getPerson(), user.getPerson()))
				return true;
		return false;
	}
	
	public static boolean allSigned(List<HandoverTeamMember> team, String role) {
		boolean found = false;
		for (HandoverTeamMember member : team)
			if (!Boolean.TRUE.equals(member.getVoided()) && role.equals(member.getRole())) {
				found = true;
				if (!member.isSigned())
					return false;
			}
		return found;
	}
	
	public static int count(List<HandoverTeamMember> team, String role, boolean signedOnly) {
		int count = 0;
		for (HandoverTeamMember member : team)
			if (!Boolean.TRUE.equals(member.getVoided()) && role.equals(member.getRole())
			        && (!signedOnly || member.isSigned()))
				count++;
		return count;
	}
	
	public static boolean maySign(HandoverTeamMember member, List<HandoverTeamMember> team) {
		return member != null && (OUTGOING.equals(member.getRole()) || allSigned(team, OUTGOING));
	}
	
	public static void sign(HandoverTeamMember member, PatienthandoverService service) {
		User user = Context.getAuthenticatedUser();
		member.setSigned(true);
		member.setSignedBy(user);
		member.setDateSigned(new Date());
		member.setChangedBy(user);
		member.setDateChanged(new Date());
		service.saveHandoverTeamMember(member);
	}
	
	private static boolean samePerson(Person left, Person right) {
		return left != null && right != null && left.getPersonId() != null && left.getPersonId().equals(right.getPersonId());
	}
}
