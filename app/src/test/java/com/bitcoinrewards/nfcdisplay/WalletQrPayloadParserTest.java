package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WalletQrPayloadParserTest {
    private static final String GENERIC_LNURL_PAY =
        "lnurl1dp68gurn8ghj7cnjv4jh5tn5d9c8xtmvde6hymrs9anhyet9denx77pcxycryj4nyfk";

    @Test
    public void parse_acceptsLowercaseLnurlCandidate() {
        WalletQrPayloadParser.ParsedPayload parsed = WalletQrPayloadParser.parse(GENERIC_LNURL_PAY);

        assertEquals(WalletQrPayloadParser.Kind.LNURL, parsed.kind);
        assertEquals(GENERIC_LNURL_PAY, parsed.value);
    }

    @Test
    public void parse_acceptsUppercaseLnurlCandidateWithoutChangingIt() {
        String uppercase = GENERIC_LNURL_PAY.toUpperCase();

        WalletQrPayloadParser.ParsedPayload parsed = WalletQrPayloadParser.parse(uppercase);

        assertEquals(WalletQrPayloadParser.Kind.LNURL, parsed.kind);
        assertEquals(uppercase, parsed.value);
    }

    @Test
    public void parse_acceptsOneCaseInsensitiveLightningWrapper() {
        WalletQrPayloadParser.ParsedPayload parsed =
            WalletQrPayloadParser.parse("LiGhTnInG:" + GENERIC_LNURL_PAY);

        assertEquals(WalletQrPayloadParser.Kind.LNURL, parsed.kind);
        assertEquals(GENERIC_LNURL_PAY, parsed.value);
    }

    @Test
    public void parse_acceptsLongLnurlUsedByRealProviders() {
        String longLnurl = encodeLnurl("https://example.com/.well-known/lnurlp/" + spaces(80).replace(' ', 'a'));
        WalletQrPayloadParser.ParsedPayload parsed = WalletQrPayloadParser.parse(longLnurl);

        assertEquals(WalletQrPayloadParser.Kind.LNURL, parsed.kind);
        assertEquals(longLnurl, parsed.value);
    }

    @Test
    public void parse_acceptsLightningAddressAndPreservesCase() {
        WalletQrPayloadParser.ParsedPayload parsed = WalletQrPayloadParser.parse("Alice@Example.COM");

        assertEquals(WalletQrPayloadParser.Kind.LIGHTNING_ADDRESS, parsed.kind);
        assertEquals("Alice@Example.COM", parsed.value);
    }

    @Test
    public void parsedPayload_usesDestinationSafeDisplayLabels() {
        WalletQrPayloadParser.ParsedPayload lnurl = WalletQrPayloadParser.parse(GENERIC_LNURL_PAY);
        WalletQrPayloadParser.ParsedPayload address = WalletQrPayloadParser.parse("Alice@Example.COM");

        assertEquals("LNURL-pay code", lnurl.displayLabel());
        assertEquals("Ali…@Example.COM", address.displayLabel());
    }

    @Test
    public void parse_rejectsInvalidOrUnsupportedQrPayloads() {
        assertRejected(GENERIC_LNURL_PAY.substring(0, GENERIC_LNURL_PAY.length() - 1) + "q");
        assertRejected("lnURL" + GENERIC_LNURL_PAY.substring(5));
        assertRejected("lightning:lightning:" + GENERIC_LNURL_PAY);
        assertRejected("lightning: " + GENERIC_LNURL_PAY);
        assertRejected("lnbc1invoice");
        assertRejected("lntb1invoice");
        assertRejected("https://example.com/.well-known/lnurlp/alice");
        assertRejected("lnurlp://example.com/alice");
        assertRejected("lnurlw://example.com/withdraw");
        assertRejected("nostr:npub1example");
        assertRejected("bitcoin:bc1qexample");
        assertRejected("bc1qexample");
        assertRejected("alice@@example.com");
        assertRejected("alice@-example.com");
    }

    @Test
    public void parse_enforcesUtf8AndControlCharacterBoundaries() {
        String allowedOuterWhitespace = " \t\r\n";
        WalletQrPayloadParser.ParsedPayload parsed = WalletQrPayloadParser.parse(
            allowedOuterWhitespace + GENERIC_LNURL_PAY + allowedOuterWhitespace);
        assertEquals(GENERIC_LNURL_PAY, parsed.value);

        String exactly4096Bytes = GENERIC_LNURL_PAY + spaces(4096 - GENERIC_LNURL_PAY.length());
        assertEquals(GENERIC_LNURL_PAY, WalletQrPayloadParser.parse(exactly4096Bytes).value);

        assertRejected(exactly4096Bytes + " ");
        assertRejected("\u000b" + GENERIC_LNURL_PAY);
        assertRejected("\u000c" + GENERIC_LNURL_PAY);
        assertRejected(GENERIC_LNURL_PAY.substring(0, 12) + "\u0000" + GENERIC_LNURL_PAY.substring(12));
        assertRejected("\ud800" + GENERIC_LNURL_PAY);
        assertRejected(null);
        assertRejected(" \t\r\n");
    }

    private static String encodeLnurl(String url) {
        final String charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
        byte[] bytes = url.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.util.ArrayList<Integer> data = new java.util.ArrayList<>();
        int accumulator = 0;
        int bits = 0;
        for (byte item : bytes) {
            accumulator = (accumulator << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                data.add((accumulator >> bits) & 31);
            }
        }
        if (bits > 0) data.add((accumulator << (5 - bits)) & 31);

        int[] values = new int[data.size() + 6];
        for (int i = 0; i < data.size(); i++) values[i] = data.get(i);
        int polymod = 1;
        String hrp = "lnurl";
        for (int i = 0; i < hrp.length(); i++) polymod = polymodStep(polymod) ^ (hrp.charAt(i) >> 5);
        polymod = polymodStep(polymod);
        for (int i = 0; i < hrp.length(); i++) polymod = polymodStep(polymod) ^ (hrp.charAt(i) & 31);
        for (int value : values) polymod = polymodStep(polymod) ^ value;
        polymod ^= 1;
        for (int i = 0; i < 6; i++) values[data.size() + i] = (polymod >> (5 * (5 - i))) & 31;

        StringBuilder result = new StringBuilder("lnurl1");
        for (int value : values) result.append(charset.charAt(value));
        return result.toString();
    }

    private static int polymodStep(int pre) {
        int bottom = pre >>> 25;
        int checksum = (pre & 0x1ffffff) << 5;
        if ((bottom & 1) != 0) checksum ^= 0x3b6a57b2;
        if ((bottom & 2) != 0) checksum ^= 0x26508e6d;
        if ((bottom & 4) != 0) checksum ^= 0x1ea119fa;
        if ((bottom & 8) != 0) checksum ^= 0x3d4233dd;
        if ((bottom & 16) != 0) checksum ^= 0x2a1462b3;
        return checksum;
    }

    private static String spaces(int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(' ');
        return result.toString();
    }

    private static void assertRejected(String payload) {
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> WalletQrPayloadParser.parse(payload));
    }
}
