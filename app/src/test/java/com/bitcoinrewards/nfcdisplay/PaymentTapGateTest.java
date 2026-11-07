package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentTapGateTest {
    @Test public void freshPendingTapOnlyAndNeverAfterPause() {
        PaymentTapGate gate = new PaymentTapGate();
        assertFalse(gate.allows(gate.ticket(), false, "pending", false));
        gate.resume(); long ticket = gate.ticket();
        assertTrue(gate.allows(ticket, false, "pending", false));
        assertFalse(gate.allows(ticket, true, "pending", false));
        assertFalse(gate.allows(ticket, false, "pending", true));
        for (String status : new String[]{"review", "paid", "expired", "cancelled", "unknown"}) {
            assertFalse(gate.allows(ticket, false, status, false));
        }
        gate.pause(); assertFalse(gate.allows(ticket, false, "pending", false));
        gate.resume(); assertFalse(gate.allows(ticket, false, "pending", false));
        assertTrue(gate.allows(gate.ticket(), false, "pending", false));
    }
    @Test public void rewardsReaderAndPaymentDispatchAreMutuallyExclusive() {
        NfcReaderGate rewards = new NfcReaderGate(); PaymentTapGate payment = new PaymentTapGate();
        rewards.update(true, true, false, false, false);
        assertTrue(rewards.shouldSubmit());
        assertFalse(payment.allows(payment.ticket(), false, "pending", false));
        rewards.update(false, true, false, false, false); payment.resume();
        assertFalse(rewards.shouldIntercept()); assertFalse(rewards.shouldSubmit());
        assertTrue(payment.allows(payment.ticket(), false, "pending", false));
        payment.pause(); rewards.update(true, true, false, false, false);
        assertTrue(rewards.shouldSubmit());
        assertFalse(payment.allows(payment.ticket(), false, "pending", false));
    }
}
