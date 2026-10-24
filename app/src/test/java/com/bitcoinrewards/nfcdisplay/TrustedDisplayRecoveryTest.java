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

        assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
        assertTrue(recovery.isRecovering());
        assertEquals(1500L, scheduler.last().delayMs);
        scheduler.runLast();

        assertEquals(Arrays.asList(DISPLAY), loads);
        assertTrue(recovery.isRecovering());
    }

    @Test public void repeatedFailuresUseLongBoundedBackoffAndAttemptLimit() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);
        long[] initialDelays = { 1500L, 3000L, 6000L, 12000L, 24000L, 30000L };

        for (long expectedDelay : initialDelays) {
            assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
            assertEquals(expectedDelay, scheduler.last().delayMs);
            scheduler.runLast();
            recovery.onPageStarted(DISPLAY);
        }
        for (int attempt = initialDelays.length; attempt < 30; attempt++) {
            assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
            assertEquals(30000L, scheduler.last().delayMs);
            scheduler.runLast();
            recovery.onPageStarted(DISPLAY);
        }

        assertFalse(recovery.onTransientMainFrameFailure(DISPLAY));
        assertEquals(30, loads.size());
        assertEquals(30, scheduler.entries.size());
    }

    @Test public void successfulCompletionCancelsPendingRetryAndResetsBackoff() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);

        assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
        FakeScheduler.Entry stale = scheduler.last();
        recovery.onSuccessfulCompletion(DISPLAY);
        assertTrue(stale.cancelled);
        assertFalse(recovery.isRecovering());
        stale.runEvenIfCancelled();
        assertTrue(loads.isEmpty());

        assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
        assertEquals(1500L, scheduler.last().delayMs);
    }

    @Test public void duplicateAndLifecycleStaleCallbacksCannotRetry() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);

        assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
        assertFalse(recovery.onTransientMainFrameFailure(DISPLAY));
        assertEquals(1, scheduler.entries.size());
        scheduler.runLast();
        assertEquals(Arrays.asList(DISPLAY), loads);
        assertFalse(recovery.onTransientMainFrameFailure(DISPLAY));

        recovery.onPageStarted(DISPLAY);
        assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
        FakeScheduler.Entry stale = scheduler.last();
        recovery.destroy();
        assertTrue(stale.cancelled);
        stale.runEvenIfCancelled();
        assertEquals(Arrays.asList(DISPLAY), loads);
        assertFalse(recovery.isRecovering());
        assertFalse(recovery.onTransientMainFrameFailure(DISPLAY));
    }

    @Test public void loginExchangeSecretIsNeverScheduledOrReplayed() {
        FakeScheduler scheduler = new FakeScheduler();
        List<String> loads = new ArrayList<>();
        TrustedDisplayRecovery recovery = recovery(scheduler, loads);

        assertFalse(recovery.onTransientMainFrameFailure(LOGIN));
        assertTrue(scheduler.entries.isEmpty());
        assertTrue(recovery.onTransientMainFrameFailure(DISPLAY));
        scheduler.runLast();

        assertEquals(Arrays.asList(DISPLAY), loads);
        assertFalse(loads.get(0).contains("LoginCode"));
        assertFalse(loads.get(0).contains("consume-once-secret"));
    }

    private static TrustedDisplayRecovery recovery(FakeScheduler scheduler, List<String> loads) {
        return new TrustedDisplayRecovery(DISPLAY, 30, 1500L, 30000L, scheduler, () -> loads.add(DISPLAY));
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
