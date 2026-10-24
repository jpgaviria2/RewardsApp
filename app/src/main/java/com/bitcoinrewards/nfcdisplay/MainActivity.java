package com.bitcoinrewards.nfcdisplay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION_REQUEST = 5501;
    private static final long DUPLICATE_SCAN_WINDOW_MS = 15_000L;
    private static final String LEGACY_INLINE_DEVICE_ID = "rewards-nfc-display-inline";

    private WebView webView;
    private TextView loadingText;
    private WebOriginPolicy policy;
    private RewardsPagePolicy rewardsPagePolicy;
    private final MainFrameLoadGate mainFrameLoadGate = new MainFrameLoadGate();
    private String trustedDisplayUrl;
    private boolean loginExchange;

    private LinearLayout rewardsScannerPanel;
    private DecoratedBarcodeView inlineRewardsScanner;
    private TextView rewardsScannerStatus;
    private Button flipInlineCameraButton;
    private List<CameraFacingSelector.CameraDescriptor> availableRewardsCameras = new ArrayList<>();
    private int requestedRewardsCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private CameraFacingSelector.Selection rewardsCameraSelection;
    private boolean scannerPageEligible;
    private boolean activityResumed;
    private boolean inlineScannerRunning;
    private boolean inlineSubmissionInFlight;
    private String lastSubmittedValue;
    private long lastSubmittedAtMs;
    private SubmitInlineCheckInTask submitTask;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LegacyCredentialPurge.run(this, () -> initialize(state));
    }

    private void initialize(Bundle state) {
        if (!SettingsActivity.displayConfigurationUsable(this)) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }
        String display = SettingsActivity.getDisplayUrl(this);
        try {
            policy = new WebOriginPolicy(SettingsActivity.getBtcpayUrl(this));
            rewardsPagePolicy = new RewardsPagePolicy(SettingsActivity.getBtcpayUrl(this), SettingsActivity.getStoreId(this));
        } catch (Exception e) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        String requestedLogin = OneTimeLoginHandoff.consume();
        loginExchange = LoginCodePayloadParser.isTrustedLoginUrl(requestedLogin, SettingsActivity.getBtcpayUrl(this), display);
        final String initialUrl = loginExchange ? requestedLogin : display;
        requestedLogin = null;
        trustedDisplayUrl = display;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        loadingText = findViewById(R.id.loading_text);
        rewardsScannerPanel = findViewById(R.id.rewards_scanner_panel);
        inlineRewardsScanner = findViewById(R.id.inline_rewards_scanner);
        rewardsScannerStatus = findViewById(R.id.rewards_scanner_status);
        flipInlineCameraButton = findViewById(R.id.btn_flip_inline_camera);
        flipInlineCameraButton.setOnClickListener(v -> flipInlineRewardsCamera());
        inlineRewardsScanner.setStatusText("");
        ImageButton settings = findViewById(R.id.btn_settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        discoverRewardsCameras();
        requestCameraPermissionIfNeeded();

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(loginExchange ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override @android.annotation.TargetApi(21)
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    String origin = request.getOrigin() == null ? "" : request.getOrigin().toString();
                    boolean camera = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) camera = true;
                    }
                    if (camera && policy.allowCamera(origin)) request.grant(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE });
                    else request.deny();
                });
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                mainFrameLoadGate.onPageStarted(url);
                scannerPageEligible = false;
                updateInlineScannerState();
                super.onPageStarted(view, url, icon);
            }

            @Override @android.annotation.TargetApi(23)
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) failMainFrameLoad();
            }

            @Override @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                String currentUrl = view == null ? null : view.getUrl();
                if (mainFrameLoadGate.isLikelyLegacyMainFrameError(failingUrl, currentUrl)) failMainFrameLoad();
            }

            @Override @android.annotation.TargetApi(23)
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null
                    && MainFrameLoadGate.isHttpFailure(response.getStatusCode())) {
                    failMainFrameLoad();
                }
            }

            @Override @android.annotation.TargetApi(21)
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                boolean blocked = request != null && request.isForMainFrame() && !policy.allowNavigation(request.getUrl().toString());
                if (blocked) {
                    scannerPageEligible = false;
                    updateInlineScannerState();
                }
                return blocked;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                boolean blocked = !policy.allowNavigation(url);
                if (blocked) {
                    scannerPageEligible = false;
                    updateInlineScannerState();
                }
                return blocked;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (loginExchange && trustedDisplayUrl.equals(url)) {
                    view.clearHistory();
                    view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                    loginExchange = false;
                }
                scannerPageEligible = mainFrameLoadGate.isSuccessfulCompletion(url)
                    && rewardsPagePolicy.isEligiblePageCompletion(url, view.getUrl());
                updateInlineScannerState();
                if (scannerPageEligible) updateScannerPanelHeightForPage(view);
                loadingText.setVisibility(View.GONE);
                view.setVisibility(View.VISIBLE);
            }
        });
        webView.setVisibility(View.INVISIBLE);
        loadingText.setVisibility(View.VISIBLE);
        webView.loadUrl(initialUrl);
    }

    private void failMainFrameLoad() {
        mainFrameLoadGate.onMainFrameLoadFailed();
        scannerPageEligible = false;
        updateInlineScannerState();
    }

    private void requestCameraPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.CAMERA }, CAMERA_PERMISSION_REQUEST);
        }
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void discoverRewardsCameras() {
        List<CameraFacingSelector.CameraDescriptor> discovered = new ArrayList<>();
        try {
            int count = Camera.getNumberOfCameras();
            for (int cameraId = 0; cameraId < count; cameraId++) {
                try {
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    Camera.getCameraInfo(cameraId, info);
                    discovered.add(new CameraFacingSelector.CameraDescriptor(cameraId, info.facing));
                } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }
        availableRewardsCameras = discovered;
        selectRewardsCamera();
    }

    private void selectRewardsCamera() {
        rewardsCameraSelection = CameraFacingSelector.select(availableRewardsCameras, requestedRewardsCameraFacing);
        if (flipInlineCameraButton != null) {
            flipInlineCameraButton.setEnabled(rewardsCameraSelection.canFlip());
            flipInlineCameraButton.setAlpha(rewardsCameraSelection.canFlip() ? 1f : 0.45f);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        if (!hasCameraPermission()) setRewardsScannerStatus("Camera permission is required for automatic rewards scanning.");
        updateInlineScannerState();
    }

    private void updateInlineScannerState() {
        if (rewardsScannerPanel == null || inlineRewardsScanner == null) return;
        if (!scannerPageEligible) {
            rewardsScannerPanel.setVisibility(View.GONE);
            pauseInlineRewardsScanner();
            return;
        }
        rewardsScannerPanel.setVisibility(View.VISIBLE);
        if (!hasCameraPermission()) {
            pauseInlineRewardsScanner();
            setRewardsScannerStatus("Camera permission is required for automatic rewards scanning.");
            return;
        }
        if (rewardsCameraSelection == null) selectRewardsCamera();
        if (!rewardsCameraSelection.hasCamera()) {
            pauseInlineRewardsScanner();
            setRewardsScannerStatus("No camera is available for automatic rewards scanning.");
            return;
        }
        if (!activityResumed || inlineSubmissionInFlight) {
            pauseInlineRewardsScanner();
            return;
        }
        if (!inlineScannerRunning) {
            configureInlineRewardsCamera();
            inlineRewardsScanner.decodeContinuous(inlineRewardsCallback);
            inlineRewardsScanner.resume();
            inlineScannerRunning = true;
        }
        setRewardsScannerStatus("Show wallet Lightning QR to camera");
    }

    private void updateScannerPanelHeightForPage(WebView view) {
        view.evaluateJavascript(
            "(function(){var hasQr=!!document.querySelector('.qr-code img, img[alt*=\\\"Reward QR\\\"], #nfc-lnurl-data[data-lnurl]');return JSON.stringify({hasQr:hasQr});})()",
            value -> {
                if (!scannerPageEligible || rewardsScannerPanel == null) return;
                boolean rewardVisible = value != null && value.contains("\\\"hasQr\\\":true");
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rewardsScannerPanel.getLayoutParams();
                params.weight = 0f;
                params.height = dp(rewardVisible ? 240 : 270);
                rewardsScannerPanel.setLayoutParams(params);
            });
    }

    private void configureInlineRewardsCamera() {
        CameraSettings cameraSettings = new CameraSettings();
        cameraSettings.setRequestedCameraId(rewardsCameraSelection.cameraId);
        cameraSettings.setAutoFocusEnabled(true);
        cameraSettings.setContinuousFocusEnabled(true);
        inlineRewardsScanner.setCameraSettings(cameraSettings);
    }

    private void flipInlineRewardsCamera() {
        if (rewardsCameraSelection == null || !rewardsCameraSelection.canFlip()) return;
        requestedRewardsCameraFacing = rewardsCameraSelection.alternateFacing;
        selectRewardsCamera();
        pauseInlineRewardsScanner();
        updateInlineScannerState();
        setRewardsScannerStatus((rewardsCameraSelection.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "Front" : "Back") + " camera ready");
    }

    private void pauseInlineRewardsScanner() {
        if (inlineRewardsScanner != null && inlineScannerRunning) inlineRewardsScanner.pause();
        inlineScannerRunning = false;
    }

    private final BarcodeCallback inlineRewardsCallback = new BarcodeCallback() {
        @Override public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null || inlineSubmissionInFlight || !scannerPageEligible) return;
            WalletQrPayloadParser.ParsedPayload payload;
            try {
                payload = WalletQrPayloadParser.parse(result.getText());
            } catch (IllegalArgumentException e) {
                setRewardsScannerStatus("QR must contain a Lightning address or LNURL-pay code.");
                return;
            }
            long now = System.currentTimeMillis();
            if (payload.value.equals(lastSubmittedValue) && now - lastSubmittedAtMs < DUPLICATE_SCAN_WINDOW_MS) return;
            inlineSubmissionInFlight = true;
            lastSubmittedValue = payload.value;
            lastSubmittedAtMs = now;
            setRewardsScannerStatus("Saving " + payload.displayLabel() + "...");
            vibrate(80);
            pauseInlineRewardsScanner();
            submitTask = new SubmitInlineCheckInTask(payload);
            submitTask.execute();
        }
    };

    private void setRewardsScannerStatus(String text) {
        if (rewardsScannerStatus != null) rewardsScannerStatus.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void vibrate(int ms) {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(ms);
        } catch (Exception ignored) { }
    }

    @SuppressLint("StaticFieldLeak")
    private final class SubmitInlineCheckInTask extends AsyncTask<Void, Void, Boolean> {
        private final WalletQrPayloadParser.ParsedPayload payload;
        private int responseCode;

        SubmitInlineCheckInTask(WalletQrPayloadParser.ParsedPayload payload) {
            this.payload = payload;
        }

        @Override protected Boolean doInBackground(Void... ignored) {
            HttpURLConnection connection = null;
            try {
                String apiUrl = SettingsActivity.getLegacyCheckInUrl(MainActivity.this);
                if (apiUrl == null || apiUrl.isEmpty() || isCancelled()) return false;
                JSONObject body = new JSONObject();
                body.put("lightningAddress", payload.value);
                body.put("deviceId", LEGACY_INLINE_DEVICE_ID);

                connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                responseCode = connection.getResponseCode();
                return responseCode >= 200 && responseCode < 300;
            } catch (Exception e) {
                return false;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        @Override protected void onPostExecute(Boolean ok) {
            if (submitTask != this) return;
            submitTask = null;
            inlineSubmissionInFlight = false;
            if (ok) {
                setRewardsScannerStatus("Ready to reward " + payload.displayLabel());
                Toast.makeText(MainActivity.this, "Rewards profile saved: " + payload.displayLabel(), Toast.LENGTH_LONG).show();
                vibrate(180);
                if (webView != null) webView.reload();
            } else if (payload.kind == WalletQrPayloadParser.Kind.LNURL_PAY && responseCode >= 400) {
                updateInlineScannerState();
                setRewardsScannerStatus("LNURL-pay is not accepted by this server yet (HTTP " + responseCode + ").");
            } else {
                updateInlineScannerState();
                setRewardsScannerStatus(responseCode >= 400
                    ? "BTCPay rejected check-in (HTTP " + responseCode + ")."
                    : "Could not reach BTCPay to save this rewards profile.");
            }
        }

        @Override protected void onCancelled() {
            if (submitTask == this) submitTask = null;
            inlineSubmissionInFlight = false;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        updateInlineScannerState();
    }

    @Override protected void onPause() {
        activityResumed = false;
        pauseInlineRewardsScanner();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (submitTask != null) submitTask.cancel(true);
        submitTask = null;
        if (inlineRewardsScanner != null) inlineRewardsScanner.pauseAndWait();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
