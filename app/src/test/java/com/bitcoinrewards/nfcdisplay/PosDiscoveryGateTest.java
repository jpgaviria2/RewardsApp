package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosDiscoveryGateTest {
    @Test public void pauseCancelAndNewAttemptsRejectLateResultsAndSelections() {
        PosDiscoveryGate gate = new PosDiscoveryGate("https://pay.example", "Store1");
        long old = gate.invalidate();
        assertFalse(gate.accepts(old, "https://pay.example", "Store1"));
        gate.resume(); long first = gate.invalidate();
        assertTrue(gate.accepts(first, "https://pay.example", "Store1"));
        gate.pause(); assertFalse(gate.accepts(first, "https://pay.example", "Store1"));
        gate.resume(); long second = gate.invalidate();
        assertFalse(gate.accepts(first, "https://pay.example", "Store1"));
        assertTrue(gate.accepts(second, "https://pay.example", "Store1"));
        gate.invalidate(); assertFalse(gate.accepts(second, "https://pay.example", "Store1"));
    }
    @Test public void exactOriginAndStoreAndAToBToARequireFreshAttempt() {
        PosDiscoveryGate gate = new PosDiscoveryGate("https://pay.example", "Store1");
        gate.resume(); long first = gate.invalidate();
        assertFalse(gate.accepts(first, "https://foreign.example", "Store1"));
        assertFalse(gate.accepts(first, "https://pay.example", "Other"));
        gate.pause(); gate.resume();
        assertFalse(gate.accepts(first, "https://pay.example", "Store1"));
    }
}
