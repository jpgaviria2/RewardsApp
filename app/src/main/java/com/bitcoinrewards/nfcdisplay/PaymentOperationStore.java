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
    boolean bindInvoice(String id) {
        String previous = invoiceId();
        return exists() && (previous == null ? prefs.edit().putString("invoice_id", id).commit() : previous.equals(id));
    }
    boolean tapAttempted() { return prefs.getBoolean("tap_attempted", false); }
    boolean begin(String origin, String store, long amount) {
        if (exists() || amount <= 0 || amount > PaymentContract.MAX_MINOR) return false;
        return prefs.edit().putString("operation", UUID.randomUUID().toString())
            .putLong("amount", amount).putString("origin", origin).putString("store", store)
            .putBoolean("tap_attempted", false).commit();
    }
    boolean markTap() { return exists() && !tapAttempted() && prefs.edit().putBoolean("tap_attempted", true).commit(); }
    boolean clearTerminal(String status) { return PaymentContract.terminal(status) && prefs.edit().clear().commit(); }
}
