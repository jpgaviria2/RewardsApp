package com.bitcoinrewards.nfcdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

/** Non-secret, exact server check-in identity, scoped to the authenticated request target. */
final class CollectionCheckInStore {
    private final SharedPreferences prefs;
    CollectionCheckInStore(Context context) { this(context.getSharedPreferences("collection_checkin", Context.MODE_PRIVATE)); }
    CollectionCheckInStore(SharedPreferences prefs) { this.prefs = prefs; }
    static boolean valid(String id) {
        try { return id != null && id.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
            && !UUID.fromString(id).equals(new UUID(0, 0)); } catch (Exception e) { return false; }
    }
    static String successId(InputStream input) throws Exception {
        byte[] body = readBody(input);
        if (body.length == 0) return null; // Old plugin: no transfer support.
        JSONObject json = PaymentContract.response(new String(body, StandardCharsets.UTF_8));
        Object id = json.opt("checkInId");
        return id instanceof String && valid((String) id) ? (String) id : null;
    }
    static byte[] readBody(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048]; int n;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 32768 || System.nanoTime() > deadline) throw new IllegalArgumentException();
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
    boolean capture(String origin, String store, String id) {
        String canonical = ApiOrigin.requireConfigured(origin).toString();
        PendingOperation.validateText(store, 256);
        if (!valid(id)) return prefs.edit().clear().commit();
        return prefs.edit().putString("origin", canonical).putString("store", store).putString("id", id).commit();
    }
    String matching(String origin, String store) {
        try {
            String id = prefs.getString("id", null);
            return valid(id) && ApiOrigin.requireConfigured(origin).toString().equals(prefs.getString("origin", ""))
                && store.equals(prefs.getString("store", "")) ? id : null;
        } catch (Exception e) { return null; }
    }
    boolean release(String origin, String store, String id) {
        if (id == null || !id.equals(matching(origin, store))) return true;
        if (prefs.edit().clear().commit()) return true;
        // Failed commit is not an in-memory rollback. Retain the capture so the
        // next validated GET retries durable release before staff can Finish.
        prefs.edit().putString("origin", ApiOrigin.requireConfigured(origin).toString())
            .putString("store", store).putString("id", id).commit();
        return false;
    }
}
