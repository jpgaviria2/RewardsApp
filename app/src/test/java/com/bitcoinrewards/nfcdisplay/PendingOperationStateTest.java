package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class PendingOperationStateTest {
    private static PendingOperation operation() { return PendingOperation.started("store","device","fingerprint",3,"alice@example.com",UUID.fromString("11111111-1111-4111-8111-111111111111"),2); }

    @Test public void typedOperationRoundTripsAndMalformedPersistenceLocksScanner() {
        PendingOperation decoded=PendingOperation.fromJson(operation().toJson().toString());
        assertEquals("store",decoded.storeId); assertEquals(3,decoded.tokenGeneration); assertEquals(PendingOperation.Phase.POLLING,decoded.phase);
        ScannerOperationState state=new ScannerOperationState("{broken");
        assertTrue(state.isLocked()); assertTrue(state.isMalformed()); assertFalse(state.mayScan());
    }

    @Test public void durableSaveRequiresCommitAndReadBackVerification() {
        FakeStorage storage=new FakeStorage(); PendingOperationRepository repo=new PendingOperationRepository(storage);
        storage.commitResult=false; assertFalse(repo.save(operation())); assertTrue(repo.load().malformed);
        storage.commitResult=true; storage.corruptReadBack=true; assertFalse(repo.save(operation())); assertTrue(repo.load().malformed);
        storage.corruptReadBack=false; assertTrue(repo.save(operation())); assertEquals("store",repo.load().operation.storeId);
    }

    @Test public void malformedDurableStateCountsAsAnActiveLock() {
        assertTrue(PendingOperationRepository.isLocked(new PendingOperationRepository.LoadResult(null,true)));
        assertTrue(PendingOperationRepository.isLocked(new PendingOperationRepository.LoadResult(operation(),false)));
        assertFalse(PendingOperationRepository.isLocked(new PendingOperationRepository.LoadResult(null,false)));
    }

    @Test public void preparedSubmissionIsLockedAndResumesTheSameClientIdentity() {
        PendingOperation prepared=PendingOperation.prepared("store","device","fingerprint",3,"alice@example.com",
            UUID.fromString("11111111-1111-4111-8111-111111111111"),2);
        PendingOperation decoded=PendingOperation.fromJson(prepared.toJson().toString());
        assertEquals(PendingOperation.Phase.SUBMITTING,decoded.phase);
        assertEquals(prepared.checkInId,decoded.checkInId);
        ScannerOperationState recreated=new ScannerOperationState(prepared.toJson().toString());
        assertTrue(recreated.isLocked());
        assertEquals(ScannerOperationState.Action.RESUME_SUBMISSION,recreated.onResume());
    }

    @Test public void productionGateKeepsOneWorkerAcrossUiDetachAndDisconnectsOnShutdown() {
        OperationTaskGate gate=new OperationTaskGate();
        FutureTask<Void> first=new FutureTask<>(()->null);
        FutureTask<Void> second=new FutureTask<>(()->null);
        assertTrue(gate.claim(first)); assertFalse(gate.claim(second));
        gate.uiDetached(); assertTrue(gate.running()); assertFalse(first.isCancelled());
        AtomicBoolean disconnected=new AtomicBoolean();
        gate.connection(()->disconnected.set(true));
        gate.shutdown();
        assertTrue(first.isCancelled()); assertTrue(disconnected.get()); assertFalse(gate.running());
        assertTrue(gate.claim(second));
        ScannerOperationState recreated=new ScannerOperationState(operation().toJson().toString());
        assertEquals(ScannerOperationState.Action.RESUME_POLLING,recreated.onResume());
        assertEquals(ScannerOperationState.Action.NONE,recreated.onResume());
    }

    @Test public void localCancelKeepsLockAndOnlyAuthoritativeTerminalClears() {
        ScannerOperationState state=new ScannerOperationState(operation().toJson().toString());
        state.localCancel(); assertTrue(state.isLocked()); assertFalse(state.mayScan()); assertEquals(ScannerOperationState.Action.OFFER_RETRY_OR_CANCEL_OPERATION,state.nextAction());
        assertFalse(state.authoritativeStatus("pending")); assertTrue(state.isLocked());
        assertTrue(state.authoritativeStatus("cancelled")); assertFalse(state.isLocked()); assertTrue(state.mayScan());
    }

    @Test public void persistedOperationMustMatchCurrentStoreDeviceCredentialAndGeneration() {
        PendingOperation operation=operation();
        assertTrue(operation.matchesIdentity("store","device","fingerprint",3));
        assertFalse(operation.matchesIdentity("other","device","fingerprint",3));
        assertFalse(operation.matchesIdentity("store","other","fingerprint",3));
        assertFalse(operation.matchesIdentity("store","device","replacement",3));
        assertFalse(operation.matchesIdentity("store","device","fingerprint",4));
    }

    @Test public void safeLegacySubmissionIsReparsedAndMigratedBeforeItCanBeSubmitted() throws Exception {
        String raw=legacy("Alice@Example.COM",PendingOperation.Phase.SUBMITTING);
        PendingOperation migrated=PendingOperation.fromJson(raw);
        assertEquals("alice@example.com",migrated.destination);
        assertEquals("Alice@Example.COM",migrated.scanText);
        assertEquals("Alice",migrated.localDestinationLabel);
        PendingOperation restored=PendingOperation.fromJson(migrated.toJson().toString());
        assertEquals(migrated.destination,restored.destination);assertEquals(migrated.scanSha256,restored.scanSha256);
    }

    @Test public void safeLegacyPollingKeepsTheAlreadySubmittedCorrelationValue() throws Exception {
        PendingOperation migrated=PendingOperation.fromJson(legacy("Alice@Example.COM",PendingOperation.Phase.POLLING));
        assertEquals("Alice@Example.COM",migrated.destination);
        assertEquals("Alice@Example.COM",migrated.scanText);
        assertEquals("Alice",migrated.localDestinationLabel);
    }

    @Test public void unsafeLegacySubmittingAndPollingBecomeManualLocks() throws Exception {
        for(PendingOperation.Phase phase:new PendingOperation.Phase[]{PendingOperation.Phase.SUBMITTING,PendingOperation.Phase.POLLING}){
            String raw=legacy("https://evil.example/not-lnurl",phase);
            assertThrows(IllegalArgumentException.class,()->PendingOperation.fromJson(raw));
            ScannerOperationState state=new ScannerOperationState(raw);
            assertTrue(state.isLocked());assertTrue(state.isMalformed());assertEquals(ScannerOperationState.Action.NONE,state.onResume());
            FakeStorage storage=new FakeStorage();storage.value=raw;
            PendingOperationRepository.LoadResult loaded=new PendingOperationRepository(storage).load();
            assertTrue(loaded.malformed);assertNull(loaded.operation);assertTrue(PendingOperationRepository.isLocked(loaded));
        }
    }

    private static String legacy(String destination,PendingOperation.Phase phase) throws Exception {
        return new JSONObject().put("storeId","store").put("deviceId","device").put("credentialFingerprint","fingerprint")
            .put("tokenGeneration",3).put("destination",destination).put("checkInId","11111111-1111-4111-8111-111111111111")
            .put("confirmationId",JSONObject.NULL).put("protocolVersion",2).put("confirmationVersion",0).put("phase",phase.name()).toString();
    }

    private static final class FakeStorage implements PendingOperationRepository.Storage {
        String value; boolean commitResult=true,corruptReadBack;
        public boolean commit(String v){value=v;return commitResult;}
        public String read(){return corruptReadBack?"corrupt":value;}
        public boolean clear(){value=null;return true;}
    }
}
