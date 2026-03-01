package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
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
import android.widget.TextView;

import com.bitcoinrewards.nfcdisplay.ndef.NdefHostCardEmulationService;

/**
 * Main activity: fullscreen WebView loading the BTCPay rewards display page,
 * with HCE NFC integration via JavaScript bridge.
 *
 * The WebView loads the existing display URL. After each page load, JS extracts
 * the LNURL from the page and sets it on the HCE service for NFC broadcast.
 */
public class MainActivity extends Activity {
    private static final String TAG = "RewardsNFC";

    // CONFIGURE: Set your BTCPay rewards display URL
    private static final String DISPLAY_URL = "https://your-btcpay-host/plugins/bitcoin-rewards/YOUR_STORE_ID/display";

    private WebView webView;
    private TextView nfcTapOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentLnurl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on (kiosk display)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        nfcTapOverlay = findViewById(R.id.nfc_tap_overlay);
        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Add JS bridge
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                extractLnurlFromPage(view);
            }
        });

        webView.loadUrl(DISPLAY_URL);

        if (!NdefHostCardEmulationService.isHceAvailable(this)) {
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
        if (lnurl.equals(currentLnurl)) return;
        currentLnurl = lnurl;

        NdefHostCardEmulationService hce = NdefHostCardEmulationService.getInstance();
        if (hce != null) {
            hce.setPaymentRequest("lightning:" + lnurl);
            Log.i(TAG, "NFC broadcasting: lightning:" + lnurl.substring(0, Math.min(30, lnurl.length())) + "...");

            // Show NFC indicator on the page
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

    @Override
    protected void onDestroy() {
        clearNfcPayload();
        super.onDestroy();
    }
}
