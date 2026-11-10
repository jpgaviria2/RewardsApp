package com.bitcoinrewards.nfcdisplay;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-derived fixtures, NOT recorded live responses. Both pinned routes project these fields. */
public class PinnedCheckInResponseTest {
    static byte[] response(String address, boolean testProcessed) throws Exception {
        return new JSONObject().put("status", "checked_in")
            .put("checkInId", CollectionCheckInStoreTest.ID)
            .put("expiresAt", "2026-11-10T18:05:00.1234567Z")
            .put("lightningAddress", address).put("testRewardProcessed", testProcessed)
            .toString().getBytes(StandardCharsets.UTF_8);
    }
    @Test public void bothPinnedRouteProjectionsConfirmWithoutInventedSuccessField() throws Exception {
        for (String destination : new String[]{"coffeelover123@trailscoffee.com", "LNURL1RESOLVEDPAYLINK"}) {
            for (boolean processed : new boolean[]{false, true}) {
                byte[] body = response(destination, processed);
                assertEquals(CollectionCheckInStoreTest.ID, ConfirmedCheckInResponse.confirmedId(body));
                assertEquals(CollectionCheckInStoreTest.ID, CollectionCheckInStore.successId(new ByteArrayInputStream(body)));
            }
        }
    }
    @Test public void actualEnvelopeRejectsPendingDuplicateUnknownAndWrongTypesForPresentationOnly() throws Exception {
        JSONObject base = new JSONObject(new String(response("custom@trailscoffee.com", false), StandardCharsets.UTF_8));
        for (String field : new String[]{"status", "checkInId", "expiresAt", "lightningAddress", "testRewardProcessed"}) {
            JSONObject missing = new JSONObject(base.toString()); missing.remove(field);
            assertNull(ConfirmedCheckInResponse.confirmedId(missing.toString().getBytes(StandardCharsets.UTF_8)));
        }
        for (String status : new String[]{"pending", "duplicate", "CHECKED_IN", ""}) {
            JSONObject bad = new JSONObject(base.toString()).put("status", status);
            assertNull(ConfirmedCheckInResponse.confirmedId(bad.toString().getBytes(StandardCharsets.UTF_8)));
        }
        JSONObject extended = new JSONObject(base.toString()).put("customerName", "Unverified");
        assertNull(ConfirmedCheckInResponse.confirmedId(extended.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(CollectionCheckInStoreTest.ID, CollectionCheckInStore.successId(new ByteArrayInputStream(extended.toString().getBytes(StandardCharsets.UTF_8))));
        for (String field : new String[]{"expiresAt", "lightningAddress", "testRewardProcessed"}) {
            JSONObject bad = new JSONObject(base.toString()).put(field, 1);
            assertNull(ConfirmedCheckInResponse.confirmedId(bad.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }
}
