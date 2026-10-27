package com.bitcoinrewards.nfcdisplay;

/** Consume-once in-process handoff for a non-secret Store QR payload. */
final class OneTimeStoreHandoff {
    private static String pending;

    static synchronized void publish(String payload) {
        pending = payload;
    }

    static synchronized String consume() {
        String value = pending;
        pending = null;
        return value;
    }

    static synchronized void clear() {
        pending = null;
    }

    private OneTimeStoreHandoff() { }
}
