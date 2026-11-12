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
	
	/**
	 * Records the member's signature. Concurrent signers of the same team are serialized by the
	 * service.
	 * 
	 * @return true when the team is now complete (every outgoing and receiving member has signed)
	 */
	public static boolean sign(HandoverTeamMember member, PatienthandoverService service) {
		return service.signHandoverTeamMember(member);
	}
	
	/**
	 * Builds an unsaved team member; persist it with the handover via the service's save(handover,
	 * team) methods.
	 */
	public static HandoverTeamMember newMember(String handoverType, String transactionKey, Person person, String role,
	        boolean signed) {
		User user = Context.getAuthenticatedUser();
		Date now = new Date();
		HandoverTeamMember member = new HandoverTeamMember();
		member.setUuid(java.util.UUID.randomUUID().toString());
		member.setHandoverType(handoverType);
		member.setTransactionKey(transactionKey);
		member.setPerson(person);
		member.setRole(role);
		member.setSigned(signed);
		member.setSignedBy(signed ? user : null);
		member.setDateSigned(signed ? now : null);
		member.setCreator(user);
		member.setDateCreated(now);
		return member;
	}
	
	/**
	 * Builds the initial team for a new handover: the submitting user (already signed) plus the
	 * selected outgoing and receiving people. A person appears at most once per role.
	 */
	public static List<HandoverTeamMember> newTeam(String handoverType, String transactionKey, Person submitter,
	        List<Person> outgoing, List<Person> receiving) {
		List<HandoverTeamMember> team = new java.util.ArrayList<HandoverTeamMember>();
		java.util.Set<String> seen = new java.util.HashSet<String>();
		if (submitter != null && seen.add(OUTGOING + "|" + submitter.getPersonId()))
			team.add(newMember(handoverType, transactionKey, submitter, OUTGOING, true));
		for (Person person : outgoing)
			if (person != null && seen.add(OUTGOING + "|" + person.getPersonId()))
				team.add(newMember(handoverType, transactionKey, person, OUTGOING, false));
		for (Person person : receiving)
			if (person != null && seen.add(RECEIVING + "|" + person.getPersonId()))
				team.add(newMember(handoverType, transactionKey, person, RECEIVING, false));
		return team;
	}
	
	/** Collects the people of the given providers, skipping providers without a person. */
	public static List<Person> people(List<org.openmrs.Provider> providers) {
		List<Person> people = new java.util.ArrayList<Person>();
		for (org.openmrs.Provider provider : providers)
			if (provider != null && provider.getPerson() != null)
				people.add(provider.getPerson());
		return people;
	}
	
	private static boolean samePerson(Person left, Person right) {
		return left != null && right != null && left.getPersonId() != null && left.getPersonId().equals(right.getPersonId());
	}
}
