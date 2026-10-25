package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NfcReaderGateTest {
    @Test public void interceptsOnlyForResumedEligibleHealthyDisplay() {
        NfcReaderGate gate = new NfcReaderGate();
        gate.update(true, true, false, false, false);
        assertTrue(gate.shouldIntercept());
        assertTrue(gate.shouldSubmit());

        gate.update(false, true, false, false, false); assertFalse(gate.shouldIntercept());
        gate.update(true, false, false, false, false); assertFalse(gate.shouldIntercept());
        gate.update(true, true, true, false, false); assertFalse(gate.shouldIntercept());
        gate.update(true, true, false, true, false); assertFalse(gate.shouldIntercept());
    }

    @Test public void keepsInterceptingButDoesNotResubmitDuringConfirmation() {
        NfcReaderGate gate = new NfcReaderGate();
        gate.update(true, true, true, false, false);
        assertFalse(gate.shouldIntercept());
        gate.update(true, true, false, false, false);
        assertTrue(gate.shouldIntercept());
        assertTrue(gate.shouldSubmit());
        gate.update(true, true, false, false, true);
        assertTrue(gate.shouldIntercept());
        assertFalse(gate.shouldSubmit());
        gate.update(true, true, false, false, false);
        assertTrue(gate.shouldSubmit());
    }
}
