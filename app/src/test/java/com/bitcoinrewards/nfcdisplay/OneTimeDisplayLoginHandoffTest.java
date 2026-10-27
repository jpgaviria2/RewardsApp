package com.bitcoinrewards.nfcdisplay;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class OneTimeDisplayLoginHandoffTest {
    @After public void clearHandoff() {
        OneTimeDisplayLoginHandoff.clear();
    }

    @Test public void requestCanBeConsumedOnlyOnce() {
        OneTimeDisplayLoginHandoff.publish("https://btcpay.example/login?secret");
        OneTimeDisplayLoginHandoff.Request request = OneTimeDisplayLoginHandoff.consume();
        assertEquals("https://btcpay.example/login?secret", request.loginUrl);
        assertFalse(request.hasPendingConfiguration());
        assertNull(OneTimeDisplayLoginHandoff.consume());
    }

    @Test public void pendingConfigurationTravelsOnlyWithLoginRequest() {
        OneTimeDisplayLoginHandoff.publishWithPendingConfiguration(
            "https://btcpay.example/login?secret", "https://BTCPAY.example/", "store-one");
        OneTimeDisplayLoginHandoff.Request request = OneTimeDisplayLoginHandoff.consume();
        assertEquals("https://btcpay.example", request.pendingOrigin);
        assertEquals("store-one", request.pendingStoreId);
        assertTrue(request.hasPendingConfiguration());
    }

    @Test public void invalidPendingConfigurationIsRejected() {
        try {
            OneTimeDisplayLoginHandoff.publishWithPendingConfiguration(
                "https://btcpay.example/login?secret", "http://btcpay.example", "store-one");
            fail("Expected rejection");
        } catch (IllegalArgumentException expected) { }
    }
}
