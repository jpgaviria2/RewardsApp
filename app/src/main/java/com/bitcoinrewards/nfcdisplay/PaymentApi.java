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
    final String origin, base, store;
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
        this.store = store;
        base = origin + "/plugins/bitcoin-rewards/" + PathSegmentCodec.encode(store) + "/pos/api";
    }
    static final class Failure extends Exception {
        final int code;
        boolean staleCheckIn, captureReleased;
        Failure(int code) { this.code = code; }
        String guidance() {
            if (code == 404) return "Collection unavailable. Update the Bitcoin Rewards plugin and verify the Store ID. An existing operation must be reconciled by staff, not replaced.";
            if (code == 403) return "Collection denied. Log in with an account authorized for this store.";
            if (staleCheckIn) return "The expected customer check-in is stale or no longer eligible. "
                + (captureReleased ? "Its captured reference was released. " : "Its captured reference could not be released. ")
                + "Verify the current customer in BTCPay. Reconcile this operation with staff before proceeding; do not create a replacement charge or cancel all check-ins.";
            if (code == 400 || code == 409) return "Collection request rejected. Verify the current customer and operation in BTCPay; reconcile before proceeding. Do not create a replacement charge.";
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
    JSONObject reconcileAndRead(String operationId, String reason) throws Exception {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 500) throw new IllegalArgumentException();
        return mutateAndRead(operationId, "/reconcile", new JSONObject().put("reason", reason));
    }
    JSONObject reconcileAndRead(PaymentOperationStore operation, String reason) throws Exception {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 500) throw new IllegalArgumentException();
        if (!operation.exists() || !origin.equals(operation.origin()) || !store.equals(operation.store())) throw new IllegalArgumentException();
        // Use the persisted create identity, including after restart/404. Only the server
        // can fence an unadmitted UUID; neither the POST ACK nor absence permits reset.
        String operationId = operation.id();
        long amount = operation.amount();
        String expected = operation.expectedCheckInId();
        if (amount <= 0 || amount > PaymentContract.MAX_MINOR
                || (expected != null && !CollectionCheckInStore.valid(expected))) throw new IllegalArgumentException();
        JSONObject body = new JSONObject().put("reason", reason).put("amountMinor", amount);
        if (expected != null) body.put("expectedCheckInId", expected);
        return mutateAndRead(operationId, "/reconcile", body);
    }
    JSONObject create(PaymentOperationStore operation) throws Exception {
        if (!operation.exists() || !origin.equals(operation.origin()) || !store.equals(operation.store())) throw new IllegalArgumentException();
        JSONObject body = new JSONObject().put("operationId", operation.id()).put("amountMinor", operation.amount());
        String expected = operation.expectedCheckInId();
        if (CollectionCheckInStore.valid(expected)) body.put("expectedCheckInId", expected);
        return request("POST", "/invoices", body);
    }
    private JSONObject mutateAndRead(String operationId, String action, JSONObject body) throws Exception {
        String path = "/invoices/" + operationId;
        try { request("POST", path + action, body); }
        catch (Exception ignored) { /* A lost ACK, timeout, or cancel race must still reconcile. Never replay. */ }
        return request("GET", path, null);
    }
    JSONObject request(String method, String suffix, JSONObject body) throws Exception {
        if (!suffix.matches("(?:/capability|/invoices(?:/[0-9a-fA-F-]{36}(?:/(?:tap|cancel|reconcile))?)?)")) throw new IllegalArgumentException();
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
            if (code < 200 || code >= 300) {
                Failure failure = new Failure(code);
                if ((code == 400 || code == 409) && c.getContentLength() <= 32768) {
                    try {
                        JSONObject error = readResponse(c.getErrorStream());
                        failure.staleCheckIn = "Expected check-in is not the store's unchanged pending capture.".equals(error.opt("error"));
                    } catch (Exception ignored) { /* Untrusted/ambiguous rejection never releases capture. */ }
                }
                throw failure;
            }
            if (c.getContentLength() > 32768) throw new Failure(0);
            return readResponse(c.getInputStream());
        } finally { c.disconnect(); }
    }
    private static JSONObject readResponse(InputStream stream) throws Exception {
            if (stream == null) throw new Failure(0);
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int length;
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                while ((length = input.read(buffer)) != -1) {
                    if (System.nanoTime() > deadline || output.size() + length > 32768) throw new Failure(0);
                    output.write(buffer, 0, length);
                }
                return PaymentContract.response(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
    }
}
