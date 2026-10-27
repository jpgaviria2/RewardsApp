package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.*;

public class StoreQrPayloadParserTest {
    private static final String ORIGIN = "https://btcpay.example";
    private static final String RAW_STORE_ID = "7YxK9mP3qR8sT2vW4zA6bC1dE5fG7hJ9kL3nP";

    @Test public void parsesRawBtcpayStoreIdQr() {
        StoreQrPayloadParser.ParsedPayload parsed = StoreQrPayloadParser.parse(RAW_STORE_ID);
        assertEquals("", parsed.origin);
        assertEquals(RAW_STORE_ID, parsed.storeId);
    }

    @Test public void parsesCanonicalRewardsDisplayUrl() {
        StoreQrPayloadParser.ParsedPayload parsed = StoreQrPayloadParser.parse(
            ORIGIN + "/plugins/bitcoin-rewards/store%20id/display");
        assertEquals(ORIGIN, parsed.origin);
        assertEquals("store id", parsed.storeId);
    }

    @Test public void acceptsCanonicalEncodedUnicodeAndReservedCharacters() {
        StoreQrPayloadParser.ParsedPayload parsed = StoreQrPayloadParser.parse(
            ORIGIN + "/plugins/bitcoin-rewards/caf%C3%A9%2Bwest/display");
        assertEquals("café+west", parsed.storeId);
    }

    @Test public void rejectsWrongSchemePortRouteAndExtraSegments() {
        assertRejected("http://btcpay.example/plugins/bitcoin-rewards/store/display");
        assertRejected("https://btcpay.example:8443/plugins/bitcoin-rewards/store/display");
        assertRejected(ORIGIN + "/stores/store");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store/check-in");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store/display/extra");
    }

    @Test public void rejectsCredentialsQueryFragmentEmptyAndEncodedSeparators() {
        assertRejected("https://user@btcpay.example/plugins/bitcoin-rewards/store/display");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store/display?next=evil");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store/display#fragment");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards//display");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store%2Fother/display");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store%5Cother/display");
    }

    @Test public void rejectsNonCanonicalAndMalformedEncoding() {
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/%73tore/display");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store+id/display");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/store%2/display");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/%FF/display");
    }

    @Test public void rejectsNullBlankAndOversizedPayloads() {
        assertRejected(null);
        assertRejected("   ");
        assertRejected(ORIGIN + "/plugins/bitcoin-rewards/" + repeat('a', 300) + "/display");
        assertRejected(repeat('a', 300));
    }

    @Test public void rejectsShortOrNonAlphanumericRawStoreIds() {
        assertRejected("short");
        assertRejected("7YxK9mP3qR8sT2vW4zA6bC1dE5fG7hJ9kL3-nP");
        assertRejected("7YxK9mP3qR8sT2vW4zA6bC1dE5fG7hJ9kL3/nP");
    }

    private static void assertRejected(String payload) {
        try {
            StoreQrPayloadParser.parse(payload);
            fail("Expected rejection");
        } catch (IllegalArgumentException expected) { }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
