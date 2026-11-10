package com.bitcoinrewards.nfcdisplay;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfirmedCheckInTest {
    @Test public void financialIdentityRemainsCompatibleWithExtendedServerResponse() throws Exception {
        String body = "{\"checkInId\":\"" + CollectionCheckInStoreTest.ID + "\",\"success\":true,\"customerName\":\"Unverified field\"}";
        assertEquals(CollectionCheckInStoreTest.ID, CollectionCheckInStore.successId(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
    }
    private String parse(String fields) throws Exception {
        return ConfirmedCheckInResponse.confirmedId(
            ("{\"checkInId\":\"" + CollectionCheckInStoreTest.ID + "\"," + fields + "}").getBytes(StandardCharsets.UTF_8));
    }
    @Test public void onlyExactSuccessfulEnvelopeCanConfirm() throws Exception {
        assertNull("Legacy local success fixture is not the pinned server confirmation contract", parse("\"success\":true"));
        for (String raw : new String[]{"", "{", "{\"success\":true}", "{\"success\":true,\"checkInId\":\"bad\"}",
            "{\"success\":true,\"success\":true,\"checkInId\":\"" + CollectionCheckInStoreTest.ID + "\"}", " ".repeat(32769)}) {
            assertNull(ConfirmedCheckInResponse.confirmedId(raw.getBytes(StandardCharsets.UTF_8)));
        }
    }
    @Test public void pendingDuplicateAndUnrecognizedResponsesFailClosed() throws Exception {
        for (String extra : new String[]{"\"status\":\"PENDING\"", "\"duplicate\":true", "\"alreadyCheckedIn\":true", "\"confirmed\":false", "\"customerName\":\"Unverified field\""}) {
            assertNull(parse("\"success\":true," + extra));
        }
    }
    @Test public void identityAloneOrHttpSuccessDoesNotConfirmCheckIn() throws Exception {
        assertNull(parse("\"success\":false"));
        assertNull(parse("\"success\":\"true\""));
        assertNull(parse("\"unconfirmed\":true"));
    }
}
