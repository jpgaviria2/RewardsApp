package com.bitcoinrewards.nfcdisplay;

/** Consume-once process-memory handoff from Settings to the display WebView. */
final class OneTimeDisplayLoginHandoff {
    static final class Request {
        final String loginUrl;
        final String pendingOrigin;
        final String pendingStoreId;

        Request(String loginUrl, String pendingOrigin, String pendingStoreId) {
            this.loginUrl = loginUrl;
            this.pendingOrigin = pendingOrigin;
            this.pendingStoreId = pendingStoreId;
        }

        boolean hasPendingConfiguration() {
            return pendingOrigin != null && pendingStoreId != null;
        }
    }

    private static Request pending;

    static synchronized void publish(String loginUrl) {
        requireLoginUrl(loginUrl);
        pending = new Request(loginUrl, null, null);
    }

    static synchronized void publishWithPendingConfiguration(String loginUrl, String origin, String storeId) {
        requireLoginUrl(loginUrl);
        String normalizedOrigin = ApiOrigin.requireConfigured(origin).toString();
        PendingOperation.validateText(storeId, 256);
        pending = new Request(loginUrl, normalizedOrigin, storeId);
    }

    static synchronized Request consume() {
        Request value = pending;
        pending = null;
        return value;
    }

    static synchronized void clear() {
        pending = null;
    }

    private static void requireLoginUrl(String loginUrl) {
        if (loginUrl == null || loginUrl.isEmpty()) throw new IllegalArgumentException("Missing login URL");
    }

    private OneTimeDisplayLoginHandoff() { }
}
