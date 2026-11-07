package com.bitcoinrewards.nfcdisplay;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentOperationStoreTest {
    static final class Memory {
        final Map<String, Object> disk = new HashMap<>();
        final Map<String, Object> durable = new HashMap<>();
        void restartProcess() { disk.clear(); disk.putAll(durable); }
        boolean writable = true;
        SharedPreferences preferences() {
            return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SharedPreferences.class}, (p,m,a) -> {
                switch (m.getName()) {
                    case "contains": return disk.containsKey(a[0]);
                    case "getString": case "getLong": case "getBoolean": return disk.getOrDefault(a[0], a[1]);
                    case "edit": return editor();
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });
        }
        SharedPreferences.Editor editor() {
            Map<String,Object> staged = new HashMap<>(); boolean[] clear = {false};
            return (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SharedPreferences.Editor.class}, (p,m,a) -> {
                switch (m.getName()) {
                    case "putString": case "putLong": case "putBoolean": staged.put((String)a[0],a[1]); return p;
                    case "remove": staged.put((String)a[0], null); return p;
                    case "clear": clear[0]=true; return p;
                    case "commit":
                        if(clear[0]) disk.clear();
                        staged.forEach((k,v) -> { if(v == null) disk.remove(k); else disk.put(k,v); });
                        if (!writable) return false;
                        durable.clear(); durable.putAll(disk); return true;
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });
        }
        PaymentOperationStore open() { return new PaymentOperationStore(preferences()); }
    }
    @Test public void processDeathRetainsIdentityAndBlocksReplacement() {
        Memory memory = new Memory(); PaymentOperationStore first = memory.open();
        assertTrue(first.begin("https://merchant.example", "Store1", 1234));
        String id = first.id(); UUID.fromString(id);
        PaymentOperationStore afterDeath = memory.open();
        assertEquals(id, afterDeath.id()); assertEquals(1234, afterDeath.amount());
        assertEquals("https://merchant.example", afterDeath.origin()); assertEquals("Store1", afterDeath.store());
        assertFalse(afterDeath.begin("https://other.example", "Other", 999));
        assertEquals(id, afterDeath.id());
        assertFalse(afterDeath.clearTerminal("pending")); assertFalse(afterDeath.clearTerminal("review"));
        assertTrue(afterDeath.exists());
    }
    @Test public void tapDispatchIsDurableAndNeverReplayable() {
        Memory memory = new Memory(); PaymentOperationStore first = memory.open();
        assertFalse(first.markTap());
        assertTrue(first.begin("https://merchant.example", "Store1", 1));
        assertTrue(first.markTap()); assertFalse(first.markTap());
        assertTrue(memory.open().tapAttempted()); assertFalse(memory.open().markTap());
    }
    @Test public void immutableInvoiceIdentitySurvivesRecreationAndOnlyExplicitTerminalFinishClears() {
        Memory memory = new Memory(); PaymentOperationStore first = memory.open();
        assertTrue(first.begin("https://merchant.example", "Store1", 100));
        assertTrue(first.bindInvoice("invoice1")); assertFalse(memory.open().bindInvoice("invoice2"));
        assertEquals("invoice1", memory.open().invoiceId());
        assertTrue(first.clearTerminal("paid")); assertFalse(memory.open().exists());
        assertTrue(memory.open().begin("https://merchant.example", "Store1", 200));
    }
    @Test public void failedDurabilityNeverGrantsCreateTapOrFinish() {
        Memory memory = new Memory(); memory.writable = false;
        assertFalse(memory.open().begin("https://merchant.example", "Store1", 100));
        memory.restartProcess(); // Failed begin did not dispatch and did not reach disk.
        memory.writable = true; assertTrue(memory.open().begin("https://merchant.example", "Store1", 100));
        memory.writable = false; assertFalse(memory.open().markTap()); assertFalse(memory.open().clearTerminal("paid"));
        assertTrue(memory.open().exists());
        memory.restartProcess(); assertTrue(memory.open().exists());
        assertFalse(memory.open().tapAttempted()); // No request was sent after failed markTap.
    }
    @Test public void expectedCheckInIsImmutableAndDurableBeforeDispatch() {
        Memory memory = new Memory(); String expected = CollectionCheckInStoreTest.ID;
        assertFalse(memory.open().begin("https://merchant.example", "Store1", 100, "1-1-1-1-1"));
        assertTrue(memory.open().begin("https://merchant.example", "Store1", 100, expected));
        assertEquals(expected, memory.open().expectedCheckInId());
        assertFalse(memory.open().begin("https://merchant.example", "Store1", 200, null));
        assertEquals(expected, memory.open().expectedCheckInId());
    }
    @Test public void terminalHistoryKeepsReferenceAndRecoversSameOperationWithoutReplayingTap() throws Exception {
        for (String status : new String[]{"paid", "cancelled", "expired"}) {
            Memory memory = new Memory(); PaymentOperationStore op = memory.open();
            assertTrue(op.begin("https://merchant.example", "Store1", 100));
            String id = op.id(); assertTrue(op.bindInvoice("invoice1")); assertTrue(op.markTap());
            assertTrue(op.clearTerminal(status)); assertFalse(memory.open().exists());
            assertEquals(id, memory.open().history().getJSONObject(0).getString("operation"));
            assertTrue(memory.open().recoverHistory(0)); assertEquals(id, memory.open().id());
            assertEquals("invoice1", memory.open().invoiceId()); assertTrue(memory.open().tapAttempted());
            assertFalse(memory.open().begin("https://merchant.example", "Store1", 100));
            assertTrue(memory.open().clearTerminal("paid")); assertEquals(1, memory.open().history().length());
        }
    }
    @Test public void historySurvivesSubsequentOperationsAndFailedArchiveCannotUnlock() throws Exception {
        Memory memory = new Memory(); PaymentOperationStore op = memory.open();
        assertTrue(op.begin("https://merchant.example", "Store1", 100, CollectionCheckInStoreTest.ID)); String id = op.id();
        assertTrue(op.bindInvoice("invoice1")); assertTrue(op.markTap());
        memory.writable = false; assertFalse(op.clearTerminal("expired")); assertTrue(op.exists());
        assertEquals(id, memory.open().id());
        assertEquals(CollectionCheckInStoreTest.ID, memory.open().expectedCheckInId());
        assertTrue(memory.open().tapAttempted()); assertEquals("invoice1", memory.open().invoiceId());
        assertEquals(100, memory.open().amount()); assertEquals("Store1", memory.open().store());
        assertEquals("https://merchant.example", memory.open().origin());
        memory.restartProcess(); assertTrue(memory.open().exists()); assertEquals(id, memory.open().id());
        assertEquals(CollectionCheckInStoreTest.ID, memory.open().expectedCheckInId()); assertTrue(memory.open().tapAttempted());
        memory.writable = true; assertTrue(op.clearTerminal("expired"));
        assertTrue(op.begin("https://other.example", "Store2", 200)); assertTrue(op.clearTerminal("paid"));
        assertEquals(2, memory.open().history().length()); assertTrue(memory.open().recoverHistory(0));
        assertEquals(id, memory.open().id()); assertEquals("https://merchant.example", memory.open().origin());
    }
    @Test public void failedArchiveFollowedByAnotherWriteCannotEraseActiveReference() throws Exception {
        Memory memory = new Memory(); PaymentOperationStore op = memory.open();
        assertTrue(op.begin("https://merchant.example", "Store1", 100));
        String id = op.id(); assertTrue(op.bindInvoice("invoice1"));
        memory.writable = false; assertFalse(op.clearTerminal("cancelled"));
        assertTrue(memory.open().exists()); assertEquals(id, memory.open().id());
        memory.writable = true; assertTrue(memory.open().markTap());
        memory.restartProcess(); assertTrue(memory.open().exists()); assertEquals(id, memory.open().id());
        assertEquals("invoice1", memory.open().invoiceId());
        assertEquals(id, memory.open().history().getJSONObject(0).getString("operation"));
    }
    @Test public void persistedFieldsAreOnlyNonSecretIdentityAndDispatchMetadata() {
        Memory memory = new Memory(); PaymentOperationStore operation = memory.open();
        operation.begin("https://merchant.example", "Store1", 100); operation.bindInvoice("invoice1"); operation.markTap();
        assertEquals(new HashSet<>(Arrays.asList("operation", "amount", "origin", "store", "invoice_id", "tap_attempted")), memory.disk.keySet());
    }
}
