package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NfcReaderGateTest {
    @Test public void enablesOnlyForResumedEligibleHealthyIdleDisplay() {
        NfcReaderGate gate = new NfcReaderGate();
        gate.update(true, true, false, false, false);
        assertTrue(gate.shouldEnable());

        gate.update(false, true, false, false, false); assertFalse(gate.shouldEnable());
        gate.update(true, false, false, false, false); assertFalse(gate.shouldEnable());
        gate.update(true, true, true, false, false); assertFalse(gate.shouldEnable());
        gate.update(true, true, false, true, false); assertFalse(gate.shouldEnable());
        gate.update(true, true, false, false, true); assertFalse(gate.shouldEnable());
    }

    @Test public void recoversAfterTransientOrSubmissionGateClears() {
        NfcReaderGate gate = new NfcReaderGate();
        gate.update(true, true, true, false, false);
        assertFalse(gate.shouldEnable());
        gate.update(true, true, false, false, false);
        assertTrue(gate.shouldEnable());
        gate.update(true, true, false, false, true);
        assertFalse(gate.shouldEnable());
        gate.update(true, true, false, false, false);
        assertTrue(gate.shouldEnable());
    }
}
