package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainFrameLoadGateTest {
    private static final String DISPLAY_URL =
        "https://btcpay.example/plugins/bitcoin-rewards/store/display";

    @Test public void mainFrameNetworkFailureBlocksCompletionUntilNextPageStarts() {
        MainFrameLoadGate gate = new MainFrameLoadGate();

        gate.onPageStarted(DISPLAY_URL);
        assertTrue(gate.isSuccessfulCompletion(DISPLAY_URL));

        gate.onMainFrameLoadFailed();
        assertFalse(gate.isSuccessfulCompletion(DISPLAY_URL));

        gate.onPageStarted(DISPLAY_URL);
        assertTrue(gate.isSuccessfulCompletion(DISPLAY_URL));
    }

    @Test public void legacyErrorOnlyMatchesActiveOrCurrentMainFrame() {
        MainFrameLoadGate gate = new MainFrameLoadGate();
        gate.onPageStarted(DISPLAY_URL);

        assertFalse(gate.isLikelyLegacyMainFrameError(
            "https://btcpay.example/assets/scanner.js", DISPLAY_URL));
        assertTrue(gate.isLikelyLegacyMainFrameError(DISPLAY_URL, DISPLAY_URL));
        assertTrue(gate.isLikelyLegacyMainFrameError(
            DISPLAY_URL, "https://btcpay.example/intermediate"));
        assertFalse(gate.isLikelyLegacyMainFrameError(null, DISPLAY_URL));
    }

    @Test public void onlyHttpErrorStatusesAreFailures() {
        assertFalse(MainFrameLoadGate.isHttpFailure(200));
        assertFalse(MainFrameLoadGate.isHttpFailure(399));
        assertTrue(MainFrameLoadGate.isHttpFailure(400));
        assertTrue(MainFrameLoadGate.isHttpFailure(404));
        assertTrue(MainFrameLoadGate.isHttpFailure(500));
        assertTrue(MainFrameLoadGate.isHttpFailure(599));
        assertFalse(MainFrameLoadGate.isHttpFailure(600));
    }

    @Test public void staleOrMissingCompletionCannotOpenGate() {
        MainFrameLoadGate gate = new MainFrameLoadGate();
        gate.onPageStarted(DISPLAY_URL);

        assertFalse(gate.isSuccessfulCompletion(null));
        assertFalse(gate.isSuccessfulCompletion(DISPLAY_URL + "?stale=true"));
    }
}
