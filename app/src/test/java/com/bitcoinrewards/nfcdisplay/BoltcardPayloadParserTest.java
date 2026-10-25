package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BoltcardPayloadParserTest {
    private static final String P = "00112233445566778899aabbccddeeff";
    private static final String C = "0011223344556677";
    private static final String URL = "lnurlw://cards.example.com/boltcards/api/v1/scan/card123?p=" + P + "&c=" + C;

    @Test public void extractsUriRecordAndRecognizesStandardLnbitsBoltcard() {
        byte[] suffix = URL.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[suffix.length + 1];
        System.arraycopy(suffix, 0, payload, 1, suffix.length);
        assertEquals(URL, BoltcardPayloadParser.requireBoltcard(
            BoltcardPayloadParser.extractUri((short) 1, new byte[] { 0x55 }, payload)));
    }

    @Test public void acceptsDefaultHttpsPortAndParameterOrder() {
        String value = "LNURLW://cards.example.com:443/boltcards/api/v1/scan/a?c=" + C + "&p=" + P;
        assertEquals(value, BoltcardPayloadParser.requireBoltcard(value));
    }

    @Test public void acceptsIssuerDefinedStandardBoltcardPath() {
        String value = "lnurlw://cards.example.com/custom/issuer/tap?p=" + P + "&c=" + C;
        assertEquals(value, BoltcardPayloadParser.requireBoltcard(value));
    }

    @Test public void rejectsNonUriRecordsInvalidUtf8AndOversizePayloads() {
        rejectExtract((short) 2, new byte[] { 0x55 }, new byte[] { 0, 'x' });
        rejectExtract((short) 1, new byte[] { 'T' }, new byte[] { 0, 'x' });
        rejectExtract((short) 1, new byte[] { 0x55 }, new byte[] { 0, (byte) 0xc3, 0x28 });
        rejectExtract((short) 1, new byte[] { 0x55 }, new byte[BoltcardPayloadParser.MAX_NDEF_BYTES + 1]);
    }

    @Test public void rejectsUntrustedOrMalformedBoltcardUrls() {
        reject("https://cards.example.com/boltcards/api/v1/scan/a?p=" + P + "&c=" + C);
        reject("lnurlw://user@cards.example.com/boltcards/api/v1/scan/a?p=" + P + "&c=" + C);
        reject("lnurlw://127.0.0.1/boltcards/api/v1/scan/a?p=" + P + "&c=" + C);
        reject("lnurlw://cards.example.com:444/boltcards/api/v1/scan/a?p=" + P + "&c=" + C);
        reject("lnurlw://cards.example.com/?p=" + P + "&c=" + C);
        reject("lnurlw://cards.example.com/boltcards/api/v1/scan/a?p=00&c=" + C);
        reject("lnurlw://cards.example.com/boltcards/api/v1/scan/a?p=" + P + "&c=00");
        reject("lnurlw://cards.example.com/boltcards/api/v1/scan/a?p=" + P + "&c=" + C + "&x=1");
        reject("lnurlw://cards.example.com/boltcards/api/v1/scan/a?p=" + P + "&p=" + P + "&c=" + C);
        reject(URL + "#fragment");
        reject(" " + URL);
    }

    private static void reject(String value) {
        try { BoltcardPayloadParser.requireBoltcard(value); fail("accepted " + value); }
        catch (IllegalArgumentException expected) { }
    }

    private static void rejectExtract(short tnf, byte[] type, byte[] payload) {
        try { BoltcardPayloadParser.extractUri(tnf, type, payload); fail("accepted invalid NDEF"); }
        catch (IllegalArgumentException expected) { }
    }
}
