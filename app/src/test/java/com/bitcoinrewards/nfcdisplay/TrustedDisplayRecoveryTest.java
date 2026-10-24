package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrustedDisplayRecoveryTest {
    private static final String DISPLAY = "https://btcpay.example/plugins/bitcoin-rewards/store/display";
    private static final String LOGIN = "https://btcpay.example/login?LoginCode=consume-once-secret&returnUrl=%2Fplugins%2Fbitcoin-rewards%2Fstore%2Fdisplay";

    @Test public void transientFailureRetriesExactTrustedDisplay() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);

        assertTrue(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        assertEquals(1000L, scheduler.last().delayMs);
        scheduler.runLast();

        assertEquals(Arrays.asList(DISPLAY), loads);
    }

    @Test public void repeatedFailuresUseBoundedBackoffAndAttemptLimit() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);
        long[] expectedDelays = { 1000L, 2000L, 4000L, 8000L, 8000L };

        for (long expectedDelay : expectedDelays) {
            assertTrue(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
            assertEquals(expectedDelay, scheduler.last().delayMs);
            scheduler.runLast();
            recovery.onPageStarted(DISPLAY);
        }

        assertFalse(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        assertEquals(5, loads.size());
        assertEquals(5, scheduler.entries.size());
    }

    @Test public void successfulCompletionCancelsPendingRetryAndResetsBackoff() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);

        assertTrue(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        FakeScheduler.Entry stale = scheduler.last();
        recovery.onSuccessfulCompletion(DISPLAY);
        assertTrue(stale.cancelled);
        stale.runEvenIfCancelled();
        assertTrue(loads.isEmpty());

        assertTrue(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        assertEquals(1000L, scheduler.last().delayMs);
    }

    @Test public void duplicateAndLifecycleStaleCallbacksCannotRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);

        assertTrue(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        assertFalse(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        assertEquals(1, scheduler.entries.size());
        scheduler.runLast();
        assertEquals(Arrays.asList(DISPLAY), loads);
        assertFalse(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));

        recovery.onPageStarted(DISPLAY);
        assertTrue(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        FakeScheduler.Entry stale = scheduler.last();
        recovery.destroy();
        assertTrue(stale.cancelled);
        stale.runEvenIfCancelled();
        assertEquals(Arrays.asList(DISPLAY), loads);
        assertFalse(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
    }

    @Test public void loginExchangeSecretIsNeverScheduledOrReplayed() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);

        assertFalse(recovery.onMainFrameNetworkOrTlsFailure(LOGIN));
        assertTrue(scheduler.entries.isEmpty());
        assertTrue(recovery.onMainFrameNetworkOrTlsFailure(DISPLAY));
        scheduler.runLast();

        assertEquals(Arrays.asList(DISPLAY), loads);
        assertFalse(loads.get(0).contains("LoginCode"));
        assertFalse(loads.get(0).contains("consume-once-secret"));
    }

    private static TrustedDisplayRecovery recovery(FakeScheduler scheduler, List<String> loads) {
        return new TrustedDisplayRecovery(DISPLAY, 5, 1000L, 8000L, scheduler, () -> loads.add(DISPLAY));
    }

    private static final class FakeScheduler implements TrustedDisplayRecovery.Scheduler {
        final List<Entry> entries = new ArrayList<>();

        @Override public TrustedDisplayRecovery.Cancellable schedule(Runnable runnable, long delayMs) {
            Entry entry = new Entry(runnable, delayMs);
            entries.add(entry);
            return () -> entry.cancelled = true;
        }

        Entry last() {
            return entries.get(entries.size() - 1);
        }

        void runLast() {
            last().run();
        }

        static final class Entry {
            final Runnable runnable;
            final long delayMs;
            boolean cancelled;

            Entry(Runnable runnable, long delayMs) {
                this.runnable = runnable;
                this.delayMs = delayMs;
            }

            void run() {
                if (!cancelled) runnable.run();
            }

            void runEvenIfCancelled() {
                runnable.run();
            }
        }
    }
}