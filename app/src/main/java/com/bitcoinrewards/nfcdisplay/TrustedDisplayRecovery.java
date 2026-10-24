package com.bitcoinrewards.nfcdisplay;

final class TrustedDisplayRecovery {
    interface Cancellable {
        void cancel();
    }

    interface Scheduler {
        Cancellable schedule(Runnable runnable, long delayMs);
    }

    interface RetryTarget {
        void loadTrustedDisplay();
    }

    private final String trustedDisplayUrl;
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final Scheduler scheduler;
    private final RetryTarget retryTarget;

    private Cancellable pendingRetry;
    private int attempts;
    private long callbackGeneration;
    private boolean retryLoadInFlight;
    private boolean active = true;

    TrustedDisplayRecovery(String trustedDisplayUrl, int maxAttempts, long initialDelayMs,
                           long maxDelayMs, Scheduler scheduler, RetryTarget retryTarget) {
        if (trustedDisplayUrl == null || trustedDisplayUrl.isEmpty()) throw new IllegalArgumentException("trusted display URL required");
        if (maxAttempts < 1 || initialDelayMs < 0 || maxDelayMs < initialDelayMs) throw new IllegalArgumentException("invalid retry policy");
        this.trustedDisplayUrl = trustedDisplayUrl;
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.scheduler = scheduler;
        this.retryTarget = retryTarget;
    }

    boolean onMainFrameNetworkOrTlsFailure(String failingUrl) {
        if (!active || !trustedDisplayUrl.equals(failingUrl) || pendingRetry != null
            || retryLoadInFlight || attempts >= maxAttempts) return false;

        long delayMs = delayForAttempt(attempts);
        attempts++;
        final long generation = ++callbackGeneration;
        pendingRetry = scheduler.schedule(() -> {
            if (!active || generation != callbackGeneration || pendingRetry == null) return;
            pendingRetry = null;
            retryLoadInFlight = true;
            retryTarget.loadTrustedDisplay();
        }, delayMs);
        return true;
    }

    void onPageStarted(String url) {
        if (!trustedDisplayUrl.equals(url)) return;
        cancelPendingRetry();
        retryLoadInFlight = false;
    }

    void onSuccessfulCompletion(String url) {
        if (!trustedDisplayUrl.equals(url)) return;
        cancelPendingRetry();
        retryLoadInFlight = false;
        attempts = 0;
    }

    void destroy() {
        active = false;
        cancelPendingRetry();
        retryLoadInFlight = false;
    }

    private long delayForAttempt(int attempt) {
        long delay = initialDelayMs;
        for (int i = 0; i < attempt && delay < maxDelayMs; i++) {
            delay = Math.min(maxDelayMs, delay > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : delay * 2);
        }
        return Math.min(delay, maxDelayMs);
    }

    private void cancelPendingRetry() {
        callbackGeneration++;
        if (pendingRetry != null) pendingRetry.cancel();
        pendingRetry = null;
    }
}