package com.bitcoinrewards.nfcdisplay;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;

/** Native merchant POS only. No Android reward capture, invoice creation API, or NFC adapter. */
public final class NativePosActivity extends Activity {
    private WebView web;
    private WebOriginPolicy policy;
    private String entry;
    private String origin, store;
    private LinearLayout discoveryPanel;
    private PosDiscoveryClient discovery;
    private Thread worker;
    private PosDiscoveryGate gate;
    private boolean resumed;
    private boolean wantsDiscovery;

    private boolean current(long token) {
        return !isFinishing() && gate != null
            && gate.accepts(token, SettingsActivity.getBtcpayUrl(this), SettingsActivity.getStoreId(this));
    }
    private void cancelDiscovery() {
        if (gate != null) gate.invalidate();
        if (discovery != null) { discovery.cancel(); discovery = null; }
        if (worker != null) { worker.interrupt(); worker = null; }
    }
    private void discover() {
        cancelDiscovery();
        final long token = gate.invalidate();
        discoveryPanel.removeAllViews();
        TextView status = new TextView(this);
        status.setText("Finding this store’s BTCPay keypad…");
        discoveryPanel.addView(status);
        final PosDiscoveryClient client = new PosDiscoveryClient();
        discovery = client;
        final String cookie = CookieManager.getInstance().getCookie(PosDiscovery.listUrl(origin, store));
        worker = new Thread(() -> {
            try {
                java.util.List<PosDiscovery.App> apps = client.discover(origin, store, cookie);
                runOnUiThread(() -> {
                    if (!current(token)) return;
                    if (new PaymentOperationStore(this).exists()) { recovery(); return; }
                    discoveryPanel.removeAllViews();
                    if (apps.size() == 1) { launch(apps.get(0).url, token); return; }
                    if (apps.isEmpty()) { discoveryError("No active Keypad POS was found for this store. In BTCPay, configure a Point of Sale app with Keypad style, then retry.", token); return; }
                    ScrollView scroll = new ScrollView(this);
                    LinearLayout choices = new LinearLayout(this);
                    choices.setOrientation(LinearLayout.VERTICAL);
                    scroll.addView(choices);
                    discoveryPanel.addView(scroll);
                    TextView title = new TextView(this);
                    title.setText("Choose this store’s keypad:");
                    choices.addView(title);
                    for (PosDiscovery.App app : apps) {
                        Button choice = new Button(this);
                        choice.setText(app.name + " (" + app.id + ")");
                        choice.setOnClickListener(v -> launch(app.url, token));
                        choices.addView(choice);
                    }
                });
            } catch (Exception failure) {
                int code = failure instanceof PosDiscoveryClient.Failure ? ((PosDiscoveryClient.Failure) failure).status : 0;
                runOnUiThread(() -> {
                    if (!current(token)) return;
                    String message = code == 403 ? "Your BTCPay account does not have permission to manage this store’s apps. Sign in with an authorized store account."
                        : code == 401 || (code >= 300 && code <= 399) ? "BTCPay needs an authorized store session. Return to Settings and scan a fresh BTCPay Login Code. A redirected store page may also mean this store is unavailable."
                        : code == 404 ? "The store apps page is unavailable. Check this server and Store ID; this is not proof that your login expired."
                        : "Could not safely read this store’s apps. Check connectivity, or use the manual POS fallback in Settings. No payment has been created.";
                    discoveryError(message, token);
                });
            }
        }, "btcpay-pos-discovery");
        worker.start();
    }
    private void discoveryError(String message, long token) {
        discoveryPanel.removeAllViews();
        TextView guidance = new TextView(this);
        guidance.setText(message);
        discoveryPanel.addView(guidance);
        Button retry = new Button(this);
        retry.setText("Retry discovery");
        retry.setOnClickListener(v -> { if (current(token)) discover(); });
        discoveryPanel.addView(retry);
        Button settings = new Button(this);
        settings.setText("Settings / BTCPay login");
        settings.setOnClickListener(v -> { startActivity(new Intent(this, SettingsActivity.class)); finish(); });
        discoveryPanel.addView(settings);
        String manual = SettingsActivity.getPosUrl(this);
        if (!manual.isEmpty()) {
            Button fallback = new Button(this);
            fallback.setText("Use this profile’s manually configured POS");
            fallback.setOnClickListener(v -> launch(manual, token));
            discoveryPanel.addView(fallback);
        }
    }
    private void launch(String url, long token) {
        if (!current(token)) { finish(); return; }
        if (new PaymentOperationStore(this).exists()) { recovery(); return; }
        entry = PosUrlPolicy.require(origin, url);
        wantsDiscovery = false;
        cancelDiscovery();
        discoveryPanel.removeAllViews();
        createWeb(discoveryPanel);
    }
    private void recovery() {
        startActivity(new Intent(this, PaymentActivity.class));
        finish();
    }
    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        com.bitcoinrewards.nfcdisplay.ndef.NdefHostCardEmulationService.clearPayload();
        if (new PaymentOperationStore(this).exists()) { recovery(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button back = new Button(this);
        back.setText("Back to rewards");
        back.setOnClickListener(v -> onBackPressed());
        root.addView(back);
        Button history = new Button(this);
        history.setText("Previous custom payments / recovery");
        history.setOnClickListener(v -> recovery());
        root.addView(history);
        TextView caution = new TextView(this);
        caution.setText("Back does not cancel a BTCPay invoice. Before charging again, check any previous checkout in BTCPay; it may still be payable.");
        root.addView(caution);
        setContentView(root);
        if (state != null) {
            TextView warning = new TextView(this);
            warning.setText("Checkout interrupted. A previous BTCPay invoice may still be payable. Check its status in BTCPay before reopening POS; do not charge again.");
            root.addView(warning);
            Button reopen = new Button(this);
            reopen.setText("I checked BTCPay — reopen POS");
            reopen.setOnClickListener(v -> {
                root.removeView(warning);
                root.removeView(reopen);
                openPos(root);
            });
            root.addView(reopen);
            return;
        }
        openPos(root);
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void openPos(LinearLayout root) {
        if (new PaymentOperationStore(this).exists()) { recovery(); return; }
        try {
            origin = ApiOrigin.requireConfigured(SettingsActivity.getBtcpayUrl(this)).toString();
            store = SettingsActivity.getStoreId(this);
            PosDiscovery.listUrl(origin, store);
            policy = new WebOriginPolicy(origin);
            gate = new PosDiscoveryGate(origin, store);
            if (resumed) gate.resume();
        } catch (Exception e) {
            TextView guidance = new TextView(this);
            guidance.setText("Configure this BTCPay server and Store ID in Settings first. No POS URL is required.");
            root.addView(guidance);
            return;
        }
        discoveryPanel = new LinearLayout(this);
        discoveryPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(discoveryPanel, new LinearLayout.LayoutParams(-1, 0, 1));
        wantsDiscovery = true;
        if (resumed) discover();
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void createWeb(LinearLayout root) {
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebChromeClient(new WebChromeClient() {
            @android.annotation.TargetApi(21)
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !policy.allowNavigation(url); }
            @android.annotation.TargetApi(24)
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !policy.allowNavigation(request.getUrl().toString());
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                if (!policy.allowNavigation(url)) { view.stopLoading(); view.loadUrl(entry); }
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return policy.allowNavigation(url) ? null : new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
            }
            @android.annotation.TargetApi(21)
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return shouldInterceptRequest(view, request.getUrl().toString());
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) { handler.cancel(); }
        });
        // Never save/restore WebView state or read/serialize CookieManager session secrets.
        web.loadUrl(entry);
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (gate != null) gate.resume();
        com.bitcoinrewards.nfcdisplay.ndef.NdefHostCardEmulationService.clearPayload();
        if (new PaymentOperationStore(this).exists()) { if (web != null) web.stopLoading(); recovery(); return; }
        if (origin != null && (!origin.equals(SettingsActivity.getBtcpayUrl(this)) || !store.equals(SettingsActivity.getStoreId(this)))) { finish(); return; }
        if (web != null) web.onResume();
        if (wantsDiscovery && discoveryPanel != null) discover();
    }
    @Override protected void onPause() {
        resumed = false;
        if (gate != null) gate.pause();
        cancelDiscovery();
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override public void onBackPressed() {
        if (new PaymentOperationStore(this).exists()) recovery(); else finish();
    }
    @Override protected void onDestroy() {
        cancelDiscovery();
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
