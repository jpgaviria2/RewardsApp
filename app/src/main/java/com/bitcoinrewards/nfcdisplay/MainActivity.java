package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;

import com.bitcoinrewards.nfcdisplay.ndef.NdefHostCardEmulationService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Main activity: fullscreen WebView loading the BTCPay rewards display page,
 * with HCE NFC integration via JavaScript bridge.
 * 
 * Auth flow:
 * 1. POST to /api/v1/api-keys with Basic auth to get API key (done in settings)
 * 2. On launch, POST login form to get session cookie
 * 3. Set cookie in WebView's CookieManager
 * 4. Load display page — BTCPay sees valid session cookie
 * 5. Auto-refresh works because cookie persists in WebView
 */
public class MainActivity extends Activity {
    private static final String TAG = "RewardsNFC";

    private WebView webView;
    private TextView nfcTapOverlay;
    private TextView loadingText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentLnurl = null;
    private boolean nfcEnabled = true;
    private boolean loginAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding is needed
        if (!SettingsActivity.isOnboarded(this)) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

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
        loadingText = findViewById(R.id.loading_text);
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

        // Enable cookies in WebView
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Add JS bridge
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Hide loading overlay
                if (loadingText != null) {
                    loadingText.setVisibility(View.GONE);
                }
                webView.setVisibility(View.VISIBLE);

                // If we hit the login page, just let the user log in manually
                // The background login already tried — don't fight the WebView
                if (url.contains("/login") || url.contains("/Account/Login")) {
                    Log.i(TAG, "Login page shown in WebView — user can log in manually");
                    // Don't interfere — let the user type
                    return;
                }

