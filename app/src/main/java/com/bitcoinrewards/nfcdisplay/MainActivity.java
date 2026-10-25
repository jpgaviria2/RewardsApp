package com.bitcoinrewards.nfcdisplay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.hardware.Camera;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.view.TextureView;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
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
    private static final int DISPLAY_RETRY_ATTEMPTS = 30;
    private static final long DISPLAY_RETRY_INITIAL_MS = 1_500L;
    private static final long DISPLAY_RETRY_MAX_MS = 30_000L;

    private WebView webView;
    private TextView loadingText;
    private WebOriginPolicy policy;
    private RewardsPagePolicy rewardsPagePolicy;
    private final MainFrameLoadGate mainFrameLoadGate = new MainFrameLoadGate();
    private String trustedDisplayUrl;
    private boolean loginExchange;
    private Handler recoveryHandler;
    private TrustedDisplayRecovery trustedDisplayRecovery;
    private final DisplayRecoveryUiState displayRecoveryUiState = new DisplayRecoveryUiState();

    private LinearLayout rewardsScannerPanel;
    private DecoratedBarcodeView inlineRewardsScanner;
    private TextView rewardsScannerStatus;
    private TextView submissionOverlay;
    private TextView successOverlay;
    private View tapActionCard;
    private Button flipInlineCameraButton;
    private WalletTutorialVideoController tutorialVideo;
    private List<CameraFacingSelector.CameraDescriptor> availableRewardsCameras = new ArrayList<>();
    private int requestedRewardsCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private CameraFacingSelector.Selection rewardsCameraSelection;
    private boolean scannerPageEligible;
    private boolean activityResumed;
    private boolean inlineScannerRunning;
    private boolean inlineSubmissionInFlight;
    private boolean captureConfirmationVisible;
    private String inlineAuthenticationFailure;
    private String lastSubmittedValue;
    private long lastSubmittedAtMs;
    private SubmitInlineCheckInTask submitTask;
    private NfcAdapter nfcAdapter;
    private boolean nfcReaderEnabled;
    private final NfcReaderGate nfcReaderGate = new NfcReaderGate();

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
        recoveryHandler = new Handler(Looper.getMainLooper());
        trustedDisplayRecovery = new TrustedDisplayRecovery(
            trustedDisplayUrl,
            DISPLAY_RETRY_ATTEMPTS,
            DISPLAY_RETRY_INITIAL_MS,
            DISPLAY_RETRY_MAX_MS,
            (runnable, delayMs) -> {
                recoveryHandler.postDelayed(runnable, delayMs);
                return () -> recoveryHandler.removeCallbacks(runnable);
            },
            () -> {
                if (webView != null) webView.loadUrl(trustedDisplayUrl);
            });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        loadingText = findViewById(R.id.loading_text);
        rewardsScannerPanel = findViewById(R.id.rewards_scanner_panel);
        inlineRewardsScanner = findViewById(R.id.inline_rewards_scanner);
        rewardsScannerStatus = findViewById(R.id.rewards_scanner_status);
        submissionOverlay = findViewById(R.id.nfc_tap_overlay);
        successOverlay = findViewById(R.id.nfc_reader_overlay);
        tapActionCard = findViewById(R.id.tap_action_card);
        flipInlineCameraButton = findViewById(R.id.btn_flip_inline_camera);
        flipInlineCameraButton.setOnClickListener(v -> flipInlineRewardsCamera());
        inlineRewardsScanner.setStatusText("");
        tutorialVideo = new WalletTutorialVideoController(
            this, (TextureView) findViewById(R.id.wallet_tutorial_video));
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
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
                trustedDisplayRecovery.onPageStarted(url);
                if (trustedDisplayUrl.equals(url)) displayRecoveryUiState.onTrustedDisplayLoadStarted();
                scannerPageEligible = false;
                if (!inlineSubmissionInFlight) hideCaptureOverlays();
                updateInlineScannerState();
                super.onPageStarted(view, url, icon);
            }

            @Override @android.annotation.TargetApi(23)
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) failMainFrameLoad(request.getUrl().toString(), true);
            }

            @Override @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                String currentUrl = view == null ? null : view.getUrl();
                if (mainFrameLoadGate.isLikelyLegacyMainFrameError(failingUrl, currentUrl)) failMainFrameLoad(failingUrl, true);
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                String failingUrl = error == null ? null : error.getUrl();
                String currentUrl = view == null ? null : view.getUrl();
                if (mainFrameLoadGate.isLikelyLegacyMainFrameError(failingUrl, currentUrl)) {
                    failMainFrameLoad(failingUrl, true);
                }
            }

            @Override @android.annotation.TargetApi(23)
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null
                    && MainFrameLoadGate.isHttpFailure(response.getStatusCode())) {
                    failMainFrameLoad(
                        request.getUrl().toString(),
                        MainFrameLoadGate.isTransientHttpFailure(response.getStatusCode()));
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
                boolean successfulMainFrame = mainFrameLoadGate.isSuccessfulCompletion(url);
                if (successfulMainFrame && trustedDisplayUrl.equals(url)) {
                    trustedDisplayRecovery.onSuccessfulCompletion(url);
                    displayRecoveryUiState.onSuccessfulTrustedDisplay();
                }
                if (loginExchange && successfulMainFrame && trustedDisplayUrl.equals(url)) {
                    view.clearHistory();
                    view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                    loginExchange = false;
                }
                scannerPageEligible = successfulMainFrame
                    && rewardsPagePolicy.isEligiblePageCompletion(url, view.getUrl());
                updateInlineScannerState();
                if (!successfulMainFrame && displayRecoveryUiState.shouldHoldFailureUi()) {
                    loadingText.setText(displayRecoveryUiState.message());
                    loadingText.setVisibility(View.VISIBLE);
                    view.setVisibility(View.INVISIBLE);
                    return;
                }
                if (scannerPageEligible) updateScannerPanelHeightForPage(view);
                loadingText.setVisibility(View.GONE);
                view.setVisibility(View.VISIBLE);
            }
        });
        webView.setVisibility(View.INVISIBLE);
        loadingText.setVisibility(View.VISIBLE);
        webView.loadUrl(initialUrl);
    }

    private void failMainFrameLoad(String failingUrl, boolean recoverableNetworkOrTlsFailure) {
        mainFrameLoadGate.onMainFrameLoadFailed();
        scannerPageEligible = false;
        updateInlineScannerState();
        if (recoverableNetworkOrTlsFailure) {
            boolean retryScheduled = trustedDisplayRecovery.onTransientMainFrameFailure(failingUrl);
            displayRecoveryUiState.onTransientFailure(retryScheduled || trustedDisplayRecovery.isRecovering());
            loadingText.setText(displayRecoveryUiState.message());
            loadingText.setVisibility(View.VISIBLE);
            if (webView != null) webView.setVisibility(View.INVISIBLE);
        }
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
        updateNfcReaderState();
        if (rewardsScannerPanel == null || inlineRewardsScanner == null) return;
        if (!scannerPageEligible) {
            rewardsScannerPanel.setVisibility(View.GONE);
            setTutorialActive(false);
            pauseInlineRewardsScanner();
            return;
        }
        rewardsScannerPanel.setVisibility(View.VISIBLE);
        boolean tutorialActive = activityResumed && !inlineSubmissionInFlight && !captureConfirmationVisible
            && inlineAuthenticationFailure == null
            && (trustedDisplayRecovery == null || !trustedDisplayRecovery.isRecovering());
        setTutorialActive(tutorialActive);
        if (!hasCameraPermission()) {
            pauseInlineRewardsScanner();
            setRewardsScannerStatus("Camera permission is required for automatic rewards scanning.");
            return;
        }
        if (inlineAuthenticationFailure != null) {
            pauseInlineRewardsScanner();
            setRewardsScannerStatus(inlineAuthenticationFailure);
            return;
        }
        if (rewardsCameraSelection == null) selectRewardsCamera();
        if (!rewardsCameraSelection.hasCamera()) {
            pauseInlineRewardsScanner();
            setRewardsScannerStatus("No camera is available for automatic rewards scanning.");
            return;
        }
        if (!activityResumed || inlineSubmissionInFlight || captureConfirmationVisible) {
            pauseInlineRewardsScanner();
            return;
        }
        if (!inlineScannerRunning) {
            configureInlineRewardsCamera();
            inlineRewardsScanner.decodeContinuous(inlineRewardsCallback);
            inlineRewardsScanner.resume();
            inlineScannerRunning = true;
        }
        setRewardsScannerStatus("Ready — tap a card or show a wallet QR");
        pulseTapCard();
    }

    private void updateScannerPanelHeightForPage(WebView view) {
        view.evaluateJavascript(
            "(function(){var hasQr=!!document.querySelector('.qr-code img, img[alt*=\\\"Reward QR\\\"], #nfc-lnurl-data[data-lnurl]');return JSON.stringify({hasQr:hasQr});})()",
            value -> {
                if (!scannerPageEligible || rewardsScannerPanel == null) return;
                boolean rewardVisible = value != null && value.contains("\\\"hasQr\\\":true");
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rewardsScannerPanel.getLayoutParams();
                params.weight = 0f;
                params.height = dp(rewardVisible ? 310 : 350);
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
                setRewardsScannerStatus(WalletQrPayloadParser.userFacingRejection(result.getText()));
                return;
            }
            long now = System.currentTimeMillis();
            if (payload.value.equals(lastSubmittedValue) && now - lastSubmittedAtMs < DUPLICATE_SCAN_WINDOW_MS) return;
            inlineSubmissionInFlight = true;
            lastSubmittedValue = payload.value;
            lastSubmittedAtMs = now;
            setRewardsScannerStatus("Connecting " + payload.displayLabel() + "…");
            showSubmissionOverlay("Connecting your wallet…");
            vibrate(80);
            pauseInlineRewardsScanner();
            updateNfcReaderState();
            submitTask = new SubmitInlineCheckInTask(payload);
            submitTask.execute();
        }
    };

    private void updateNfcReaderState() {
        if (nfcAdapter == null) return;
        nfcReaderGate.update(
            activityResumed,
            scannerPageEligible,
            trustedDisplayRecovery != null && trustedDisplayRecovery.isRecovering(),
            inlineAuthenticationFailure != null,
            inlineSubmissionInFlight || captureConfirmationVisible);
        boolean shouldEnable = nfcReaderGate.shouldEnable();
        try {
            if (shouldEnable && !nfcReaderEnabled) {
                Bundle options = new Bundle();
                options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
                nfcAdapter.enableReaderMode(
                    this,
                    this::onNfcTagDiscovered,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
                nfcReaderEnabled = true;
            } else if (!shouldEnable && nfcReaderEnabled) {
                nfcAdapter.disableReaderMode(this);
                nfcReaderEnabled = false;
            }
        } catch (IllegalStateException e) {
            nfcReaderEnabled = false;
        }
    }

    private void disableNfcReader() {
        if (nfcAdapter == null || !nfcReaderEnabled) return;
        try { nfcAdapter.disableReaderMode(this); }
        catch (IllegalStateException ignored) { }
        nfcReaderEnabled = false;
    }

    private void onNfcTagDiscovered(Tag tag) {
        final String boltcardUrl;
        try {
            boltcardUrl = readBoltcardUrl(tag);
        } catch (Exception e) {
            runOnUiThread(() -> {
                if (nfcReaderGate.shouldEnable()) setRewardsScannerStatus("Tap a standard LNbits Boltcard.");
            });
            return;
        }
        runOnUiThread(() -> submitBoltcardTap(boltcardUrl));
    }

    private String readBoltcardUrl(Tag tag) throws Exception {
        if (tag == null) throw new IllegalArgumentException("Missing NFC tag");
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            // Type 4 Boltcard tags expose both IsoDep and Ndef. NdefFormatable/sole IsoDep
            // tags have no standard readable NDEF message, so never fall back to raw APDUs.
            if (NdefFormatable.get(tag) != null || IsoDep.get(tag) != null) {
                throw new IllegalArgumentException("NFC tag does not expose readable NDEF");
            }
            throw new IllegalArgumentException("NFC tag is not NDEF");
        }
        try {
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            if (message == null || message.toByteArray().length > BoltcardPayloadParser.MAX_NDEF_BYTES) {
                throw new IllegalArgumentException("Invalid NDEF message");
            }
            NdefRecord[] records = message.getRecords();
            if (records == null || records.length != 1 || records.length > BoltcardPayloadParser.MAX_NDEF_RECORDS) {
                throw new IllegalArgumentException("Boltcard must contain one URI record");
            }
            NdefRecord record = records[0];
            return BoltcardPayloadParser.requireBoltcard(BoltcardPayloadParser.extractUri(
                record.getTnf(), record.getType(), record.getPayload()));
        } finally {
            try { ndef.close(); } catch (Exception ignored) { }
        }
    }

    private void submitBoltcardTap(String boltcardUrl) {
        if (!activityResumed || !nfcReaderGate.shouldEnable() || inlineSubmissionInFlight || !scannerPageEligible) return;
        long now = System.currentTimeMillis();
        if (boltcardUrl.equals(lastSubmittedValue) && now - lastSubmittedAtMs < DUPLICATE_SCAN_WINDOW_MS) return;
        inlineSubmissionInFlight = true;
        lastSubmittedValue = boltcardUrl;
        lastSubmittedAtMs = now;
        setRewardsScannerStatus("Reading your rewards card…");
        showSubmissionOverlay("Reading rewards card…");
        vibrate(80);
        pauseInlineRewardsScanner();
        updateNfcReaderState();
        submitTask = new SubmitInlineCheckInTask(boltcardUrl);
        submitTask.execute();
    }

    private void setRewardsScannerStatus(String text) {
        if (rewardsScannerStatus != null) rewardsScannerStatus.setText(text);
    }

    private void setTutorialActive(boolean active) {
        if (tutorialVideo != null) tutorialVideo.setActive(active);
    }

    private void showSubmissionOverlay(String message) {
        setTutorialActive(false);
        if (successOverlay != null) successOverlay.setVisibility(View.GONE);
        if (submissionOverlay != null) {
            submissionOverlay.setText(message);
            submissionOverlay.setAlpha(0f);
            submissionOverlay.setVisibility(View.VISIBLE);
            submissionOverlay.animate().alpha(1f).setDuration(160L).start();
        }
    }

    private void showSuccessOverlay(String label) {
        captureConfirmationVisible = true;
        updateNfcReaderState();
        if (submissionOverlay != null) submissionOverlay.setVisibility(View.GONE);
        if (successOverlay == null) return;
        String customer = label == null || label.isEmpty() ? "Wallet" : label;
        successOverlay.setText("Wallet connected\n" + customer + "\nYou’re ready to earn rewards");
        successOverlay.setScaleX(0.88f);
        successOverlay.setScaleY(0.88f);
        successOverlay.setAlpha(0f);
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L).start();
    }

    private void hideCaptureOverlays() {
        captureConfirmationVisible = false;
        if (submissionOverlay != null) submissionOverlay.setVisibility(View.GONE);
        if (successOverlay != null) successOverlay.setVisibility(View.GONE);
    }

    private void pulseTapCard() {
        if (tapActionCard == null || tapActionCard.getAnimation() != null) return;
        tapActionCard.animate().cancel();
        tapActionCard.animate().scaleX(1.025f).scaleY(1.025f).setDuration(450L)
            .withEndAction(() -> {
                if (tapActionCard != null) tapActionCard.animate()
                    .scaleX(1f).scaleY(1f).setDuration(450L).start();
            }).start();
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
        private final String boltcardUrl;
        private int responseCode;
        private boolean loginExpired;

        SubmitInlineCheckInTask(WalletQrPayloadParser.ParsedPayload payload) {
            this.payload = payload;
            this.boltcardUrl = null;
        }

        SubmitInlineCheckInTask(String boltcardUrl) {
            this.payload = null;
            this.boltcardUrl = boltcardUrl;
        }

        @Override protected Boolean doInBackground(Void... ignored) {
            HttpURLConnection connection = null;
            try {
                String legacyApiUrl = SettingsActivity.getLegacyCheckInUrl(MainActivity.this);
                if (legacyApiUrl == null || legacyApiUrl.isEmpty() || isCancelled()) return false;
                String apiUrl = boltcardUrl == null
                    ? legacyApiUrl
                    : BoltcardCheckInEndpoint.fromLegacyCheckInUrl(legacyApiUrl);
                String cookie = InlineCheckInAuthentication.cookieForTrustedApi(
                    SettingsActivity.getBtcpayUrl(MainActivity.this),
                    apiUrl,
                    url -> CookieManager.getInstance().getCookie(url));
                if (cookie == null) {
                    loginExpired = true;
                    return false;
                }
                JSONObject body = new JSONObject();
                if (boltcardUrl == null) body.put("lightningAddress", payload.value);
                else body.put("boltcardUrl", boltcardUrl);
                body.put("deviceId", LEGACY_INLINE_DEVICE_ID);

                connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cookie", cookie);
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                responseCode = connection.getResponseCode();
                loginExpired = InlineCheckInAuthentication.isLoginExpired(responseCode);
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
            if (loginExpired) {
                inlineAuthenticationFailure = InlineCheckInAuthentication.failureMessage(responseCode);
                hideCaptureOverlays();
                updateInlineScannerState();
                return;
            }
            if (ok) {
                String label = boltcardUrl == null ? payload.displayLabel() : "Rewards card";
                setRewardsScannerStatus("Wallet connected — " + label);
                showSuccessOverlay(label);
                Toast.makeText(MainActivity.this, "Wallet connected: " + label, Toast.LENGTH_LONG).show();
                vibrate(180);
                recoveryHandler.postDelayed(() -> {
                    if (!isFinishing() && webView != null) webView.reload();
                }, 1_600L);
            } else if (payload != null && payload.kind == WalletQrPayloadParser.Kind.LNURL_PAY && responseCode >= 400) {
                hideCaptureOverlays();
                updateInlineScannerState();
                setRewardsScannerStatus("LNURL-pay is not accepted by this server yet (HTTP " + responseCode + ").");
            } else {
                hideCaptureOverlays();
                if (boltcardUrl != null && boltcardUrl.equals(lastSubmittedValue)) lastSubmittedValue = null;
                updateInlineScannerState();
                setRewardsScannerStatus(responseCode >= 400
                    ? "BTCPay rejected check-in (HTTP " + responseCode + ")."
                    : "Could not reach BTCPay to save this rewards profile.");
            }
        }

        @Override protected void onCancelled() {
            if (submitTask == this) submitTask = null;
            inlineSubmissionInFlight = false;
            hideCaptureOverlays();
            updateInlineScannerState();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        updateInlineScannerState();
    }

    @Override protected void onPause() {
        activityResumed = false;
        setTutorialActive(false);
        nfcReaderGate.update(false, scannerPageEligible,
            trustedDisplayRecovery != null && trustedDisplayRecovery.isRecovering(),
            inlineAuthenticationFailure != null, inlineSubmissionInFlight || captureConfirmationVisible);
        disableNfcReader();
        pauseInlineRewardsScanner();
        super.onPause();
    }

    @Override protected void onDestroy() {
        activityResumed = false;
        nfcReaderGate.update(false, scannerPageEligible,
            trustedDisplayRecovery != null && trustedDisplayRecovery.isRecovering(),
            inlineAuthenticationFailure != null, inlineSubmissionInFlight || captureConfirmationVisible);
        disableNfcReader();
        if (trustedDisplayRecovery != null) trustedDisplayRecovery.destroy();
        if (submitTask != null) submitTask.cancel(true);
        submitTask = null;
        if (tutorialVideo != null) tutorialVideo.release();
        tutorialVideo = null;
        if (inlineRewardsScanner != null) inlineRewardsScanner.pauseAndWait();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
