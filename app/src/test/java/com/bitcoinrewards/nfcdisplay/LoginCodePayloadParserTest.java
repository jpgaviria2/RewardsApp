package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.*;

public class LoginCodePayloadParserTest {
    private static final String ORIGIN = "https://btcpay.example";
    private static final String DISPLAY = ORIGIN + "/plugins/bitcoin-rewards/store%20id/display";

    @Test public void parsesBtcpayLoginUrlAndRebuildsTrustedReturnTarget() {
        LoginCodePayloadParser.ParsedPayload parsed = LoginCodePayloadParser.parse(
            "https://btcpay.example/login?returnUrl=https%3A%2F%2Fevil.test%2Fadmin&LoginCode=short%2Bcode", ORIGIN);
        assertEquals(ORIGIN, parsed.origin);
        assertEquals("short+code", parsed.loginCode);
        String login = LoginCodePayloadParser.buildTrustedLoginUrl(parsed, DISPLAY);
        assertTrue(login.startsWith(ORIGIN + "/login?LoginCode=short%2Bcode"));
        assertFalse(login.contains("evil.test"));
        assertTrue(LoginCodePayloadParser.isTrustedLoginUrl(login, ORIGIN, DISPLAY));
    }

    @Test public void parsesLegacyCodeBaseUrlFormAndIgnoresEmail() {
        LoginCodePayloadParser.ParsedPayload parsed = LoginCodePayloadParser.parse(
            "one-time-code;https://btcpay.example;operator@example.com", "");
        assertEquals(ORIGIN, parsed.origin);
        assertEquals("one-time-code", parsed.loginCode);
    }

    @Test public void rawCodeRequiresConfiguredOrigin() {
        assertEquals("raw-code", LoginCodePayloadParser.parse("raw-code", ORIGIN).loginCode);
        assertRejected("raw-code", "");
    }

    @Test public void rejectsHttpForeignOriginsDuplicatesAndUnsupportedPaths() {
        assertRejected("http://btcpay.example/login?LoginCode=code", ORIGIN);
        assertRejected("https://evil.test/login?LoginCode=code", ORIGIN);
        assertRejected("https://btcpay.example/stores?LoginCode=code", ORIGIN);
        assertRejected("https://btcpay.example/login?LoginCode=one&logincode=two", ORIGIN);
        assertRejected("code;https://evil.test;operator@example.com", ORIGIN);
    }

    @Test public void trustedLoginValidationRejectsQrChosenReturnAndOtherOrigins() {
        String maliciousReturn = ORIGIN + "/login?LoginCode=code&returnUrl=%2Fstores";
        assertFalse(LoginCodePayloadParser.isTrustedLoginUrl(maliciousReturn, ORIGIN, DISPLAY));
        assertFalse(LoginCodePayloadParser.isTrustedLoginUrl(
            "https://evil.test/login?LoginCode=code&returnUrl=%2Fplugins%2Fbitcoin-rewards%2Fstore%2520id%2Fdisplay", ORIGIN, DISPLAY));
    }

    @Test public void trustedLoginRequiresExactlyCanonicalFields() {
        String returnUrl = "%2Fplugins%2Fbitcoin-rewards%2Fstore%2520id%2Fdisplay";
        assertTrusted(ORIGIN + "/login?LoginCode=code&returnUrl=" + returnUrl);
        assertUntrusted(ORIGIN + "/login?returnUrl=" + returnUrl + "&LoginCode=code");
        assertUntrusted(ORIGIN + "/login?logincode=code&returnUrl=" + returnUrl);
        assertUntrusted(ORIGIN + "/login?LoginCode=code&ReturnUrl=" + returnUrl);
        assertUntrusted(ORIGIN + "/login?LoginCode=code&returnUrl=" + returnUrl + "&extra=value");
        assertUntrusted(ORIGIN + "/login?LoginCode=one&LoginCode=two&returnUrl=" + returnUrl);
        assertUntrusted(ORIGIN + "/login?LoginCode=code&returnUrl=" + returnUrl + "&returnUrl=" + returnUrl);
    }

    @Test public void trustedLoginRejectsEmptyKeyOnlyMalformedAndFragmentForms() {
        String returnUrl = "%2Fplugins%2Fbitcoin-rewards%2Fstore%2520id%2Fdisplay";
        assertUntrusted(ORIGIN + "/login?LoginCode=&returnUrl=" + returnUrl);
        assertUntrusted(ORIGIN + "/login?LoginCode&returnUrl=" + returnUrl);
        assertUntrusted(ORIGIN + "/login?LoginCode=code&returnUrl=");
        assertUntrusted(ORIGIN + "/login?LoginCode=code&returnUrl");
        assertUntrusted(ORIGIN + "/login?LoginCode=bad%2&returnUrl=" + returnUrl);
        assertUntrusted(ORIGIN + "/login?LoginCode=code&returnUrl=%GG");
        assertUntrusted(ORIGIN + "/login?LoginCode=code&returnUrl=" + returnUrl + "#secret");
    }

    private static void assertTrusted(String loginUrl) {
        assertTrue(LoginCodePayloadParser.isTrustedLoginUrl(loginUrl, ORIGIN, DISPLAY));
    }

    private static void assertUntrusted(String loginUrl) {
        assertFalse(LoginCodePayloadParser.isTrustedLoginUrl(loginUrl, ORIGIN, DISPLAY));
    }

    private static void assertRejected(String payload, String configured) {
        try {
            LoginCodePayloadParser.parse(payload, configured);
            fail("Expected rejection");
        } catch (IllegalArgumentException expected) { }
    }
}
