package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScannerEntrySourceTest {
    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(relative)), StandardCharsets.UTF_8);
    }

    @Test public void mainDisplayContainsAutomaticInlineScannerInsteadOfManualProvisionButton() throws Exception {
        String layout = source("src/main/res/layout/activity_main.xml");
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(layout.contains("DecoratedBarcodeView"));
        assertTrue(layout.contains("@+id/inline_rewards_scanner"));
        assertTrue(layout.contains("@+id/rewards_scanner_status"));
        assertTrue(layout.contains("@+id/btn_flip_inline_camera"));
        assertFalse(layout.contains("btn_open_rewards_scanner"));
        assertFalse(layout.contains("Provision rewards scanner"));
        assertTrue(main.contains("requestPermissions(new String[] { Manifest.permission.CAMERA }"));
        assertTrue(main.contains("mainFrameLoadGate.isSuccessfulCompletion(url)"));
        assertTrue(main.contains("rewardsPagePolicy.isEligiblePageCompletion(url, view.getUrl())"));
        assertFalse(main.contains("scannerPageEligible = rewardsPagePolicy.isTrustedRewardsPage(url)"));
        assertTrue(main.contains("scannerPageEligible = false;"));
        assertTrue(main.contains("activityResumed = false;"));
        assertTrue(main.contains("pauseInlineRewardsScanner();"));
        assertTrue(main.contains("boolean blocked = !policy.allowNavigation(url)"));
        assertTrue(main.contains("WalletQrPayloadParser.parse(result.getText())"));
        assertTrue(main.contains("WalletQrPayloadParser.userFacingRejection(result.getText())"));
        assertTrue(main.contains("body.put(\"lightningAddress\", payload.value)"));
        assertTrue(main.contains("SettingsActivity.getLegacyCheckInUrl"));
        assertTrue(main.contains("DUPLICATE_SCAN_WINDOW_MS"));
        assertFalse(main.contains("scannerConfigurationUsable(this)"));
        assertFalse(main.contains("new Intent(this, RewardsProfileScanActivity.class)"));
    }

    @Test public void inlineScannerEnumeratesCameraFacingAndNeverAssumesFrontIsIdOne() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(main.contains("Camera.getNumberOfCameras()"));
        assertTrue(main.contains("Camera.getCameraInfo(cameraId, info)"));
        assertTrue(main.contains("CameraFacingSelector.select(availableRewardsCameras, requestedRewardsCameraFacing)"));
        assertTrue(main.contains("cameraSettings.setRequestedCameraId(rewardsCameraSelection.cameraId)"));
        assertTrue(main.contains("flipInlineCameraButton.setEnabled(rewardsCameraSelection.canFlip())"));
        assertTrue(main.contains("if (rewardsCameraSelection == null || !rewardsCameraSelection.canFlip()) return;"));
        assertFalse(main.contains("setRequestedCameraId(useFrontRewardsCamera ? 1 : 0)"));
        assertFalse(main.contains("setRequestedCameraId(1)"));
        assertFalse(main.contains("setRequestedCameraId(0)"));
    }

    @Test public void bothRewardScannersExplainWhyBolt11InvoicesAreUnsafe() throws Exception {
        String parser = source("src/main/java/com/bitcoinrewards/nfcdisplay/WalletQrPayloadParser.java");
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        String provisioned = source("src/main/java/com/bitcoinrewards/nfcdisplay/RewardsProfileScanActivity.java");
        assertTrue(parser.contains("Fixed Lightning invoices cannot be used for rewards. Show a Lightning address or LNURL-pay QR."));
        assertTrue(main.contains("WalletQrPayloadParser.userFacingRejection(result.getText())"));
        assertTrue(provisioned.contains("WalletQrPayloadParser.userFacingRejection(result.getText())"));
    }

    @Test public void webViewMainFrameFailuresImmediatelyGateTheInlineScanner() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(main.contains("onReceivedError(WebView view, WebResourceRequest request, WebResourceError error)"));
        assertTrue(main.contains("request != null && request.isForMainFrame()) failMainFrameLoad(request.getUrl().toString(), true)"));
        assertTrue(main.contains("onReceivedError(WebView view, int errorCode, String description, String failingUrl)"));
        assertTrue(main.contains("mainFrameLoadGate.isLikelyLegacyMainFrameError(failingUrl, currentUrl)"));
        assertTrue(main.contains("onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response)"));
        assertTrue(main.contains("request != null && request.isForMainFrame() && response != null"));
        assertTrue(main.contains("MainFrameLoadGate.isHttpFailure(response.getStatusCode())"));
        assertTrue(main.contains("mainFrameLoadGate.onMainFrameLoadFailed();"));
        assertTrue(main.contains("scannerPageEligible = false;\n        updateInlineScannerState();"));
        assertTrue(main.contains("trustedDisplayRecovery.onMainFrameNetworkOrTlsFailure(failingUrl)"));
        assertTrue(main.contains("handler.cancel()"));
        assertTrue(main.contains("mainFrameLoadGate.isLikelyLegacyMainFrameError(failingUrl, currentUrl)"));
        assertFalse(main.contains("handler.proceed()"));
    }

    @Test public void inlineCheckInUsesOnlyWebViewCookieAuthWithoutPersistenceLoggingOrIntents() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        String auth = source("src/main/java/com/bitcoinrewards/nfcdisplay/InlineCheckInAuthentication.java");
        assertTrue(main.contains("InlineCheckInAuthentication.cookieForTrustedApi"));
        assertTrue(main.contains("CookieManager.getInstance().getCookie(url)"));
        assertTrue(main.contains("connection.setRequestProperty(\"Cookie\", cookie)"));
        assertTrue(main.contains("connection.setInstanceFollowRedirects(false)"));
        assertTrue(auth.contains("BTCPay login expired; scan login QR again"));
        assertTrue(auth.contains("missing store-settings permission"));
        assertTrue(auth.contains("ApiOrigin.sameOrigin(configuredOrigin, apiUrl)"));
        assertFalse(main.contains("putExtra(\"Cookie\""));
        assertFalse(main.contains("putString(\"Cookie\""));
        assertFalse(main.contains("Log."));
        assertFalse(auth.contains("Log."));
        assertFalse(auth.contains("Intent"));
        assertFalse(auth.contains("SharedPreferences"));
    }

    @Test public void manifestHasOnlyNonExportedCanonicalRewardsScanner() throws Exception {
        String manifest = source("src/main/AndroidManifest.xml");
        int first = manifest.indexOf(".RewardsProfileScanActivity");
        assertTrue(first >= 0);
        assertTrue(manifest.indexOf(".RewardsProfileScanActivity", first + 1) < 0);
        String declaration = manifest.substring(first, Math.min(manifest.length(), first + 220));
        assertTrue(declaration.contains("android:exported=\"false\""));
    }

    @Test public void scannerUsesApplicationCoordinatorAndPauseOnlyDetachesUi() throws Exception {
        String scanner=source("src/main/java/com/bitcoinrewards/nfcdisplay/RewardsProfileScanActivity.java");
        String coordinator=source("src/main/java/com/bitcoinrewards/nfcdisplay/RewardsScanCoordinator.java");
        assertTrue(scanner.contains("RewardsScanCoordinator.get(getApplicationContext())"));
        assertTrue(scanner.contains("coordinator.detach(this)"));
        assertFalse(scanner.contains("AsyncTask"));
        assertFalse(scanner.contains(".cancel(true)"));
        assertTrue(coordinator.contains("new OperationTaskGate()"));
        assertTrue(coordinator.contains("WeakReference<Listener>"));
        assertTrue(scanner.contains("coordinator.begin(payload)"));
        assertTrue(scanner.indexOf("coordinator.begin(payload)")<scanner.indexOf("barcode.pause()",scanner.indexOf("coordinator.begin(payload)")));
        assertTrue(coordinator.contains("PendingOperation.prepared"));
    }
}
