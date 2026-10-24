package com.bitcoinrewards.nfcdisplay;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class OneTimeLoginHandoffTest {
    @After public void clearHandoff() {
        OneTimeLoginHandoff.clear();
    }

    @Test public void loginUrlCanBeConsumedOnlyOnce() {
        OneTimeLoginHandoff.publish("https://btcpay.example/login?secret");
        assertEquals("https://btcpay.example/login?secret", OneTimeLoginHandoff.consume());
        assertNull(OneTimeLoginHandoff.consume());
    }

    @Test public void processRecreationStateHasNoReplayableLoginUrl() {
        OneTimeLoginHandoff.publish("https://btcpay.example/login?secret");
        OneTimeLoginHandoff.clear();
        assertNull(OneTimeLoginHandoff.consume());
    }

    @Test public void newerScanReplacesUnconsumedLoginUrl() {
        OneTimeLoginHandoff.publish("first");
        OneTimeLoginHandoff.publish("second");
        assertEquals("second", OneTimeLoginHandoff.consume());
        assertNull(OneTimeLoginHandoff.consume());
    }
}
