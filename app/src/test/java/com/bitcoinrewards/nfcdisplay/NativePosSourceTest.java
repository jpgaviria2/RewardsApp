package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class NativePosSourceTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/" + name + ".java")), StandardCharsets.UTF_8);
    }
    @Test public void posHasNoNativeCaptureOrCustomCreatePath() throws Exception {
        String pos = source("NativePosActivity");
        for (String forbidden : new String[]{"enableReaderMode", "BarcodeCallback", "addJavascriptInterface", "PaymentApi", "api.create", "getCookie(", "setCookie(", "saveState(", "restoreState(", "SslErrorHandler.proceed"}) assertFalse(forbidden, pos.contains(forbidden));
        assertTrue(pos.contains("NdefHostCardEmulationService.clearPayload()"));
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
