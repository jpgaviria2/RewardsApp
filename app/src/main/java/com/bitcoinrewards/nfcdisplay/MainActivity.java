package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.cardemulation.CardEmulation;
import android.content.ComponentName;
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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Main activity: fullscreen WebView loading local Trails-branded HTML,
 * polling BTCPay API for pending rewards, with HCE NFC integration.
 */
public class MainActivity extends Activity {
    private static final String TAG = "RewardsNFC";

    private WebView webView;
    private TextView nfcTapOverlay;
    private TextView loadingText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentLnurl = null;
    private boolean nfcEnabled = true;

    private RewardPoller poller;
    private boolean showingReward = false;
    private String currentRewardId = null;

    // Pending reward data to inject after page load
    private String pendingSats;
    private String pendingQrDataUri;
    private String pendingLnurl;
    private String pendingRewardId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding is needed
        if (!SettingsActivity.isOnboarded(this)) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String btcpayUrl = prefs.getString(SettingsActivity.KEY_BTCPAY_URL, "");
        String storeId = prefs.getString(SettingsActivity.KEY_STORE_ID, "");
        String apiKey = prefs.getString(SettingsActivity.KEY_API_KEY, "");

        if (btcpayUrl.isEmpty() || storeId.isEmpty() || apiKey.isEmpty()) {
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

        // Add JS bridge
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (loadingText != null) {
                    loadingText.setVisibility(View.GONE);
                }
                webView.setVisibility(View.VISIBLE);

                if (url.contains("reward.html") && pendingSats != null) {
                    String js = "loadReward('" + pendingSats + "','" + pendingQrDataUri + "','" + pendingLnurl + "','" + pendingRewardId + "');";
                    view.evaluateJavascript(js, null);
                    if (nfcEnabled) {
                        setNfcPayload(pendingLnurl);
                    }
                    pendingSats = null;
                    pendingQrDataUri = null;
                    pendingLnurl = null;
                    pendingRewardId = null;
                }
            }
        });

        // Load waiting page
        webView.setVisibility(View.INVISIBLE);
        if (loadingText != null) {
            loadingText.setVisibility(View.VISIBLE);
            loadingText.setText("Loading...");
        }
        webView.loadUrl("file:///android_asset/waiting.html");

        // Start polling
        poller = new RewardPoller(btcpayUrl, storeId, apiKey, new RewardPoller.RewardCallback() {
            @Override
            public void onReward(String rewardId, long sats, String lnurl, String qrDataUri, int remainingSeconds) {
                handler.post(() -> {
                    if (showingReward && rewardId.equals(currentRewardId)) return;
                    showingReward = true;
                    currentRewardId = rewardId;
                    pendingSats = String.valueOf(sats);
                    pendingQrDataUri = qrDataUri;
                    pendingLnurl = lnurl;
                    pendingRewardId = rewardId;
                    webView.loadUrl("file:///android_asset/reward.html");
                });
            }

            @Override
            public void onNoReward() {
                handler.post(() -> {
                    if (showingReward) {
                        showingReward = false;
                        currentRewardId = null;
                        webView.loadUrl("file:///android_asset/waiting.html");
                        clearNfcPayload();
                    }
                });
            }
        });
        poller.start();

        if (nfcEnabled) {
            NfcManager nfcManager = (NfcManager) getSystemService(NFC_SERVICE);
            NfcAdapter nfcAdapter = nfcManager != null ? nfcManager.getDefaultAdapter() : null;

            if (nfcAdapter == null) {
                Log.e(TAG, "NFC not supported on this device");
            } else if (!nfcAdapter.isEnabled()) {
                Log.e(TAG, "NFC is disabled — opening settings");
                startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
            } else if (NdefHostCardEmulationService.isHceAvailable(this)) {
                NdefHostCardEmulationService.setStaticTapListener(() -> onNfcTapDetected());
                Log.i(TAG, "NFC HCE available, tap listener set");
            } else {
                Log.e(TAG, "HCE not supported on this device");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcEnabled) {
            try {
                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
                if (nfcAdapter != null) {
                    CardEmulation cardEmulation = CardEmulation.getInstance(nfcAdapter);
                    ComponentName hceComponent = new ComponentName(this, NdefHostCardEmulationService.class);
                    cardEmulation.setPreferredService(this, hceComponent);
                    Log.i(TAG, "Set preferred HCE service");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set preferred HCE service: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        if (nfcEnabled) {
            try {
                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
                if (nfcAdapter != null) {
                    CardEmulation cardEmulation = CardEmulation.getInstance(nfcAdapter);
                    cardEmulation.unsetPreferredService(this);
                    Log.i(TAG, "Unset preferred HCE service");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to unset preferred HCE service: " + e.getMessage());
            }
        }
        super.onPause();
    }

    public void dismissCurrentReward() {
        if (currentRewardId == null) return;
        final String rewardId = currentRewardId;
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        final String btcpayUrl = prefs.getString(SettingsActivity.KEY_BTCPAY_URL, "");
        final String storeId = prefs.getString(SettingsActivity.KEY_STORE_ID, "");
        final String apiKey = prefs.getString(SettingsActivity.KEY_API_KEY, "");

        showingReward = false;
        currentRewardId = null;
        webView.loadUrl("file:///android_asset/waiting.html");
        clearNfcPayload();

        new Thread(() -> {
            try {
                String endpoint = btcpayUrl + "/plugins/bitcoin-rewards/" + storeId + "/dismiss-reward";
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String body = "{\"rewardId\":\"" + rewardId + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.flush();
                os.close();
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Dismiss error: " + e.getMessage());
            }
        }).start();
    }

    void setNfcPayload(String lnurl) {
        if (!nfcEnabled) return;
        if (lnurl.equals(currentLnurl)) return;
        currentLnurl = lnurl;

        String fullUri = "lightning:" + lnurl;
        NdefHostCardEmulationService.setPayload(fullUri);
        Log.i(TAG, "NFC payload set (" + fullUri.length() + " chars): " + fullUri.substring(0, Math.min(50, fullUri.length())) + "...");
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
        if (poller != null) poller.destroy();
        clearNfcPayload();
        super.onDestroy();
    }
}