                if (nfcEnabled) {
                    extractLnurlFromPage(view);
                }
            }
        });

        // Show loading state
        webView.setVisibility(View.INVISIBLE);
        if (loadingText != null) {
            loadingText.setVisibility(View.VISIBLE);
            loadingText.setText("⚡ Connecting to BTCPay Server...");
        }

        // First, try to establish a session cookie via login
        performBackgroundLogin();

        if (nfcEnabled) {
            if (NdefHostCardEmulationService.isHceAvailable(this)) {
                // Set up tap listener (static — works before service is created)
                NdefHostCardEmulationService.setStaticTapListener(() -> onNfcTapDetected());
                Log.i(TAG, "NFC HCE available, tap listener set");
            } else {
                Log.e(TAG, "HCE not available on this device!");
            }
        }
    }

    /**
     * Perform login in background to get session cookie, then load display page.
     * 
     * BTCPay login flow:
     * 1. GET /login — get the anti-forgery token from the form
     * 2. POST /login — submit email + password + token
     * 3. Extract Set-Cookie headers
     * 4. Inject cookies into WebView CookieManager
     * 5. Load display page
     */
    private void performBackgroundLogin() {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String btcpayUrl = prefs.getString(SettingsActivity.KEY_BTCPAY_URL, "");
        String email = prefs.getString(SettingsActivity.KEY_EMAIL, "");
        String password = prefs.getString(SettingsActivity.KEY_PASSWORD, "");
        String apiKey = prefs.getString(SettingsActivity.KEY_API_KEY, "");

        if (btcpayUrl.isEmpty() || email.isEmpty()) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        new LoginTask().execute(btcpayUrl, email, password, apiKey);
    }

    private class LoginTask extends AsyncTask<String, Void, Boolean> {
        private String btcpayUrl;
        private java.util.List<String> sessionCookies = new java.util.ArrayList<>();

        @Override
        protected Boolean doInBackground(String... params) {
            btcpayUrl = params[0];
            String email = params[1];
            String password = params[2];
            String apiKey = params[3];

            try {
                // Step 1: GET /login to get anti-forgery token and initial cookies
                Log.i(TAG, "Step 1: Fetching login page for anti-forgery token...");
                URL loginUrl = new URL(btcpayUrl + "/login");
                HttpURLConnection getConn = (HttpURLConnection) loginUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setInstanceFollowRedirects(false);

                int getCode = getConn.getResponseCode();
                Log.i(TAG, "Login page GET returned: " + getCode);

                // Collect cookies from GET
                java.util.List<String> getCookies = new java.util.ArrayList<>();
                Map<String, List<String>> getHeaders = getConn.getHeaderFields();
                if (getHeaders != null) {
                    for (Map.Entry<String, List<String>> entry : getHeaders.entrySet()) {
                        if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                            getCookies.addAll(entry.getValue());
                        }
                    }
                }

                // Extract anti-forgery token from HTML
                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream()));
                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) html.append(line);
                reader.close();

                String antiForgeryToken = "";
                String body = html.toString();
                int tokenIdx = body.indexOf("__RequestVerificationToken");
                if (tokenIdx != -1) {
                    int valueIdx = body.indexOf("value=\"", tokenIdx);
                    if (valueIdx != -1) {
                        valueIdx += 7;
                        int endIdx = body.indexOf("\"", valueIdx);
                        if (endIdx != -1) {
                            antiForgeryToken = body.substring(valueIdx, endIdx);
                            Log.i(TAG, "Found anti-forgery token: " + antiForgeryToken.substring(0, Math.min(20, antiForgeryToken.length())) + "...");
                        }
                    }
                }

                // Step 2: POST login form
                Log.i(TAG, "Step 2: Submitting login form...");
                HttpURLConnection postConn = (HttpURLConnection) loginUrl.openConnection();
                postConn.setRequestMethod("POST");
                postConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                postConn.setInstanceFollowRedirects(false);
                postConn.setDoOutput(true);

                // Forward cookies from GET request
                StringBuilder cookieHeader = new StringBuilder();
                for (String cookie : getCookies) {
                    String cookieName = cookie.split(";")[0];
                    if (cookieHeader.length() > 0) cookieHeader.append("; ");
                    cookieHeader.append(cookieName);
                }
                if (cookieHeader.length() > 0) {
                    postConn.setRequestProperty("Cookie", cookieHeader.toString());
                }

                String postBody = "Email=" + java.net.URLEncoder.encode(email, "UTF-8") +
                    "&Password=" + java.net.URLEncoder.encode(password, "UTF-8") +
                    "&__RequestVerificationToken=" + java.net.URLEncoder.encode(antiForgeryToken, "UTF-8");

                OutputStream os = postConn.getOutputStream();
                os.write(postBody.getBytes("UTF-8"));
                os.flush();
                os.close();

                int postCode = postConn.getResponseCode();
                Log.i(TAG, "Login POST returned: " + postCode);

                // Collect ALL cookies (from both GET and POST)
                sessionCookies.addAll(getCookies);
                Map<String, List<String>> postHeaders = postConn.getHeaderFields();
                if (postHeaders != null) {
                    for (Map.Entry<String, List<String>> entry : postHeaders.entrySet()) {
                        if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                            sessionCookies.addAll(entry.getValue());
                        }
                    }
                }

                Log.i(TAG, "Total cookies collected: " + sessionCookies.size());
                return !sessionCookies.isEmpty() && (postCode == 302 || postCode == 200);

            } catch (Exception e) {
                Log.e(TAG, "Login error: " + e.getMessage(), e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean gotCookies) {
            CookieManager cookieManager = CookieManager.getInstance();

            if (gotCookies) {
                // Inject session cookies into WebView
                for (String cookie : sessionCookies) {
                    Log.i(TAG, "Setting cookie: " + cookie.substring(0, Math.min(50, cookie.length())) + "...");
                    cookieManager.setCookie(btcpayUrl, cookie);
                }
                cookieManager.flush();
            }

            // Load the display page with cookies set
            String displayUrl = SettingsActivity.getDisplayUrl(MainActivity.this);
            if (displayUrl != null) {
                Log.i(TAG, "Loading display: " + displayUrl + " (cookies: " + (gotCookies ? "yes" : "no") + ")");
                webView.loadUrl(displayUrl);
            }
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

        // Use static method — works even before service is created by Android
        NdefHostCardEmulationService.setPayload("lightning:" + lnurl);
        Log.i(TAG, "NFC payload set: lightning:" + lnurl.substring(0, Math.min(30, lnurl.length())) + "...");

        webView.evaluateJavascript(
            "var ind = document.getElementById('nfc-hce-indicator');" +
            "if (ind) ind.style.display = 'block';",
            null
        );
    }

    void clearNfcPayload() {
        if (currentLnurl == null) return;
        currentLnurl = null;

        NdefHostCardEmulationService.clearPayload();
        Log.i(TAG, "NFC cleared");
    }

    public void onNfcTapDetected() {
        handler.post(() -> {
            nfcTapOverlay.setVisibility(View.VISIBLE);

            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(100);

            handler.postDelayed(() -> nfcTapOverlay.setVisibility(View.GONE), 1500);
        });
    }

    @Override
    protected void onDestroy() {
        clearNfcPayload();
        super.onDestroy();
    }
}
