package com.bitcoinrewards.nfcdisplay;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.*;

public class FinalContractBlockerTest {
    private static final UUID CHECK_IN=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CONFIRMATION=UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test public void frozenFixturesAreConsumedByTheProductionParser() throws Exception {
        JSONObject root=new JSONObject(resource("contracts/api-v2-fixtures.json"));
        assertEquals(2,root.getInt("protocolVersion"));
        JSONArray fixtures=root.getJSONArray("fixtures");
        PendingOperation operation=PendingOperation.prepared("store","device","fingerprint",4,"alice@example.com",CHECK_IN,2);
        for(int i=0;i<fixtures.length();i++){
            JSONObject fixture=fixtures.getJSONObject(i);
            if(fixture.has("request")) CheckInApiContract.validateFrozenRequest(fixture.getString("name"),fixture.getJSONObject("request"),operation,CONFIRMATION);
            if(fixture.has("response")) operation=CheckInApiContract.parseConfirmationOrCheckInFixture(fixture.getJSONObject("response").toString(),operation).operation;
        }
    }

    @Test public void cancellingIsPersistedBeforeAnyCancellationTransmission() throws Exception {
        PendingOperation awaiting=PendingOperation.started("store","device","fingerprint",4,"alice@example.com",CHECK_IN,2).awaiting(CONFIRMATION,1);
        PendingOperation cancelling=awaiting.cancelling();
        assertEquals(PendingOperation.Phase.CANCELLING,cancelling.phase);
        CheckInApiContract.Request request=CheckInApiContract.cancel("https://btcpay.example","token",cancelling);
        assertTrue(request.url.endsWith("/confirmations/"+CONFIRMATION+"/cancel"));
        assertTrue(request.body.contains("\"confirmationVersion\":1"));
    }

    @Test public void confirmingAndCancellingNeverDowngradeOnPendingOrAmbiguousReadback() {
        PendingOperation confirming=PendingOperation.started("store","device","fingerprint",4,"alice@example.com",CHECK_IN,2).awaiting(CONFIRMATION,1).confirming();
        PendingOperation cancelling=PendingOperation.started("store","device","fingerprint",4,"alice@example.com",CHECK_IN,2).awaiting(CONFIRMATION,1).cancelling();
        assertEquals(PendingOperation.Phase.CONFIRMING,CheckInApiContract.reconcilePhase(confirming,"PENDING",null,0));
        assertEquals(PendingOperation.Phase.CONFIRMING,CheckInApiContract.reconcilePhase(confirming,"AMBIGUOUS",CONFIRMATION,2));
        assertEquals(PendingOperation.Phase.CANCELLING,CheckInApiContract.reconcilePhase(cancelling,"PENDING",null,0));
        assertEquals(PendingOperation.Phase.CANCELLING,CheckInApiContract.reconcilePhase(cancelling,"AMBIGUOUS",CONFIRMATION,2));
    }

    @Test public void ambiguousConfirmationResumesPollingAndNeverOffersConfirmAgain() {
        PendingOperation confirming=PendingOperation.started("store","device","fingerprint",4,"alice@example.com",CHECK_IN,2).awaiting(CONFIRMATION,1).confirming();
        String ambiguous="{\"status\":\"AMBIGUOUS\",\"storeId\":\"store\",\"deviceId\":\"device\",\"tokenGeneration\":4,\"destination\":\"alice@example.com\",\"checkInId\":\""+CHECK_IN+"\",\"confirmationId\":\""+CONFIRMATION+"\",\"confirmationVersion\":2,\"protocolVersion\":2,\"resultCode\":\"reconciliation_required\",\"payoutState\":\"MANUAL_REVIEW\"}";
        CheckInApiContract.ConfirmationResponse parsed=CheckInApiContract.parseConfirmationResponse(ambiguous,confirming);
        assertEquals(PendingOperation.Phase.CONFIRMING,parsed.operation.phase);
        assertFalse(parsed.presentConfirmation());
        assertFalse(parsed.terminal());
    }

    private static String resource(String path)throws Exception{try(InputStream in=FinalContractBlockerTest.class.getClassLoader().getResourceAsStream(path)){assertNotNull(in);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
}
