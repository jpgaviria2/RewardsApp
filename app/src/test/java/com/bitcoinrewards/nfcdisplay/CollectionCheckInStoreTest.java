package com.bitcoinrewards.nfcdisplay;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionCheckInStoreTest {
    static final String ID = "CD829D5A-7652-4F2A-BCFF-698132447685";
    static final String ORIGIN = "https://merchant.example";
    private String parse(String body) throws Exception {
        return CollectionCheckInStore.successId(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void capturesExactServerGuidAndLegacyNoIdStaysCompatible() throws Exception {
        assertEquals(ID, parse("{\"checkInId\":\"" + ID + "\",\"success\":true}"));
        assertNull(parse("{\"success\":true}")); assertNull(parse(""));
        assertNull(parse("{\"checkInId\":null}")); assertNull(parse("{\"checkInId\":42}"));
        for (String id : new String[]{"1-1-1-1-1", " "+ID, ID+" ", "00000000-0000-0000-0000-000000000000", "device"}) {
            assertFalse(CollectionCheckInStore.valid(id));
            assertNull(parse("{\"checkInId\":\""+id+"\"}"));
        }
    }
    @Test public void malformedDuplicateAndOversizedBodiesNeverCaptureIdentity() throws Exception {
        for (String body : new String[]{"{", "{\"checkInId\":\""+ID+"\",\"checkInId\":\""+ID+"\"}", " ".repeat(32769),
                "{\"x\":".repeat(3000) + "0" + "}".repeat(3000), "{\"x\":" + "[".repeat(3000) + "0" + "]".repeat(3000) + "}"}) {
            try { parse(body); fail(); } catch (Exception expected) { }
        }
    }
    @Test public void scopeBindingSurvivesRestartAndNeverUsesDeviceOrForeignOrigin() {
        PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
        CollectionCheckInStore store = new CollectionCheckInStore(memory.preferences());
        assertTrue(store.capture("https://MERCHANT.example:443/", "Store1", ID));
        store = new CollectionCheckInStore(memory.preferences());
        assertEquals(ID, store.matching(ORIGIN, "Store1"));
        assertNull(store.matching("https://evil.example", "Store1"));
        assertNull(store.matching("http://merchant.example", "Store1"));
        assertNull(store.matching(ORIGIN, "Store2"));
        assertTrue(store.release(ORIGIN, "Store2", ID)); assertEquals(ID, store.matching(ORIGIN, "Store1"));
        assertTrue(store.release(ORIGIN, "Store1", "cd829d5a-7652-4f2a-bcff-698132447685"));
        assertEquals(ID, store.matching(ORIGIN, "Store1"));
        assertTrue(store.release(ORIGIN, "Store1", ID)); assertNull(store.matching(ORIGIN, "Store1"));
    }
    @Test public void failedReleaseRetainsCaptureAcrossActivityAndProcessRecreation() {
        PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
        CollectionCheckInStore store = new CollectionCheckInStore(memory.preferences());
        assertTrue(store.capture(ORIGIN, "Store1", ID)); memory.writable = false;
        assertFalse(store.release(ORIGIN, "Store1", ID));
        assertEquals(ID, new CollectionCheckInStore(memory.preferences()).matching(ORIGIN, "Store1"));
        memory.restartProcess(); assertEquals(ID, store.matching(ORIGIN, "Store1"));
        memory.writable = true; assertTrue(store.release(ORIGIN, "Store1", ID));
        memory.restartProcess(); assertNull(store.matching(ORIGIN, "Store1"));
    }
    @Test public void legacySuccessInvalidatesPreviousCustomerAndDurabilityFailureIsReported() {
        PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
        CollectionCheckInStore store = new CollectionCheckInStore(memory.preferences());
        assertTrue(store.capture(ORIGIN, "Store1", ID));
        assertTrue(store.capture(ORIGIN, "Store1", null)); assertNull(store.matching(ORIGIN, "Store1"));
        memory.writable = false; assertFalse(store.capture(ORIGIN, "Store1", ID));
    }
}
