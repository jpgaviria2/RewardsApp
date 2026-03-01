package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;

import com.bitcoinrewards.nfcdisplay.ndef.NdefHostCardEmulationService;

/**
 * Main activity: fullscreen WebView loading the BTCPay rewards display page,
 * with HCE NFC integration via JavaScript bridge.
 */
public class MainActivity extends Activity {
    private static final String TAG = "RewardsNFC";

    private WebView webView;
    private TextView nfcTapOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentLnurl = null;
    private boolean nfcEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding is needed
        if (!SettingsActivity.isOnboarded(this)) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        // Get display URL from settings
        String displayUrl = SettingsActivity.getDisplayUrl(this);
        if (displayUrl == null) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        nfcEnabled = SettingsActivity.isNfcEnabled(this);

        // Keep screen on (kiosk display)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        nfcTapOverlay = findViewById(R.id.nfc_tap_overlay);
        webView = findViewById(R.id.webview);

        // Settings gear button
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Add JS bridge
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // If we landed on login page, auto-fill and submit
                if (url.contains("/login") || url.contains("/Account/Login")) {
                    Log.i(TAG, "Login page detected, auto-authenticating...");
                    autoLogin(view);
                    return;
                }
                if (nfcEnabled) {
                    extractLnurlFromPage(view);
                }
            }
        });

        Log.i(TAG, "Loading display URL: " + displayUrl);
        webView.loadUrl(displayUrl);

        if (nfcEnabled && !NdefHostCardEmulationService.isHceAvailable(this)) {
            Log.e(TAG, "HCE not available on this device!");
        }
    }

    private void extractLnurlFromPage(WebView view) {
        view.evaluateJavascript(
            "(function() {" +
            "  var el = document.getElementById('nfc-lnurl-data');" +
            "  if (el && el.dataset.lnurl) return el.dataset.lnurl;" +
            "  var html = document.body.innerHTML;" +
            "  var m = html.match(/lnurl[0-9a-zA-Z]{50,}/);" +
            "  return m ? m[0] : '';" +
            "})()",
            value -> {
                String lnurl = value != null ? value.replace("\"", "").trim() : "";
                if (!lnurl.isEmpty() && lnurl.startsWith("lnurl")) {
                    setNfcPayload(lnurl);
                } else {
                    clearNfcPayload();
                }
            }
        );
    }

    void setNfcPayload(String lnurl) {
        if (!nfcEnabled) return;
        if (lnurl.equals(currentLnurl)) return;
        currentLnurl = lnurl;

        NdefHostCardEmulationService hce = NdefHostCardEmulationService.getInstance();
        if (hce != null) {
            hce.setPaymentRequest("lightning:" + lnurl);
            Log.i(TAG, "NFC broadcasting: lightning:" + lnurl.substring(0, Math.min(30, lnurl.length())) + "...");

            webView.evaluateJavascript(
                "var ind = document.getElementById('nfc-hce-indicator');" +
                "if (ind) ind.style.display = 'block';",
                null
            );
        }
    }

    void clearNfcPayload() {
        if (currentLnurl == null) return;
        currentLnurl = null;

        NdefHostCardEmulationService hce = NdefHostCardEmulationService.getInstance();
        if (hce != null) {
            hce.clearPaymentRequest();
            Log.i(TAG, "NFC cleared");
        }
    }

    /**
     * Called by HCE service when a customer taps their phone.
     */
    public void onNfcTapDetected() {
        handler.post(() -> {
            nfcTapOverlay.setVisibility(View.VISIBLE);

            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(100);

            handler.postDelayed(() -> nfcTapOverlay.setVisibility(View.GONE), 1500);
        });
    }

    /**
     * Auto-fill login form when the WebView is redirected to the login page.
     * Uses the stored email and submits — password is handled via API key cookie.
     */
    private void autoLogin(WebView view) {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String email = prefs.getString(SettingsActivity.KEY_EMAIL, "");
        String apiKey = prefs.getString(SettingsActivity.KEY_API_KEY, "");
        String btcpayUrl = prefs.getString(SettingsActivity.KEY_BTCPAY_URL, "");

        if (!apiKey.isEmpty() && !btcpayUrl.isEmpty()) {
            // Set API key as cookie for authentication
            android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);

            // Use Authorization header approach: load the display URL with API key
            String displayUrl = SettingsActivity.getDisplayUrl(this);
            if (displayUrl != null) {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Authorization", "token " + apiKey);
                Log.i(TAG, "Reloading with API key auth");
                view.loadUrl(displayUrl, headers);
            }
        }
    }

    @Override
    protected void onDestroy() {
        clearNfcPayload();
        super.onDestroy();
    }
}
