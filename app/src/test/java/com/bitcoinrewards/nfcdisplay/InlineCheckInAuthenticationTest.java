package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InlineCheckInAuthenticationTest {
    private static final String ORIGIN = "https://btcpay.example";
    private static final String API = ORIGIN + "/plugins/bitcoin-rewards/store/check-in/api";

    @Test public void returnsCookieForTrustedSameOriginApiUrl() {
        AtomicInteger calls = new AtomicInteger();
        String cookie = InlineCheckInAuthentication.cookieForTrustedApi(ORIGIN, API, url -> {
            calls.incrementAndGet();
            assertEquals(API, url);
            return "session=secret; other=value";
        });
        assertEquals("session=secret; other=value", cookie);
        assertEquals(1, calls.get());
    }

    @Test public void neverQueriesOrReturnsCookieForForeignOrInsecureUrls() {
        AtomicInteger calls = new AtomicInteger();
        InlineCheckInAuthentication.CookieLookup lookup = url -> {
            calls.incrementAndGet();
            return "session=secret";
        };
        assertNull(InlineCheckInAuthentication.cookieForTrustedApi(ORIGIN, "https://evil.example/check-in/api", lookup));
        assertNull(InlineCheckInAuthentication.cookieForTrustedApi(ORIGIN, "http://btcpay.example/check-in/api", lookup));
        assertNull(InlineCheckInAuthentication.cookieForTrustedApi(ORIGIN, "https://btcpay.example.evil.test/check-in/api", lookup));
        assertEquals(0, calls.get());
    }

    @Test public void rejectsMissingOrHeaderInjectionCookies() {
        assertNull(InlineCheckInAuthentication.cookieForTrustedApi(ORIGIN, API, url -> null));
        assertNull(InlineCheckInAuthentication.cookieForTrustedApi(ORIGIN, API, url -> "  "));
        assertNull(InlineCheckInAuthentication.cookieForTrustedApi(ORIGIN, API, url -> "session=x\r\nX-Leak: yes"));
    }

    @Test public void redirectsUnauthorizedAndForbiddenMeanLoginExpired() {
        assertTrue(InlineCheckInAuthentication.isLoginExpired(302));
        assertTrue(InlineCheckInAuthentication.isLoginExpired(303));
        assertTrue(InlineCheckInAuthentication.isLoginExpired(307));
        assertTrue(InlineCheckInAuthentication.isLoginExpired(308));
        assertTrue(InlineCheckInAuthentication.isLoginExpired(401));
        assertTrue(InlineCheckInAuthentication.isLoginExpired(403));
        assertFalse(InlineCheckInAuthentication.isLoginExpired(404));
        assertFalse(InlineCheckInAuthentication.isLoginExpired(299));
        assertFalse(InlineCheckInAuthentication.isLoginExpired(400));
        assertFalse(InlineCheckInAuthentication.isLoginExpired(500));
    }

    @Test public void forbiddenMessageExplainsStoreSettingsPermission() {
        assertTrue(InlineCheckInAuthentication.failureMessage(403).contains("missing store-settings permission"));
        assertEquals("BTCPay login expired; scan login QR again", InlineCheckInAuthentication.failureMessage(302));
        assertEquals("BTCPay login expired; scan login QR again", InlineCheckInAuthentication.failureMessage(401));
        assertTrue(InlineCheckInAuthentication.failureMessage(404).contains("endpoint was not found"));
    }
}