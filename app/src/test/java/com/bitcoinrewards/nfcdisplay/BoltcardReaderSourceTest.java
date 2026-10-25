package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoltcardReaderSourceTest {
    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(relative)), StandardCharsets.UTF_8);
    }

    @Test public void readerModeIsLifecycleAndRecoveryGated() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(main.contains("nfcAdapter.enableReaderMode("));
        assertTrue(main.contains("NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B"));
        assertTrue(main.contains("trustedDisplayRecovery != null && trustedDisplayRecovery.isRecovering()"));
        assertTrue(main.contains("nfcReaderGate.update("));
        assertTrue(main.contains("activityResumed = false;\n        nfcReaderGate.update(false, scannerPageEligible,"));
        assertTrue(main.contains("@Override protected void onDestroy() {\n        activityResumed = false;\n        nfcReaderGate.update(false, scannerPageEligible,"));
        assertTrue(main.contains("inlineAuthenticationFailure != null"));
        assertTrue(main.contains("inlineSubmissionInFlight"));
    }

    @Test public void readsOnlyBoundedNdefUriWithoutLegacyHceOrRawApduPath() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(main.contains("Ndef ndef = Ndef.get(tag)"));
        assertTrue(main.contains("NdefFormatable.get(tag) != null || IsoDep.get(tag) != null"));
        assertTrue(main.contains("message.toByteArray().length > BoltcardPayloadParser.MAX_NDEF_BYTES"));
        assertTrue(main.contains("records.length != 1"));
        assertTrue(main.contains("BoltcardPayloadParser.extractUri("));
        assertFalse(main.contains("NdefHostCardEmulationService"));
        assertFalse(main.contains("transceive("));
    }

    @Test public void boltcardUsesDedicatedCookieAuthenticatedContractAndQrContractIsUnchanged() throws Exception {
        String main = source("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java");
        assertTrue(main.contains("BoltcardCheckInEndpoint.fromLegacyCheckInUrl(legacyApiUrl)"));
        assertTrue(main.contains("body.put(\"boltcardUrl\", boltcardUrl)"));
        assertTrue(main.contains("body.put(\"deviceId\", LEGACY_INLINE_DEVICE_ID)"));
        assertTrue(main.contains("InlineCheckInAuthentication.cookieForTrustedApi("));
        assertTrue(main.contains("body.put(\"lightningAddress\", payload.value)"));
        assertTrue(main.contains("WalletQrPayloadParser.parse(result.getText())"));
        assertFalse(main.contains("WalletQrPayloadParser.parse(boltcardUrl)"));
        assertTrue(main.contains("setInstanceFollowRedirects(false)"));
        assertTrue(main.contains("setRewardsScannerStatus(\"Wallet connected — \" + label)"));
        assertTrue(main.contains("showSuccessOverlay(label)"));
    }
}
