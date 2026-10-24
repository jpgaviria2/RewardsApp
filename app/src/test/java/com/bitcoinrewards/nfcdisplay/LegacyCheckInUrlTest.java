package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class LegacyCheckInUrlTest {
    @Test public void legacyUrlUsesTheConfiguredOriginAndEncodedStorePath() {
        assertEquals(
            "https://btcpay.example/plugins/bitcoin-rewards/store%20%2A%2F%3F%C3%A9/check-in/api",
            SettingsActivity.buildLegacyCheckInUrl("https://btcpay.example/", "store */?é"));
    }

    @Test public void legacyUrlRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> SettingsActivity.buildLegacyCheckInUrl("http://btcpay.example", "store"));
        assertThrows(IllegalArgumentException.class, () -> SettingsActivity.buildLegacyCheckInUrl("https://btcpay.example", ""));
    }
}