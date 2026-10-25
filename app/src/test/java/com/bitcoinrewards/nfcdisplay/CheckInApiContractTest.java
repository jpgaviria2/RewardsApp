package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class CheckInApiContractTest {
    private static final String CHECK_IN = "11111111-1111-4111-8111-111111111111";
    private static final String CONFIRMATION = "22222222-2222-4222-8222-222222222222";

    @Test public void lnurlPayUsesTheIdenticalExistingCheckInAndConfirmationPath() throws Exception {
        String lnurl="lnurl1dp68gurn8ghj7urp0yhx27rpd4cxcef0d3h82unvwqhkzmrfvdjsvmqd25";
        WalletQrPayloadParser.ParsedPayload scan=WalletQrPayloadParser.parse(" lightning:"+lnurl+"\r\n");
        PendingOperation prepared=PendingOperation.prepared("store","device","fingerprint",4,scan,UUID.fromString(CHECK_IN),2);
        CheckInApiContract.Request create=CheckInApiContract.checkIn("https://btcpay.example","token",prepared);
        assertEquals("https://btcpay.example/plugins/bitcoin-rewards/store/check-in/api/v2",create.url);
        assertEquals(lnurl,new org.json.JSONObject(create.body).getString("destination"));
        assertFalse(create.body.contains("lnurlEndpoint"));
        String pending="{\"status\":\"PENDING\",\"storeId\":\"store\",\"deviceId\":\"device\",\"tokenGeneration\":4,\"destination\":\""+lnurl+"\",\"checkInId\":\""+CHECK_IN+"\",\"protocolVersion\":2}";
        PendingOperation polling=CheckInApiContract.parseCheckInResponse(pending).correlate(prepared);
        assertEquals(PendingOperation.Phase.POLLING,polling.phase);
        assertEquals(scan.scanSha256,polling.scanSha256);
        assertEquals(" lightning:"+lnurl+"\r\n",polling.scanText);
        assertEquals(scan.displayLabel(),polling.localDestinationLabel);
        String awaiting="{\"status\":\"AWAITING_CONFIRMATION\",\"storeId\":\"store\",\"deviceId\":\"device\",\"tokenGeneration\":4,\"destination\":\""+lnurl+"\",\"checkInId\":\""+CHECK_IN+"\",\"confirmationId\":\""+CONFIRMATION+"\",\"confirmationVersion\":1,\"protocolVersion\":2,\"amountSatoshis\":321,\"destinationLabel\":\"server metadata\"}";
        CheckInApiContract.ConfirmationResponse confirmation=CheckInApiContract.parseConfirmationResponse(awaiting,polling);
        assertEquals(321,confirmation.amountSatoshis);assertEquals(PendingOperation.Phase.AWAITING_APPROVAL,confirmation.operation.phase);
        assertEquals("Wallet",confirmation.operation.localDestinationLabel);
    }

    @Test public void lowerConfirmationVersionsAreRejectedBeforeStateMutation() {
        PendingOperation operation=PendingOperation.started("store","device","fingerprint",4,"alice@example.com",UUID.fromString(CHECK_IN),2).awaiting(UUID.fromString(CONFIRMATION),2);
        String lower="{\"status\":\"AWAITING_CONFIRMATION\",\"storeId\":\"store\",\"deviceId\":\"device\",\"tokenGeneration\":4,\"destination\":\"alice@example.com\",\"checkInId\":\""+CHECK_IN+"\",\"confirmationId\":\""+CONFIRMATION+"\",\"confirmationVersion\":1,\"protocolVersion\":2,\"amountSatoshis\":321,\"destinationLabel\":\"alice@example.com\"}";
        assertThrows(IllegalArgumentException.class,()->CheckInApiContract.parseConfirmationResponse(lower,operation));
    }

    @Test public void mixedCaseLightningAddressUsesCanonicalContractValueAndPreservesLocalEvidence() throws Exception {
        String raw=" lightning:Alice@Example.COM\r\n";
        WalletQrPayloadParser.ParsedPayload scan=WalletQrPayloadParser.parse(raw);
        PendingOperation prepared=PendingOperation.prepared("store","device","fingerprint",4,scan,UUID.fromString(CHECK_IN),2);
        CheckInApiContract.Request create=CheckInApiContract.checkIn("https://btcpay.example","token",prepared);
        assertEquals("alice@example.com",new org.json.JSONObject(create.body).getString("destination"));
        assertEquals(raw,prepared.scanText);assertEquals(scan.scanSha256,prepared.scanSha256);assertEquals("Alice",prepared.localDestinationLabel);
        String pending=validCheckIn().replace("Alice@Example.com","alice@example.com");
        PendingOperation polling=CheckInApiContract.parseCheckInResponse(pending).correlate(prepared);
        assertEquals("alice@example.com",polling.destination);assertEquals(raw,polling.scanText);assertEquals("Alice",polling.localDestinationLabel);
        PendingOperation restored=PendingOperation.fromJson(polling.toJson().toString());
        assertEquals(polling.destination,restored.destination);assertEquals(polling.scanText,restored.scanText);assertEquals(polling.scanSha256,restored.scanSha256);assertEquals(polling.localDestinationLabel,restored.localDestinationLabel);
    }

    @Test public void v2RequestsEncodeEveryPathSegmentAndUseOrgJsonBodies() throws Exception {
        PendingOperation operation = PendingOperation.started("store */?é", "device", "fingerprint", 4,
            "Alice@Example.com", UUID.fromString(CHECK_IN), 2);
        CheckInApiContract.Request request = CheckInApiContract.pending("https://btcpay.example", "token", operation);
        assertEquals("https://btcpay.example/plugins/bitcoin-rewards/store%20%2A%2F%3F%C3%A9/check-in/api/v2/check-ins/" + CHECK_IN + "?deviceId=device&tokenGeneration=4&protocolVersion=2", request.url);
        assertEquals("Bearer token", request.authorization);
        assertFalse(request.followRedirects);
        assertTrue(request.connectTimeoutMs <= 10_000);
        assertTrue(request.readTimeoutMs <= 15_000);
        assertTrue(request.maxResponseBytes <= 65_536);
        assertEquals("store%20%2A%2F%3F%C3%A9",PathSegmentCodec.encode("store */?é"));
    }

    @Test public void displayUrlUsesTheSameStrictPathSegmentEncoder() throws Exception {
        String source=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(source.contains("PathSegmentCodec.encode(getStoreId(c))"));
        assertFalse(source.contains("URLEncoder.encode(getStoreId(c)"));
    }

    @Test public void originMustBeExactHttpsDefaultPortWithoutUserinfoFragmentOrBasePath() {
        assertEquals("https://btcpay.example", ApiOrigin.requireConfigured("https://BTCPAY.example:443/").toString());
        assertRejectedOrigin("http://btcpay.example");
        assertRejectedOrigin("https://btcpay.example:8443");
        assertRejectedOrigin("https://user@btcpay.example");
        assertRejectedOrigin("https://btcpay.example/path");
        assertRejectedOrigin("https://btcpay.example/#fragment");
        assertFalse(ApiOrigin.sameOrigin("https://btcpay.example", "https://evil.example/"));
        assertFalse(ApiOrigin.sameOrigin("https://btcpay.example", "http://btcpay.example/"));
        assertTrue(ApiOrigin.sameOrigin("https://btcpay.example", "https://BTCPAY.example:443/display"));
    }

    @Test public void exactJsonRejectsDuplicateKeysTrailingDataExtrasAndWrongTypes() {
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(
            validCheckIn().replace("\"storeId\":\"store\"", "\"storeId\":\"store\",\"storeId\":\"evil\"")));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(
            validCheckIn().replace("\"storeId\":\"store\"", "\"storeId\":\"store\",\"stor\\u0065Id\":\"evil\"")));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn() + " null"));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn().replace("}", ",\"extra\":1}")));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn().replace("\"protocolVersion\":2", "\"protocolVersion\":2.0")));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn().replace(CHECK_IN, "not-a-uuid")));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn().replace(CHECK_IN, "1-1-1-1-1")));
        assertThrows(IllegalArgumentException.class, () -> PendingOperation.exactUuid("AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn().replace(CHECK_IN, "00000000-0000-0000-0000-000000000000")));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn().replace(CHECK_IN, "11111111-1111-0111-0111-111111111111")));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseCheckInResponse(validCheckIn().replace("\"protocolVersion\":2", "\"protocolVersion\":0")));
    }

    @Test public void responsesCorrelateExactlyAndEnforceCaps() throws Exception {
        CheckInApiContract.CheckInResponse response = CheckInApiContract.parseCheckInResponse(validCheckIn());
        PendingOperation operation = response.correlate(PendingOperation.prepared("store", "device", "fingerprint", 4,
            "Alice@Example.com", UUID.fromString(CHECK_IN), 2));
        String ready = "{\"status\":\"AWAITING_CONFIRMATION\",\"storeId\":\"store\",\"deviceId\":\"device\"," +
            "\"tokenGeneration\":4,\"destination\":\"Alice@Example.com\",\"checkInId\":\"" + CHECK_IN + "\"," +
            "\"confirmationId\":\"" + CONFIRMATION + "\",\"confirmationVersion\":1,\"protocolVersion\":2,\"amountSatoshis\":321,\"destinationLabel\":\"Ali…@Example.com\"}";
        CheckInApiContract.ConfirmationResponse confirmation = CheckInApiContract.parseConfirmationResponse(ready, operation);
        assertEquals(321, confirmation.amountSatoshis);
        assertEquals(PendingOperation.Phase.AWAITING_APPROVAL, confirmation.operation.phase);
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseConfirmationResponse(ready.replace("\"device\"", "\"other\""), operation));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseConfirmationResponse(ready.replace("\"amountSatoshis\":321", "\"amountSatoshis\":0"), operation));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseConfirmationResponse(ready.replace("Ali…@Example.com", repeat('x', 257)), operation));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseConfirmationResponse(ready.replace("Ali…@Example.com", "   "), operation));
        assertThrows(IllegalArgumentException.class, () -> CheckInApiContract.parseConfirmationResponse(ready.replace("Ali…@Example.com", "safe\\u202Eevil"), operation));
    }

    @Test public void checkInUsesExactDurablyPreparedClientIdentity() throws Exception {
        PendingOperation prepared = PendingOperation.prepared("store", "device", "fingerprint", 4,
            "Alice@Example.com", UUID.fromString(CHECK_IN), CheckInApiContract.VERSION);
        assertEquals(PendingOperation.Phase.SUBMITTING, prepared.phase);
        CheckInApiContract.Request request = CheckInApiContract.checkIn(
            "https://btcpay.example", "token", prepared);
        assertTrue(request.body.contains("\"checkInId\":\"" + CHECK_IN + "\""));
        assertTrue(request.body.contains("\"protocolVersion\":2"));

        PendingOperation correlated = CheckInApiContract.parseCheckInResponse(validCheckIn()).correlate(prepared);
        assertEquals(PendingOperation.Phase.POLLING, correlated.phase);
        assertEquals(prepared.checkInId, correlated.checkInId);
        assertThrows(IllegalArgumentException.class, () ->
            CheckInApiContract.parseCheckInResponse(validCheckIn().replace(CHECK_IN,
                "33333333-3333-4333-8333-333333333333")).correlate(prepared));
    }

    @Test public void cancellationRequestIsCorrelatedAndCannotClearLocally() throws Exception {
        PendingOperation operation=PendingOperation.started("store","device","fingerprint",4,"Alice@Example.com",UUID.fromString(CHECK_IN),2)
            .awaiting(UUID.fromString(CONFIRMATION),1).cancelling();
        CheckInApiContract.Request request=CheckInApiContract.cancel("https://btcpay.example","token",operation);
        assertEquals("https://btcpay.example/plugins/bitcoin-rewards/store/check-in/api/v2/confirmations/"+CONFIRMATION+"/cancel",request.url);
        assertTrue(request.body.contains("\"checkInId\":\""+CHECK_IN+"\""));
        ScannerOperationState state=new ScannerOperationState(operation.toJson().toString());
        state.localCancel(); assertTrue(state.isLocked());
    }

    @Test public void durableOperationRejectsWrongProtocolVersionAndUnsafeUnicodeOnlyFields() {
        String persisted=PendingOperation.started("store","device","fingerprint",4,"Alice@Example.com",UUID.fromString(CHECK_IN),2).toJson().toString();
        assertThrows(IllegalArgumentException.class,()->PendingOperation.fromJson(persisted.replace("\"protocolVersion\":2","\"protocolVersion\":3")));
        assertThrows(IllegalArgumentException.class,()->PendingOperation.validateText("\u0301",32));
        assertThrows(IllegalArgumentException.class,()->PendingOperation.validateText("\uE000",32));
        assertThrows(IllegalArgumentException.class,()->PendingOperation.validateText("\u0378",32));
    }

    private static String validCheckIn() {
        return "{\"status\":\"PENDING\",\"storeId\":\"store\",\"deviceId\":\"device\",\"tokenGeneration\":4," +
            "\"destination\":\"Alice@Example.com\",\"checkInId\":\"" + CHECK_IN + "\",\"protocolVersion\":2}";
    }
    private static void assertRejectedOrigin(String value) { assertThrows(IllegalArgumentException.class, () -> ApiOrigin.requireConfigured(value)); }
    private static String repeat(char c, int n) { StringBuilder b = new StringBuilder(); while (n-- > 0) b.append(c); return b.toString(); }
}
