package com.bitcoinrewards.nfcdisplay;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** One bounded same-origin read; cookies are process-local and redirects are never followed. */
final class PosDiscoveryClient {
    static final class Failure extends Exception {
        final int status;
        Failure(int status) { this.status = status; }
    }
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    private final Connections connections;
    private final long deadlineMillis;
    PosDiscoveryClient() { this(url -> (HttpURLConnection) url.openConnection()); }
    PosDiscoveryClient(Connections connections) { this(connections, 25000L); }
    PosDiscoveryClient(Connections connections, long deadlineMillis) {
        this.connections = connections;
        this.deadlineMillis = deadlineMillis;
    }
    private volatile HttpURLConnection active;
    private volatile boolean cancelled;
    List<PosDiscovery.App> discover(String origin, String store, String cookie) throws Exception {
        String endpoint = PosDiscovery.listUrl(origin, store);
        if (cookie == null || cookie.isEmpty()) throw new Failure(401);
        HttpURLConnection connection = connections.open(new URL(endpoint));
        active = connection;
        java.util.Timer deadlineTimer = new java.util.Timer("pos-discovery-deadline", true);
        deadlineTimer.schedule(new java.util.TimerTask() {
            @Override public void run() { PosDiscoveryClient.this.cancel(); }
        }, deadlineMillis);
        try {
            if (cancelled) throw new InterruptedIOException();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("Accept", "text/html");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            int status = connection.getResponseCode();
            if (status != 200) throw new Failure(status);
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.US).startsWith("text/html")) throw new Failure(0);
            if (connection.getContentLength() > PosDiscovery.MAX_BYTES) throw new Failure(0);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int size;
                long deadline = System.nanoTime() + 15000000000L;
                while ((size = input.read(buffer)) != -1) {
                    if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    if (System.nanoTime() > deadline || output.size() + size > PosDiscovery.MAX_BYTES) throw new Failure(0);
                    output.write(buffer, 0, size);
                }
                if (cancelled) throw new InterruptedIOException();
                List<PosDiscovery.App> apps = PosDiscovery.parse(origin, store, new String(output.toByteArray(), StandardCharsets.UTF_8));
                if (cancelled) throw new InterruptedIOException();
                return apps;
            }
        } finally {
            deadlineTimer.cancel();
            connection.disconnect();
            active = null;
        }
    }
    void cancel() {
        cancelled = true;
        HttpURLConnection connection = active;
        if (connection != null) connection.disconnect();
    }
}
