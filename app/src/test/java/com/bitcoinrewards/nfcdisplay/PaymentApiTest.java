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
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)); }
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
    @Test public void createUsesDurableScopedCheckInOnlyAndLegacyOmitsIt() throws Exception {
        for (String expected : new String[]{ID, null}) {
            PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
            assertTrue(memory.open().begin(ORIGIN, "Store1", 100, expected));
            Connection c = new Connection(); api(c).create(memory.open());
            JSONObject body = new JSONObject(c.sent.toString("UTF-8"));
            assertEquals(memory.open().id(), body.getString("operationId"));
            assertEquals(expected != null, body.has("expectedCheckInId"));
            if (expected != null) assertEquals(expected, body.getString("expectedCheckInId"));
            PaymentApi other = new PaymentApi("https://other.example", "Store1", url -> { fail(); return c; }, url -> "session=test-only");
            try { other.create(memory.open()); fail(); } catch (IllegalArgumentException correct) { }
            other = new PaymentApi(ORIGIN, "Store2", url -> { fail(); return c; }, url -> "session=test-only");
            try { other.create(memory.open()); fail(); } catch (IllegalArgumentException correct) { }
        }
    }
    @Test public void reconcileAlwaysPostsThenReadsEvenWhenAckIsLostOrDenied() throws Exception {
        for (int code : new int[]{200, 401, 403, 409, 500}) {
            Connection post = new Connection(); post.code = code; post.response = "{\"status\":\"cancelled\"}";
            Connection read = new Connection(); read.response = "{\"status\":\"review\"}";
            java.util.List<String> paths = new java.util.ArrayList<>();
            PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> { paths.add(url); return paths.size() == 1 ? post : read; }, url -> "session=test-only");
            PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
            assertTrue(memory.open().begin(ORIGIN, "Store1", 1234, ID));
            String operationId = memory.open().id();
            assertEquals("review", api.reconcileAndRead(memory.open(), "Investigate interrupted collection").getString("status"));
            JSONObject body = new JSONObject(post.sent.toString("UTF-8"));
            assertEquals("Investigate interrupted collection", body.getString("reason"));
            assertEquals(1234, body.getLong("amountMinor")); assertEquals(ID, body.getString("expectedCheckInId"));
            assertEquals(2, paths.size()); assertTrue(paths.get(0).endsWith("/"+operationId+"/reconcile"));
            assertTrue(paths.get(1).endsWith("/"+operationId)); assertEquals("POST", post.getRequestMethod());
            assertEquals("GET", read.getRequestMethod()); assertFalse(post.getInstanceFollowRedirects());
        }
    }
    private JSONObject cancelled(String operation, long amount) throws Exception {
        return new JSONObject().put("operationId", operation).put("amountMinor", amount)
            .put("currency", "CAD").put("status", "cancelled").put("invoiceId", JSONObject.NULL)
            .put("bolt11", JSONObject.NULL).put("expiresAt", JSONObject.NULL);
    }
    @Test public void unknownReadThenScopedReconcileSendsOriginalIdentityAndValidatesTerminalGet() throws Exception {
        for (String expected : new String[]{ID.toUpperCase(java.util.Locale.ROOT), null}) {
            PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
            assertTrue(memory.open().begin(ORIGIN, "Store1", 1234, expected));
            String operationId = memory.open().id();
            // Transport fixtures only: durable fencing/delayed-create exclusion belong to backend tests.
            Connection unknown = new Connection(); unknown.code = 404; unknown.response = "{\"noOperation\":true}";
            Connection post = new Connection(); post.response = cancelled(operationId, 1234).toString();
            Connection read = new Connection(); read.response = cancelled(operationId, 1234).toString();
            Connection[] replies = {unknown, post, read};
            java.util.List<String> paths = new java.util.ArrayList<>();
            PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> {
                paths.add(url); return replies[paths.size() - 1];
            }, url -> "session=test-only");
            try { api.request("GET", "/invoices/" + operationId, null); fail(); }
            catch (PaymentApi.Failure failure) { assertEquals(404, failure.code); }
            assertTrue(memory.open().exists()); assertEquals(operationId, memory.open().id());
            assertEquals(expected, memory.open().expectedCheckInId());
            // Reopening preferences models Activity/process recovery; no create or client reset.
            PaymentOperationStore recovered = memory.open();
            JSONObject result = api.reconcileAndRead(recovered, "Operator confirmed abandonment");
            JSONObject body = new JSONObject(post.sent.toString("UTF-8"));
            assertEquals("Operator confirmed abandonment", body.getString("reason"));
            assertEquals(1234, body.getLong("amountMinor"));
            assertEquals(expected != null, body.has("expectedCheckInId"));
            if (expected != null) assertEquals(expected, body.getString("expectedCheckInId"));
            assertEquals(expected == null ? 2 : 3, body.length());
            assertEquals(3, paths.size());
            String path = ORIGIN + "/plugins/bitcoin-rewards/Store1/pos/api/invoices/" + operationId;
            assertEquals(path, paths.get(0)); assertEquals(path + "/reconcile", paths.get(1));
            assertEquals(path, paths.get(2)); assertEquals("POST", post.getRequestMethod());
            assertEquals("GET", read.getRequestMethod()); assertEquals(ORIGIN, post.getRequestProperty("Origin"));
            PaymentContract.Invoice invoice = new PaymentContract.Invoice(result, recovered.id(), recovered.amount(), recovered.invoiceId());
            assertEquals("cancelled", invoice.status); assertNull(invoice.invoiceId);
            assertTrue(PaymentContract.terminal(invoice.status));
            assertTrue(recovered.exists()); // Explicit Finish, not transport, releases the local guard.
            assertEquals(operationId, recovered.id()); assertEquals(1234, recovered.amount());
            assertEquals(expected, recovered.expectedCheckInId());
        }
    }
    @Test public void fencedReconcileAckNeverReplacesUnknownOrContradictoryGet() throws Exception {
        for (boolean missing : new boolean[]{true, false}) {
            PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
            assertTrue(memory.open().begin(ORIGIN, "Store1", 1234, ID));
            PaymentOperationStore operation = memory.open();
            Connection post = new Connection(); post.response = cancelled(operation.id(), 1234).toString();
            Connection read = new Connection();
            read.code = missing ? 404 : 200;
            read.response = missing ? "{\"noOperation\":true}" : cancelled(operation.id(), 1235).toString();
            int[] calls = {0};
            PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> ++calls[0] == 1 ? post : read, url -> "session=test-only");
            try {
                JSONObject result = api.reconcileAndRead(operation, "Operator confirmed abandonment");
                new PaymentContract.Invoice(result, operation.id(), operation.amount(), operation.invoiceId());
                fail("POST cancellation ACK cannot authorize terminal recovery");
            } catch (PaymentApi.Failure failure) { assertTrue(missing); assertEquals(404, failure.code); }
            catch (IllegalArgumentException mismatch) { assertFalse(missing); }
            assertEquals(2, calls[0]); assertTrue(operation.exists());
            assertEquals(1234, operation.amount()); assertEquals(ID, operation.expectedCheckInId());
        }
    }
    @Test public void scopedReconcileRejectsAbsentOrForeignOperationBeforeNetwork() throws Exception {
        PaymentOperationStoreTest.Memory memory = new PaymentOperationStoreTest.Memory();
        PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> { fail("No transport without matching identity"); return null; }, url -> "session=test-only");
        try { api.reconcileAndRead(memory.open(), "Abandon"); fail(); } catch (IllegalArgumentException correct) { }
        for (String[] scope : new String[][]{{"https://other.example", "Store1"}, {ORIGIN, "Store2"}}) {
            PaymentOperationStoreTest.Memory other = new PaymentOperationStoreTest.Memory();
            assertTrue(other.open().begin(scope[0], scope[1], 1234, ID));
            try { api.reconcileAndRead(other.open(), "Abandon"); fail(); } catch (IllegalArgumentException correct) { }
        }
        assertTrue(memory.open().begin(ORIGIN, "Store1", 1234, ID));
        for (String reason : new String[]{null, "", "   ", "x".repeat(501)}) {
            try { api.reconcileAndRead(memory.open(), reason); fail(); } catch (IllegalArgumentException correct) { }
        }
    }
    @Test public void onlyExactBoundedStaleRejectionReleasesCustomerCapture() throws Exception {
        for (String response : new String[]{"{\"error\":\"Expected check-in is not the store's unchanged pending capture.\"}",
                "{\"error\":\"Another POS operation is active\"}", "{", "x".repeat(32769)}) {
            Connection c = new Connection(); c.code = 400; c.response = response;
            try { api(c).request("POST", "/invoices", new JSONObject()); fail(); }
            catch (PaymentApi.Failure failure) {
                assertEquals(400, failure.code);
                assertEquals(response.startsWith("{\"error\":\"Expected"), failure.staleCheckIn);
                if (failure.staleCheckIn) {
                    failure.captureReleased = true;
                    assertTrue(failure.guidance().contains("captured reference was released"));
                    assertTrue(failure.guidance().contains("do not create a replacement"));
                }
            }
            assertTrue(c.closed);
        }
    }
    @Test public void reconciliationRequiresBoundedOperatorReasonBeforeAnyNetwork() throws Exception {
        PaymentApi api = new PaymentApi(ORIGIN, "Store1", url -> { fail(); return null; }, url -> "session=test-only");
        for (String reason : new String[]{null, "", "   ", "x".repeat(501)}) {
            try { api.reconcileAndRead(ID, reason); fail(); } catch (IllegalArgumentException correct) { }
        }
    }
    @Test public void oversizedResponsesFailClosedAndDisconnect() throws Exception {
        Connection c = new Connection(); c.response = "x".repeat(32769);
        try { api(c).request("GET", "/capability", null); fail(); } catch (PaymentApi.Failure expected) { assertEquals(0, expected.code); }
        assertTrue(c.closed);
    }
}
