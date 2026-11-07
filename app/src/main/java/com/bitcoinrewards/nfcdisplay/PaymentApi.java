package com.bitcoinrewards.nfcdisplay;

import android.webkit.CookieManager;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** No redirects, issuer requests, persisted cookies, or response/error logging. */
final class PaymentApi {
    final String origin, base;
    interface Connections { HttpURLConnection open(String url) throws Exception; }
    private final Connections connections;
    private final InlineCheckInAuthentication.CookieLookup cookies;
    PaymentApi(String configuredOrigin, String store) {
        this(configuredOrigin, store, url -> (HttpURLConnection) new URL(url).openConnection(),
            url -> CookieManager.getInstance().getCookie(url));
    }
    PaymentApi(String configuredOrigin, String store, Connections connections,
               InlineCheckInAuthentication.CookieLookup cookies) {
        this.connections = connections;
        this.cookies = cookies;
        origin = ApiOrigin.requireConfigured(configuredOrigin).toString();
        PendingOperation.validateText(store, 256);
        base = origin + "/plugins/bitcoin-rewards/" + PathSegmentCodec.encode(store) + "/pos/api";
    }
    static final class Failure extends Exception {
        final int code;
        Failure(int code) { this.code = code; }
        String guidance() {
            if (code == 404) return "Collection unavailable. Update the Bitcoin Rewards plugin and verify the Store ID. An existing operation must be reconciled by staff, not replaced.";
            if (code == 403) return "Collection denied. Log in with an account authorized for this store.";
            if (code == 401 || (code >= 300 && code < 400)) return "BTCPay login required. Reauthenticate in Settings; do not start a replacement charge.";
            return "Could not reconcile payment. Check connection and refresh status; do not charge again.";
        }
    }
    JSONObject tapAndRead(String operationId, String boltcardUrl) throws Exception {
        String card = BoltcardPayloadParser.requireBoltcard(boltcardUrl);
        return mutateAndRead(operationId, "/tap", new JSONObject().put("boltcardUrl", card));
    }
    JSONObject cancelAndRead(String operationId) throws Exception {
        return mutateAndRead(operationId, "/cancel", new JSONObject());
    }
    private JSONObject mutateAndRead(String operationId, String action, JSONObject body) throws Exception {
        String path = "/invoices/" + operationId;
        try { request("POST", path + action, body); }
        catch (Exception ignored) { /* A lost ACK, timeout, or cancel race must still reconcile. Never replay. */ }
        return request("GET", path, null);
    }
    JSONObject request(String method, String suffix, JSONObject body) throws Exception {
        if (!suffix.matches("(?:/capability|/invoices(?:/[0-9a-fA-F-]{36}(?:/(?:tap|cancel))?)?)")) throw new IllegalArgumentException();
        String url = base + suffix;
        String cookie = InlineCheckInAuthentication.cookieForTrustedApi(origin, url, cookies);
        if (cookie == null) throw new Failure(401);
        HttpURLConnection c = connections.open(url);
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setUseCaches(false);
            c.setRequestMethod(method);
            c.setRequestProperty("Origin", origin);
            c.setRequestProperty("Cookie", cookie);
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 16384) throw new IllegalArgumentException();
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(bytes.length);
                c.setDoOutput(true);
                try (java.io.OutputStream output = c.getOutputStream()) { output.write(bytes); }
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Failure(code);
            if (c.getContentLength() > 32768) throw new Failure(0);
            try (InputStream input = c.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int length;
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                while ((length = input.read(buffer)) != -1) {
                    if (System.nanoTime() > deadline || output.size() + length > 32768) throw new Failure(0);
                    output.write(buffer, 0, length);
                }
                return PaymentContract.response(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { c.disconnect(); }
    }
}
