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
            entry = SettingsActivity.getPosUrl(this);
            policy = new WebOriginPolicy(SettingsActivity.getBtcpayUrl(this));
            if (entry.isEmpty()) throw new IllegalArgumentException();
        } catch (Exception e) {
            TextView guidance = new TextView(this);
            guidance.setText("Configure the native BTCPay POS URL for this server and Store ID in Settings. No payment has been created.");
            root.addView(guidance);
            Button settings = new Button(this);
            settings.setText("Settings");
            settings.setOnClickListener(v -> { startActivity(new Intent(this, SettingsActivity.class)); finish(); });
            root.addView(settings);
            return;
        }
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
        com.bitcoinrewards.nfcdisplay.ndef.NdefHostCardEmulationService.clearPayload();
        if (web != null && new PaymentOperationStore(this).exists()) { web.stopLoading(); recovery(); }
    }
    @Override public void onBackPressed() {
        if (new PaymentOperationStore(this).exists()) recovery(); else finish();
    }
    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
