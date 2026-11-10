package com.bitcoinrewards.nfcdisplay;

import java.net.URI;

/** Non-secret, profile-bound native BTCPay POS entry point. */
final class PosUrlPolicy {
    static String forProfileSave(String oldOrigin, String oldStore, String oldPos,
                                 String origin, String store, String requested) {
        boolean changed = !origin.equals(oldOrigin) || !store.equals(oldStore);
        // Never silently associate an inherited app URL with a different store.
        if (changed && requested.equals(oldPos)) return "";
        return requested.isEmpty() ? "" : require(origin, requested);
    }
    static String require(String origin, String value) {
        try {
            URI uri = new URI(value);
            if (!ApiOrigin.sameOrigin(origin, value) || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !uri.getRawPath().matches("/apps/[A-Za-z0-9]{1,128}/pos")) throw new IllegalArgumentException();
            return ApiOrigin.requireConfigured(origin).toString() + uri.getRawPath();
        } catch (Exception e) { throw new IllegalArgumentException("Enter a same-server HTTPS BTCPay /apps/APP_ID/pos URL."); }
    }
    private PosUrlPolicy() { }
}
