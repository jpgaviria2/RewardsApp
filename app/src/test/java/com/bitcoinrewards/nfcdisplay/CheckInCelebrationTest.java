package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class CheckInCelebrationTest {
    private static final String ID = CollectionCheckInStoreTest.ID;
    @Test public void longRunningKioskConsumesEachOperationWithoutLifetimeExhaustion() {
        CheckInCelebration state = new CheckInCelebration();
        for (int i = 1; i <= 1024; i++) {
            String id = String.format("%08x-1111-1111-1111-111111111111", i);
            state.begin(i);
            assertTrue("new check-in " + i, state.confirm(id, "coffeelover" + i, i));
            assertFalse("one completion per operation", state.confirm(id, "stale", i));
            state.clear(); state.begin(i + 1);
            assertFalse("current identity replay", state.confirm(id.toUpperCase(java.util.Locale.ROOT), "stale", i + 1));
            assertEquals("", state.name());
        }
    }
    @Test public void untrustedDisplayNameCannotInjectControlsOrUnboundedText() {
        for (String name : new String[]{"Ada\nOther customer", " ", "A".repeat(161), "Ada\u202Eevil"}) {
            CheckInCelebration state = new CheckInCelebration();
            state.begin(1);
            assertTrue(state.confirm(ID, name, 1));
            assertEquals("", state.name());
        }
    }
    @Test public void onlyCompletedHttpStatusesAreEligible() {
        assertTrue(CheckInCelebration.isConfirmedHttpStatus(200));
        assertTrue(CheckInCelebration.isConfirmedHttpStatus(201));
        for (int status : new int[]{0, 202, 204, 206, 208, 301, 400, 401, 403, 409, 500}) {
            assertFalse(CheckInCelebration.isConfirmedHttpStatus(status));
        }
    }
    @Test public void confirmsOnlyCurrentActiveResponseAndClearsNameOnIdle() {
        CheckInCelebration state = new CheckInCelebration();
        state.begin(5);
        assertTrue(state.confirm(ID, "Ada Lovelace", 5));
        assertEquals("Ada Lovelace", state.name());
        assertTrue(state.isCelebrating());
        state.clear();
        assertEquals("", state.name());
        assertFalse(state.isCelebrating());
        assertFalse(state.confirm(ID, "Ada Lovelace", 5));
    }
    @Test public void staleAndRepeatedResponsesNeverCelebrate() {
        CheckInCelebration state = new CheckInCelebration();
        state.begin(5);
        assertFalse(state.confirm(ID, "Ada", 4));
        assertFalse(state.confirm(null, "Ada", 5));
        assertFalse(state.confirm("not-a-guid", "Ada", 5));
        assertTrue(state.confirm(ID, null, 5));
        assertEquals("", state.name());
        state.clear(); state.begin(6);
        assertFalse(state.confirm(ID.toLowerCase(java.util.Locale.ROOT), "Ada", 6));
    }
    @Test public void clearRevokesLateResponseAfterLifecycleChange() {
        CheckInCelebration state = new CheckInCelebration();
        state.begin(5); state.clear();
        assertFalse(state.confirm(ID, "Ada", 5));
        assertFalse(state.isCelebrating());
        assertEquals("", state.name());
    }
}
