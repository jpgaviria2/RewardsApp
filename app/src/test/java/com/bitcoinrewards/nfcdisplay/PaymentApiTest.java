package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class PaymentApiTest {
    static final String ORIGIN = "https://merchant.example";
    static final String ID = "cd829d5a-7652-4f2a-bcff-698132447685";
    static final class Connection extends HttpURLConnection {
        int code = 200;
        String response = "{\"available\":true,\"currency\":\"CAD\",\"version\":1}";
        boolean closed;
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        Connection() throws Exception { super(new URL(ORIGIN)); }
        @Override public void connect() { }
        @Override public void disconnect() { closed = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return code; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getOutputStream() { return sent; }
    }
    PaymentApi api(Connection connection) {
        return new PaymentApi(ORIGIN, "Store1", url -> {
            assertTrue(url.startsWith(ORIGIN + "/plugins/bitcoin-rewards/Store1/pos/api"));
            return connection;
        }, url -> "session=test-only");
    }
    @Test public void authenticatedExactOriginBoundedNonredirectRequest() throws Exception {
        Connection c = new Connection();
        api(c).request("POST", "/invoices", new JSONObject().put("operationId", ID).put("amountMinor", 1234));
        assertFalse(c.getInstanceFollowRedirects());
        assertEquals(10_000, c.getConnectTimeout());
        assertEquals(15_000, c.getReadTimeout());
        assertEquals(ORIGIN, c.getRequestProperty("Origin"));
        assertEquals("session=test-only", c.getRequestProperty("Cookie"));
        assertEquals("application/json", c.getRequestProperty("Content-Type"));
        assertFalse(c.getUseCaches());
        assertEquals(1234, new JSONObject(c.sent.toString("UTF-8")).getInt("amountMinor"));
        assertTrue(c.closed);
    }
    @Test public void redirectsAndAuthFailuresNeverFollowOrSucceed() throws Exception {
        for (int code : new int[]{302, 303, 307, 308, 401, 403, 404, 500}) {
            Connection c = new Connection(); c.code = code;
            try { api(c).request("GET", "/capability", null); fail(); }
            catch (PaymentApi.Failure failure) { assertEquals(code, failure.code); }
            assertFalse(c.getInstanceFollowRedirects()); assertTrue(c.closed);
        }
        assertTrue(new PaymentApi.Failure(404).guidance().contains("plugin"));
        assertTrue(new PaymentApi.Failure(403).guidance().contains("authorized"));
    }
    @Test public void cookiesCannotBeSentToForeignPathsOrMissingSession() throws Exception {
        Connection c = new Connection();
        for (String path : new String[]{"https://evil.example", "//evil.example", "/invoices/../login", "/invoices/"+ID+"?x=1", "/invoices/"+ID+"/reward"}) {
            try { api(c).request("GET", path, null); fail(path); } catch (IllegalArgumentException expected) { }
        }
        PaymentApi missing = new PaymentApi(ORIGIN, "Store1", url -> { fail("Must not connect without session"); return c; }, url -> null);
        try { missing.request("GET", "/capability", null); fail(); } catch (PaymentApi.Failure expected) { assertEquals(401, expected.code); }
    }
    @Test public void cancelRaceAlwaysReadsServerSettlementAndNeverInfersCancelled() throws Exception {
        Connection cancel = new Connection(); cancel.code = 409;
        Connection read = new Connection(); read.response = "{\"status\":\"paid\"}";
        java.util.List<String> paths = new java.util.ArrayList<>();
        PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> {
            paths.add(url); return paths.size() == 1 ? cancel : read;
        }, url -> "session=test-only");
        assertEquals("paid", api.cancelAndRead(ID).getString("status"));
        assertEquals(2, paths.size()); assertTrue(paths.get(0).endsWith("/"+ID+"/cancel"));
        assertTrue(paths.get(1).endsWith("/"+ID));
        assertEquals("POST", cancel.getRequestMethod()); assertEquals("GET", read.getRequestMethod());
    }
    @Test public void cardAckAndCallbackErrorNeverEstablishPaidOrCauseReplay() throws Exception {
        for (int code : new int[]{200, 500}) {
            Connection tap = new Connection(); tap.code = code; tap.response = "{\"status\":\"OK\"}";
            Connection read = new Connection(); read.response = "{\"status\":\"review\"}";
            int[] calls = {0};
            PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> ++calls[0] == 1 ? tap : read, url -> "session=test-only");
            String card = "lnurlw://issuer.example/tap?p=" + "a".repeat(32) + "&c=" + "b".repeat(16);
            assertEquals("review", api.tapAndRead(ID, card).getString("status"));
            assertEquals(2, calls[0]); assertEquals("GET", read.getRequestMethod());
            assertEquals(card, new JSONObject(tap.sent.toString("UTF-8")).getString("boltcardUrl"));
        }
    }
    @Test public void malformedCardCannotReachCollectionTransport() throws Exception {
        PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> { fail("No transport for unsafe card"); return null; }, url -> "session=test-only");
        for (String card : new String[]{"https://issuer.example/tap", "lnurlw://issuer.example/?k1=secret", "lnurlw://issuer.example/tap?p=x&c=y"}) {
            try { api.tapAndRead(ID, card); fail(card); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void oversizedResponsesFailClosedAndDisconnect() throws Exception {
        Connection c = new Connection(); c.response = "x".repeat(32769);
        try { api(c).request("GET", "/capability", null); fail(); } catch (PaymentApi.Failure expected) { assertEquals(0, expected.code); }
        assertTrue(c.closed);
    }
}
