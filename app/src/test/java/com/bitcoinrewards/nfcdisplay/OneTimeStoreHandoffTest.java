package com.bitcoinrewards.nfcdisplay;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class OneTimeStoreHandoffTest {
    @After public void clearHandoff() {
        OneTimeStoreHandoff.clear();
    }

    @Test public void storePayloadCanBeConsumedOnlyOnce() {
        OneTimeStoreHandoff.publish("https://btcpay.example/plugins/bitcoin-rewards/store/display");
        assertEquals("https://btcpay.example/plugins/bitcoin-rewards/store/display", OneTimeStoreHandoff.consume());
        assertNull(OneTimeStoreHandoff.consume());
    }

    @Test public void newerStoreScanReplacesUnconsumedPayload() {
        OneTimeStoreHandoff.publish("first");
        OneTimeStoreHandoff.publish("second");
        assertEquals("second", OneTimeStoreHandoff.consume());
    }
}
