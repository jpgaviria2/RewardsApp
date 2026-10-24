package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class LoginCodeFlowSourceTest {
    @Test public void settingsExposesNativeLoginScannerAndKeepsScannerProvisioningSeparate() throws Exception {
        String settings = source("src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java");
        String layout = source("src/main/res/layout/activity_settings.xml");
        assertTrue(layout.contains("@+id/btn_scan_login_code"));
        assertTrue(layout.contains("Scan BTCPay login QR"));
        assertTrue(layout.contains("@+id/btn_save_scanner"));
        assertTrue(settings.contains("new Intent(this, LoginCodeScanActivity.class)"));
        assertTrue(settings.contains("displayConfigurationUsable"));
        assertTrue(settings.contains("scannerConfigurationUsable"));
    }

    @Test public void secretLoginUrlUsesOnlyConsumeOnceProcessMemory() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        String settings = source("src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java");
        String scanner = source("src/main/java/com/bitcoinrewards/nfcdisplay/LoginCodeScanActivity.java");
        assertTrue(settings.contains("OneTimeLoginHandoff.publish(loginUrl)"));
        assertTrue(main.contains("OneTimeLoginHandoff.consume()"));
        assertTrue(scanner.contains("OneTimeLoginHandoff.publish(payload)"));
        assertTrue(settings.contains("String raw = OneTimeLoginHandoff.consume()"));
        assertFalse(main.contains("getStringExtra"));
        assertFalse(settings.contains("putExtra(MainActivity"));
        assertFalse(scanner.contains("putExtra"));
        assertFalse(main.contains("EXTRA_LOGIN_URL"));
        assertFalse(settings.contains("putString(loginUrl"));
    }

    @Test public void loginExchangeAvoidsCacheThenClearsHistoryAtExactDisplayUrl() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(main.contains("WebSettings.LOAD_NO_CACHE"));
        assertTrue(main.contains("trustedDisplayUrl.equals(url)"));
        assertTrue(main.contains("view.clearHistory()"));
        assertTrue(main.contains("setCacheMode(WebSettings.LOAD_DEFAULT)"));
        assertTrue(main.indexOf("view.clearHistory()") < main.lastIndexOf("setCacheMode(WebSettings.LOAD_DEFAULT)"));
        assertTrue(main.contains("CookieManager.getInstance().setAcceptCookie(true)"));
        assertFalse(main.contains("session_cookies"));
    }

    @Test public void exportedMainActivityNeverAcceptsExternalSecretUrls() throws Exception {
        String manifest = source("src/main/AndroidManifest.xml");
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(manifest.contains("android:name=\".MainActivity\""));
        assertTrue(manifest.contains("android:exported=\"true\""));
        assertFalse(main.contains("getIntent().getData"));
        assertFalse(main.contains("getIntent().getStringExtra"));
        assertFalse(main.contains("loadUrl(getIntent"));
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
