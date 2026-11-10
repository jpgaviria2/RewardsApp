package com.bitcoinrewards.nfcdisplay;

import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Presentation-only evidence. Never controls financial capture/transfer authority. */
final class ConfirmedCheckInResponse {
    /** User-selected display semantics: local handle, not a human name or NIP-05 proof. */
    static String confirmedHandle(byte[] body) {
        if (confirmedId(body) == null) return "";
        try {
            String address = (String) PaymentContract.response(new String(body, StandardCharsets.UTF_8)).opt("lightningAddress");
            int at = address.indexOf('@');
            if (at <= 0 || at != address.lastIndexOf('@')
                || !"trailscoffee.com".equals(address.substring(at + 1).toLowerCase(java.util.Locale.ROOT))) return "";
            String handle = address.substring(0, at);
            // ASCII identifier only: no whitespace, controls, bidi, markup or quoted mailbox.
            // Preserve the server's handle spelling; never trim or repair malformed input.
            if (!handle.matches("[A-Za-z0-9_.-]{1,64}") || handle.startsWith(".")
                || handle.endsWith(".") || handle.contains("..")) return "";
            return handle;
        } catch (Exception invalid) {
            return "";
        }
    }

    static String confirmedId(byte[] body) {
        if (body == null || body.length == 0 || body.length > 32768) return null;
        try {
            JSONObject json = PaymentContract.response(new String(body, StandardCharsets.UTF_8));
            // Pinned wallet and Boltcard creation routes return this five-field projection.
            // Pending is the stored purchase state; checked_in means admission committed.
            // Unknown extensions remain presentation-only failures, never capture failures.
            if (json.length() != 5 || !"checked_in".equals(json.opt("status"))
                || !(json.opt("expiresAt") instanceof String)
                || ((String) json.opt("expiresAt")).isEmpty()
                || !(json.opt("lightningAddress") instanceof String)
                || !(json.opt("testRewardProcessed") instanceof Boolean)) return null;
            Object id = json.opt("checkInId");
            return id instanceof String && CollectionCheckInStore.valid((String) id) ? (String) id : null;
        } catch (Exception invalid) {
            return null;
        }
    }
}
