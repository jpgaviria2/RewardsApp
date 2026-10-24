package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DisplayRecoveryUiStateTest {
    @Test public void rejectedRetryKeepsTerminalGuidanceThroughPageFinished() {
        DisplayRecoveryUiState state = new DisplayRecoveryUiState();

        state.onTransientFailure(false);

        assertTrue(state.shouldHoldFailureUi());
        assertEquals(
            "Could not reconnect to BTCPay. Check the server and Wi-Fi, then reopen the app or use Settings.",
            state.message());
        // A failed page completion reads state but must not clear it.
        assertTrue(state.shouldHoldFailureUi());
    }

    @Test public void acceptedRetryKeepsReconnectStateUntilTrustedReloadStarts() {
        DisplayRecoveryUiState state = new DisplayRecoveryUiState();

        state.onTransientFailure(true);
        assertTrue(state.shouldHoldFailureUi());
        assertEquals("Reconnecting to BTCPay...", state.message());

        state.onTrustedDisplayLoadStarted();
        assertFalse(state.shouldHoldFailureUi());
    }

    @Test public void successfulTrustedDisplayClearsTerminalFailure() {
        DisplayRecoveryUiState state = new DisplayRecoveryUiState();
        state.onTransientFailure(false);

        state.onSuccessfulTrustedDisplay();

        assertFalse(state.shouldHoldFailureUi());
    }
}
