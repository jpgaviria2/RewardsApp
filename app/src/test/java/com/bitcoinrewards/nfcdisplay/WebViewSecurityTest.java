package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class WebViewSecurityTest {
    @Test public void navigationAndCameraRequireExactConfiguredOrigin() {
        WebOriginPolicy p=new WebOriginPolicy("https://btcpay.example");
        assertTrue(p.allowNavigation("https://btcpay.example/plugins/bitcoin-rewards/store/display"));
        assertFalse(p.allowNavigation("http://btcpay.example/display"));
        assertFalse(p.allowNavigation("https://btcpay.example.evil.test/display"));
        assertFalse(p.allowNavigation("https://evil.test/?next=https://btcpay.example"));
        assertTrue(p.allowCamera("https://btcpay.example"));
        assertFalse(p.allowCamera("https://btcpay.example:8443"));
    }

    @Test public void mainHasNoJavascriptBridgeAndNeverEnablesThirdPartyCookies() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java")), StandardCharsets.UTF_8);
        assertFalse(source.contains("addJavascriptInterface"));
        assertFalse(source.contains("AndroidBridge"));
        assertTrue(source.contains("policy.allowNavigation"));
        assertTrue(source.contains("policy.allowCamera"));
        assertFalse(source.contains("setAcceptThirdPartyCookies(webView, true)"));
        assertTrue(source.contains("onReceivedSslError"));
        assertTrue(source.contains("handler.cancel()"));
        assertFalse(source.contains("handler.proceed()"));
        assertFalse(source.contains("SslErrorHandler.proceed"));
    }
}
