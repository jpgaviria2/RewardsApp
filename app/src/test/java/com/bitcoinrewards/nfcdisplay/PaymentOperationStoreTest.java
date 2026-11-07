package com.bitcoinrewards.nfcdisplay;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentOperationStoreTest {
    static final class Memory {
        final Map<String, Object> disk = new HashMap<>();
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
                    case "clear": clear[0]=true; return p;
                    case "commit": if (!writable) return false; if(clear[0]) disk.clear(); disk.putAll(staged); return true;
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
        memory.writable = true; assertTrue(memory.open().begin("https://merchant.example", "Store1", 100));
        memory.writable = false; assertFalse(memory.open().markTap()); assertFalse(memory.open().clearTerminal("paid"));
        assertTrue(memory.open().exists());
    }
    @Test public void persistedFieldsAreOnlyNonSecretIdentityAndDispatchMetadata() {
        Memory memory = new Memory(); PaymentOperationStore operation = memory.open();
        operation.begin("https://merchant.example", "Store1", 100); operation.bindInvoice("invoice1"); operation.markTap();
        assertEquals(new HashSet<>(Arrays.asList("operation", "amount", "origin", "store", "invoice_id", "tap_attempted")), memory.disk.keySet());
    }
}
