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

    @Test public void visibleMainEntryLaunchesCanonicalAuthenticatedActivity() throws Exception {
        String layout = source("src/main/res/layout/activity_main.xml");
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(layout.contains("@+id/btn_open_rewards_scanner"));
        assertTrue(main.contains("new Intent(this, RewardsProfileScanActivity.class)"));
        assertFalse(layout.contains("DecoratedBarcodeView"));
        assertFalse(main.contains("SubmitInlineCheckInTask"));
        assertFalse(main.contains("inlineRewardsScanner"));
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
    }
}
