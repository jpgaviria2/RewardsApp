package com.bitcoinrewards.nfcdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** Only non-secret identity/dispatch guards. Never store invoice, card or session data. */
final class PaymentOperationStore {
    private final SharedPreferences prefs;
    PaymentOperationStore(Context context) { this(context.getSharedPreferences("collection_identity", Context.MODE_PRIVATE)); }
    PaymentOperationStore(SharedPreferences preferences) { prefs = preferences; }
    boolean exists() { return prefs.contains("operation"); }
    String id() { return prefs.getString("operation", ""); }
    long amount() { return prefs.getLong("amount", 0); }
    String origin() { return prefs.getString("origin", ""); }
    String store() { return prefs.getString("store", ""); }
    String invoiceId() { return prefs.getString("invoice_id", null); }
    String expectedCheckInId() { return prefs.getString("expected_checkin", null); }
    boolean bindInvoice(String id) {
        if (!exists() || id == null || id.isEmpty()) return false;
        String previous = invoiceId();
        if (previous != null) return previous.equals(id);
        boolean committed = prefs.edit().putString("invoice_id", id).commit();
        if (!committed) {
            // A failed Android commit still changes memory. Restore the unbound
            // state so matching responses cannot bypass the required disk retry.
            prefs.edit().remove("invoice_id").commit();
        }
        return committed;
    }
    boolean tapAttempted() { return prefs.getBoolean("tap_attempted", false); }
    boolean begin(String origin, String store, long amount) {
        return begin(origin, store, amount, null);
    }
    boolean begin(String origin, String store, long amount, String expectedCheckInId) {
        if (exists() || amount <= 0 || amount > PaymentContract.MAX_MINOR) return false;
        origin = ApiOrigin.requireConfigured(origin).toString();
        PendingOperation.validateText(store, 256);
        if (expectedCheckInId != null && !CollectionCheckInStore.valid(expectedCheckInId)) return false;
        return prefs.edit().putString("operation", UUID.randomUUID().toString())
            .putLong("amount", amount).putString("origin", origin).putString("store", store)
            .putString("expected_checkin", expectedCheckInId).putString("invoice_id", null)
            .putBoolean("tap_attempted", false).commit();
    }
    boolean markTap() { return exists() && !tapAttempted() && prefs.edit().putBoolean("tap_attempted", true).commit(); }
    org.json.JSONArray history() {
        try { return new org.json.JSONArray(prefs.getString("history", "[]")); }
        catch (Exception e) { throw new IllegalStateException("Payment history unavailable", e); }
    }
    boolean clearTerminal(String status) {
        if (!exists() || !PaymentContract.terminal(status)) return false;
        try {
            final String activeId = id(), activeOrigin = origin(), activeStore = store();
            final String activeInvoice = invoiceId(), activeCheckIn = expectedCheckInId();
            final long activeAmount = amount();
            final boolean activeTap = tapAttempted();
            org.json.JSONArray history = history();
            org.json.JSONArray updated = new org.json.JSONArray();
            for (int i = 0; i < history.length(); i++) {
                org.json.JSONObject item = history.getJSONObject(i);
                if (!id().equals(item.getString("operation"))) updated.put(item);
            }
            updated.put(new org.json.JSONObject().put("operation", id()).put("origin", origin()).put("store", store())
                .put("amount", amount()).put("invoice_id", invoiceId()).put("status", status).put("tap_attempted", tapAttempted()));
            boolean committed = prefs.edit().remove("operation").remove("amount").remove("origin").remove("store")
                .remove("invoice_id").remove("expected_checkin").remove("tap_attempted")
                .putString("history", updated.toString()).commit();
            if (!committed) {
                // Android applies failed commits to memory too. Restore the lock in memory
                // even if disk remains unwritable; disk still has the previous active row.
                prefs.edit().putString("operation", activeId).putString("origin", activeOrigin).putString("store", activeStore)
                    .putLong("amount", activeAmount).putString("invoice_id", activeInvoice)
                    .putString("expected_checkin", activeCheckIn).putBoolean("tap_attempted", activeTap).commit();
            }
            return committed;
        } catch (Exception e) { return false; }
    }
    boolean recoverHistory(int index) {
        if (exists()) return false;
        try {
            org.json.JSONObject item = history().getJSONObject(index);
            return prefs.edit().putString("operation", item.getString("operation"))
                .putString("origin", item.getString("origin")).putString("store", item.getString("store"))
                .putLong("amount", item.getLong("amount")).putString("invoice_id", item.optString("invoice_id", null))
                .putString("expected_checkin", null).putBoolean("tap_attempted", item.getBoolean("tap_attempted")).commit();
        } catch (Exception e) { return false; }
    }
}
