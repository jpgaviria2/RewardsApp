package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class PosDiscoveryClientTest {
    static class Connection extends HttpURLConnection {
        int status = 200, reads;
        boolean disconnected;
        String type = "text/html; charset=utf-8";
        byte[] body = PosDiscoveryTest.row("Key1", "Counter", "Keypad", "Store1").getBytes(StandardCharsets.UTF_8);
        int declared = -1;
        Connection(URL url) { super(url); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public int getResponseCode() throws IOException { return status; }
        @Override public String getContentType() { return type; }
        @Override public int getContentLength() { return declared; }
        @Override public InputStream getInputStream() { reads++; return new ByteArrayInputStream(body); }
    }
    @Test public void sendsOnlyExistingCookieToExactStoreAndDisablesRedirects() throws Exception {
        Connection c = new Connection(new URL(PosDiscovery.listUrl(PosDiscoveryTest.ORIGIN, "Store1")));
        PosDiscoveryClient client = new PosDiscoveryClient(url -> { assertEquals(c.getURL(), url); return c; });
        assertEquals(1, client.discover(PosDiscoveryTest.ORIGIN, "Store1", "synthetic-session").size());
        assertEquals("synthetic-session", c.getRequestProperty("Cookie"));
        assertEquals("GET", c.getRequestMethod());
        assertFalse(c.getInstanceFollowRedirects()); assertFalse(c.getUseCaches());
        assertEquals(10000, c.getConnectTimeout()); assertEquals(10000, c.getReadTimeout());
        assertTrue(c.disconnected);
    }
    @Test public void authPermissionRedirectAndMissingRouteStatusesAreDistinctAndNeverReadBody() throws Exception {
        for (int status : new int[]{301,302,303,307,308,401,403,404,500}) {
            Connection c = new Connection(new URL(PosDiscoveryTest.ORIGIN)); c.status = status;
            try { new PosDiscoveryClient(url -> c).discover(PosDiscoveryTest.ORIGIN, "Store1", "synthetic"); fail(); }
            catch (PosDiscoveryClient.Failure e) { assertEquals(status, e.status); }
            assertEquals(0, c.reads); assertTrue(c.disconnected);
        }
    }
    @Test public void missingCookieNeverConnectsAndInvalidProfileNeverReceivesCookie() throws Exception {
        PosDiscoveryClient client = new PosDiscoveryClient(url -> { throw new AssertionError("must not connect"); });
        for (String cookie : new String[]{null,""}) {
            try { client.discover(PosDiscoveryTest.ORIGIN, "Store1", cookie); fail(); }
            catch (PosDiscoveryClient.Failure e) { assertEquals(401, e.status); }
        }
        try { client.discover("https://pay.example/path", "Store1", "synthetic"); fail(); } catch (IllegalArgumentException expected) { }
        try { client.discover(PosDiscoveryTest.ORIGIN, "../Other", "synthetic"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void unknownContentLengthStillEnforcesBodyLimitAndContentType() throws Exception {
        for (int mode=0;mode<3;mode++) {
            Connection c = new Connection(new URL(PosDiscoveryTest.ORIGIN));
            if (mode==0) c.body = new byte[PosDiscovery.MAX_BYTES+1];
            if (mode==1) c.declared = PosDiscovery.MAX_BYTES+1;
            if (mode==2) c.type = "application/json";
            try { new PosDiscoveryClient(url -> c).discover(PosDiscoveryTest.ORIGIN, "Store1", "synthetic"); fail(); }
            catch (PosDiscoveryClient.Failure expected) { assertEquals(0, expected.status); }
            assertTrue(c.disconnected);
            if (mode!=0) assertEquals(0,c.reads);
        }
    }
    @Test public void totalDeadlineDisconnectsEvenWhileWaitingForHeaders() throws Exception {
        java.util.concurrent.CountDownLatch disconnected = new java.util.concurrent.CountDownLatch(1);
        Connection stalled = new Connection(new URL(PosDiscoveryTest.ORIGIN)) {
            @Override public void disconnect() { super.disconnect(); disconnected.countDown(); }
            @Override public int getResponseCode() throws IOException {
                try { assertTrue(disconnected.await(2, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                throw new InterruptedIOException();
            }
        };
        try { new PosDiscoveryClient(url -> stalled, 20L).discover(PosDiscoveryTest.ORIGIN, "Store1", "synthetic"); fail(); }
        catch (InterruptedIOException expected) { }
        assertTrue(stalled.disconnected); assertEquals(0, stalled.reads);
    }
    @Test public void cancellationBeforeAndDuringReadFailsClosedAndDisconnects() throws Exception {
        Connection c = new Connection(new URL(PosDiscoveryTest.ORIGIN));
        PosDiscoveryClient client = new PosDiscoveryClient(url -> c); client.cancel();
        try { client.discover(PosDiscoveryTest.ORIGIN,"Store1","synthetic"); fail(); } catch (InterruptedIOException expected) { }
        assertEquals(0,c.reads); assertTrue(c.disconnected);
        final PosDiscoveryClient[] holder = new PosDiscoveryClient[1];
        Connection during = new Connection(new URL(PosDiscoveryTest.ORIGIN)) {
            @Override public InputStream getInputStream() { holder[0].cancel(); return super.getInputStream(); }
        };
        holder[0] = new PosDiscoveryClient(url -> during);
        try { holder[0].discover(PosDiscoveryTest.ORIGIN,"Store1","synthetic"); fail(); } catch (InterruptedIOException expected) { }
        assertTrue(during.disconnected);
    }
}
