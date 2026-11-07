package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class NativePosSourceTest {
    @Test public void alternateSettingsSaveInvalidatesInheritedInputAndValidatesBeforeCommit() throws Exception {
        String settings = source("SettingsActivity");
        String scanner = settings.substring(settings.indexOf("private void saveScannerProvisioning"), settings.indexOf("private String persistDisplayConfiguration"));
        assertTrue(scanner.indexOf("if (deviceId.isEmpty())") < scanner.indexOf("persistDisplayConfiguration(origin, storeId)"));
        assertTrue(scanner.indexOf("if (!secure.available())") < scanner.indexOf("persistDisplayConfiguration(origin, storeId)"));
        assertTrue(scanner.contains("!sameProfile || !scannerConfigurationUsable(this)"));
        String commit = settings.substring(settings.indexOf("private String persistDisplayConfiguration"), settings.indexOf("static String commitDisplayConfiguration"));
        assertTrue(commit.contains("pos.setText(\"\")"));
        assertTrue(commit.indexOf("if (!origin.equals(oldOrigin)") < commit.indexOf("pos.setText(\"\")"));
        assertTrue(settings.contains("editor.remove(\"native_pos_url\").remove(\"native_pos_origin\").remove(\"native_pos_store\")"));
        assertTrue(settings.contains("Display settings may already be saved"));
    }
    @Test public void recreationShowsWarningAndRequiresExplicitReopenWithoutRestoringSecrets() throws Exception {
        String pos = source("NativePosActivity");
        String gate = pos.substring(pos.indexOf("if (state != null)"), pos.indexOf("private void openPos"));
        assertTrue(gate.contains("Checkout interrupted."));
        assertTrue(gate.contains("A previous BTCPay invoice may still be payable"));
        assertTrue(gate.contains("reopen.setOnClickListener"));
        assertTrue(gate.contains("return;"));
        assertFalse(gate.contains("web.loadUrl"));
        assertFalse(pos.contains("putString("));
    }
    @Test public void discoveryUsesExistingSessionAndFencesSelectionBeforeWebCreation() throws Exception {
        String pos = source("NativePosActivity");
        assertTrue(pos.contains("getCookie(PosDiscovery.listUrl(origin, store))"));
        assertTrue(pos.contains("apps.size() == 1"));
        assertTrue(pos.contains("for (PosDiscovery.App app : apps)"));
        assertTrue(pos.contains("choice.setText(app.name"));
        assertTrue(pos.contains("if (!current(token)) return;"));
        assertTrue(pos.contains("gate.accepts(token, SettingsActivity.getBtcpayUrl(this), SettingsActivity.getStoreId(this))"));
        assertTrue(pos.contains("gate.pause()"));
        assertTrue(pos.contains("client.discover(origin, store, cookie)"));
        assertTrue(pos.contains("worker.interrupt()"));
        assertTrue(pos.contains("discovery.cancel()"));
        assertTrue(pos.contains("PosUrlPolicy.require(origin, url)"));
        assertTrue(pos.indexOf("if (!current(token)) { finish(); return; }") < pos.indexOf("createWeb(discoveryPanel)"));
    }
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/" + name + ".java")), StandardCharsets.UTF_8);
    }
    @Test public void posHasNoNativeCaptureOrCustomCreatePath() throws Exception {
        String pos = source("NativePosActivity");
        for (String forbidden : new String[]{"enableReaderMode", "BarcodeCallback", "addJavascriptInterface", "PaymentApi", "api.create", "setCookie(", "saveState(", "restoreState(", "SslErrorHandler.proceed"}) assertFalse(forbidden, pos.contains(forbidden));
        assertTrue(pos.contains("NdefHostCardEmulationService.clearPayload()"));
        assertTrue(pos.contains("Back does not cancel a BTCPay invoice"));
        assertTrue(pos.contains("request.deny()"));
        assertTrue(pos.contains("handler.cancel()"));
        assertTrue(pos.contains("setAcceptThirdPartyCookies(web, false)"));
        assertTrue(pos.contains("setAllowFileAccess(false)"));
        assertTrue(pos.contains("setAllowContentAccess(false)"));
        assertTrue(pos.contains("MIXED_CONTENT_NEVER_ALLOW"));
        assertTrue(pos.contains("!policy.allowNavigation"));
    }
    @Test public void financialLockRoutesToRecoveryBeforePosLoad() throws Exception {
        String pos = source("NativePosActivity");
        assertTrue(pos.indexOf("if (new PaymentOperationStore(this).exists())") < pos.indexOf("web.loadUrl(entry)"));
        assertTrue(pos.contains("PaymentActivity.class"));
        assertFalse(pos.contains("clearTerminal"));
        assertTrue(source("MainActivity").contains("PaymentActivity.class : NativePosActivity.class"));
        assertFalse(source("PaymentActivity").contains("api.create("));
        assertFalse(source("PaymentActivity").contains("operations.begin("));
    }
    @Test public void entryConfigurationIsBoundToBothProfileFields() throws Exception {
        String settings = source("SettingsActivity");
        assertTrue(settings.contains("getBtcpayUrl(c).equals(p.getString(\"native_pos_origin\""));
        assertTrue(settings.contains("getStoreId(c).equals(p.getString(\"native_pos_store\""));
        assertTrue(settings.contains("PosUrlPolicy.require(getBtcpayUrl(c)"));
        assertFalse(settings.contains("pay.anmore.space"));
    }
}
