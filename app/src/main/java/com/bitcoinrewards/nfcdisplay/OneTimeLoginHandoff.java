package com.bitcoinrewards.nfcdisplay;

/** Process-local, consume-once handoff for a secret-bearing login URL. */
final class OneTimeLoginHandoff {
    private static String pending;

    static synchronized void publish(String loginUrl) {
        if (loginUrl == null || loginUrl.isEmpty()) throw new IllegalArgumentException("Missing login URL");
        pending = loginUrl;
    }

    static synchronized String consume() {
        String value = pending;
        pending = null;
        return value;
    }

    static synchronized void clear() {
        pending = null;
    }

    private OneTimeLoginHandoff() { }
}
