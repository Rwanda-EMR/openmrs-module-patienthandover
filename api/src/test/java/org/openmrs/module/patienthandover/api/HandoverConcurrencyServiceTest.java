package org.openmrs.module.patienthandover.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.patienthandover.api.dao.PatienthandoverDao;
import org.openmrs.module.patienthandover.api.impl.PatienthandoverServiceImpl;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyCatalogItem;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandover;
import org.openmrs.module.patienthandover.domain.EmergencyTrolleyHandoverItem;
import org.openmrs.module.patienthandover.domain.HandoverTeamMember;
import org.openmrs.module.patienthandover.domain.LaboratoryHandover;

/**
 * Covers the transactional rules of the service: atomic handover + team saves, serialized team
 * signing and the pending-only guard on trolley receipt.
 */
public class HandoverConcurrencyServiceTest {
	
	@InjectMocks
	private PatienthandoverServiceImpl service;
	
	@Mock
	private PatienthandoverDao dao;
	
	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		Context.setUserContext(new UserContext());
	}
	
	@After
	public void tearDown() {
		Context.clearUserContext();
	}
	
	private static HandoverTeamMember member(int id, String role, boolean signed) {
		HandoverTeamMember m = new HandoverTeamMember();
		m.setId(id);
		m.setHandoverType("LABORATORY");
		m.setTransactionKey("tx-1");
		m.setRole(role);
		m.setSigned(signed);
		return m;
	}
	
	private static Object[] row(int id, String role, boolean signed) {
		return new Object[] { id, role, signed };
	}
	
	@Test
	public void signHandoverTeamMember_shouldReportCompleteWhenLastMemberSigns() {
		HandoverTeamMember receiver = member(3, "RECEIVING", false);
		// The locked read already contains the other receiver's committed signature, which the caller's stale
		// in-memory team would have missed.
		when(dao.lockHandoverTeam("LABORATORY", "tx-1")).thenReturn(
		    Arrays.asList(row(1, "OUTGOING", true), row(2, "RECEIVING", true), row(3, "RECEIVING", false)));
		
		assertTrue(service.signHandoverTeamMember(receiver));
		assertTrue(receiver.isSigned());
		assertNotNull(receiver.getDateSigned());
		verify(dao).saveHandoverTeamMember(receiver);
	}
	
	@Test
	public void signHandoverTeamMember_shouldReportIncompleteWhileOthersAreUnsigned() {
		HandoverTeamMember receiver = member(2, "RECEIVING", false);
		when(dao.lockHandoverTeam("LABORATORY", "tx-1")).thenReturn(
		    Arrays.asList(row(1, "OUTGOING", true), row(2, "RECEIVING", false), row(3, "RECEIVING", false)));
		
		assertFalse(service.signHandoverTeamMember(receiver));
		verify(dao).saveHandoverTeamMember(receiver);
	}
	
	@Test
	public void signHandoverTeamMember_shouldAcceptNumericSignedFlags() {
		HandoverTeamMember receiver = member(2, "RECEIVING", false);
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { 1L, "OUTGOING", (byte) 1 });
		rows.add(new Object[] { 2L, "RECEIVING", (byte) 0 });
		when(dao.lockHandoverTeam("LABORATORY", "tx-1")).thenReturn(rows);
		
		assertTrue(service.signHandoverTeamMember(receiver));
	}
	
	@Test
	public void signHandoverTeamMember_shouldRejectDoubleSignature() {
		HandoverTeamMember receiver = member(2, "RECEIVING", false);
		when(dao.lockHandoverTeam("LABORATORY", "tx-1")).thenReturn(
		    Arrays.asList(row(1, "OUTGOING", true), row(2, "RECEIVING", true)));
		try {
			service.signHandoverTeamMember(receiver);
			fail("A second signature must be rejected");
		}
		catch (APIException expected) {}
		verify(dao, never()).saveHandoverTeamMember(receiver);
	}
	
	@Test
	public void signHandoverTeamMember_shouldRejectMemberRemovedFromTeam() {
		HandoverTeamMember receiver = member(9, "RECEIVING", false);
		when(dao.lockHandoverTeam("LABORATORY", "tx-1")).thenReturn(Collections.singletonList(row(1, "OUTGOING", true)));
		try {
			service.signHandoverTeamMember(receiver);
			fail("A voided assignment must not sign");
		}
		catch (APIException expected) {}
		verify(dao, never()).saveHandoverTeamMember(receiver);
	}
	
	@Test
	public void signHandoverTeamMember_shouldNotCompleteWithoutReceivingTeam() {
		HandoverTeamMember sender = member(1, "OUTGOING", false);
		when(dao.lockHandoverTeam("LABORATORY", "tx-1")).thenReturn(Collections.singletonList(row(1, "OUTGOING", false)));
		assertFalse(service.signHandoverTeamMember(sender));
	}
	
	@Test
	public void saveLaboratoryHandover_shouldSaveHandoverBeforeItsTeam() {
		LaboratoryHandover handover = new LaboratoryHandover();
		HandoverTeamMember a = member(0, "OUTGOING", true), b = member(0, "RECEIVING", false);
		
		service.saveLaboratoryHandover(handover, Arrays.asList(a, b));
		
		InOrder order = inOrder(dao);
		order.verify(dao).saveLaboratoryHandover(handover);
		order.verify(dao).saveHandoverTeamMember(a);
		order.verify(dao).saveHandoverTeamMember(b);
	}
	
	@Test
	public void receiveTrolleyHandover_shouldRejectHandoverThatIsNoLongerPending() {
		EmergencyTrolleyHandover handover = new EmergencyTrolleyHandover();
		handover.setId(5);
		handover.setStatus("PENDING"); // stale in-memory state
		when(dao.lockTrolleyHandoverStatus(5)).thenReturn("RECEIVED");
		try {
			service.receiveTrolleyHandover(handover);
			fail("Receiving twice must be rejected");
		}
		catch (APIException expected) {}
		verify(dao, never()).saveTrolleyHandover(handover);
	}
	
	@Test
	public void receiveTrolleyHandover_shouldRejectCancelledHandover() {
		EmergencyTrolleyHandover handover = new EmergencyTrolleyHandover();
		handover.setId(5);
		when(dao.lockTrolleyHandoverStatus(5)).thenReturn("CANCELLED");
		try {
			service.receiveTrolleyHandover(handover);
			fail("A cancelled handover must not be received");
		}
		catch (APIException expected) {}
	}
	
	@Test
	public void receiveTrolleyHandover_shouldMarkReceivedAndApplyHandedQuantities() {
		EmergencyTrolleyCatalogItem item = new EmergencyTrolleyCatalogItem();
		item.setCurrentQuantity(1.0);
		EmergencyTrolleyHandoverItem snapshot = new EmergencyTrolleyHandoverItem();
		snapshot.setCatalogItem(item);
		snapshot.setHandedQuantity(4.0);
		EmergencyTrolleyHandover handover = new EmergencyTrolleyHandover();
		handover.setId(5);
		handover.setStatus("PENDING");
		handover.addItem(snapshot);
		when(dao.lockTrolleyHandoverStatus(5)).thenReturn("PENDING");
		
		service.receiveTrolleyHandover(handover);
		
		assertEquals("RECEIVED", handover.getStatus());
		assertNotNull(handover.getDateReceived());
		assertEquals(Double.valueOf(4.0), item.getCurrentQuantity());
		verify(dao).saveTrolleyCatalogItem(item);
		verify(dao).saveTrolleyHandover(handover);
	}
	
	@Test
	public void getHandoverTeams_shouldGroupMembersByTransactionKeyIncludingEmptyTeams() {
		HandoverTeamMember a = member(1, "OUTGOING", true);
		a.setTransactionKey("tx-a");
		HandoverTeamMember b = member(2, "RECEIVING", false);
		b.setTransactionKey("tx-a");
		when(dao.getHandoverTeams("LABORATORY", Arrays.asList("tx-a", "tx-b"))).thenReturn(Arrays.asList(a, b));
		
		Map<String, List<HandoverTeamMember>> teams = service.getHandoverTeams("LABORATORY", Arrays.asList("tx-a", "tx-b"));
		
		assertEquals(2, teams.get("tx-a").size());
		assertTrue(teams.get("tx-b").isEmpty());
	}
}
