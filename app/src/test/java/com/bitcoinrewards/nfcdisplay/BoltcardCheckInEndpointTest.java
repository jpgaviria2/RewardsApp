package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BoltcardCheckInEndpointTest {
    @Test public void appendsDedicatedBoltcardRouteToLegacyApi() {
        assertEquals(
            "https://btcpay.example/plugins/bitcoin-rewards/store/check-in/api/boltcard",
            BoltcardCheckInEndpoint.fromLegacyCheckInUrl(
                "https://btcpay.example/plugins/bitcoin-rewards/store/check-in/api"));
    }

    @Test public void preservesExplicitDefaultPortWithoutChangingOrigin() {
        assertEquals(
            "https://btcpay.example:443/plugins/bitcoin-rewards/store/check-in/api/boltcard",
            BoltcardCheckInEndpoint.fromLegacyCheckInUrl(
                "https://btcpay.example:443/plugins/bitcoin-rewards/store/check-in/api"));
        assertEquals(
            "https://btcpay.example/plugins/bitcoin-rewards/store%2Fid/check-in/api/boltcard",
            BoltcardCheckInEndpoint.fromLegacyCheckInUrl(
                "https://btcpay.example/plugins/bitcoin-rewards/store%2Fid/check-in/api"));
    }

    @Test public void rejectsWrongPathQueryFragmentCredentialsAndCleartext() {
        reject("https://btcpay.example/check-in/not-api");
        reject("https://btcpay.example/check-in/api?next=elsewhere");
        reject("https://btcpay.example/check-in/api#fragment");
        reject("https://user@btcpay.example/check-in/api");
        reject("http://btcpay.example/check-in/api");
    }

    private static void reject(String value) {
        try { BoltcardCheckInEndpoint.fromLegacyCheckInUrl(value); fail("accepted " + value); }
        catch (IllegalArgumentException expected) { }
    }
}
