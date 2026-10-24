package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class CredentialUpdateStateTest {
    @Test public void everyIncompleteCredentialWriteOrderFailsClosed() {
        FakeMetadata metadata=new FakeMetadata(); FakeToken token=new FakeToken();
        CredentialUpdateState updater=new CredentialUpdateState(metadata,token);

        metadata.failPending=true;
        assertFalse(updater.update("https://btcpay.example","store","device","secret"));
        assertFalse(updater.usable()); assertFalse(metadata.pending);

        metadata.failPending=false; token.failSave=true;
        assertFalse(updater.update("https://btcpay.example","store","device","secret"));
        assertTrue(metadata.pending); assertFalse(metadata.onboarded); assertFalse(updater.usable()); assertEquals("",updater.token());

        token.failSave=false; metadata.failFinal=true;
        assertFalse(updater.update("https://btcpay.example","store","device","secret"));
        assertTrue(metadata.pending); assertFalse(metadata.onboarded); assertFalse(updater.usable()); assertEquals("",updater.token());

        metadata.failFinal=false; metadata.corruptFinalReadback=true;
        assertFalse(updater.update("https://btcpay.example","store","device","secret"));
        assertTrue(metadata.pending); assertFalse(metadata.onboarded); assertFalse(updater.usable());
    }

    @Test public void successfulFinalCommitAtomicallyPublishesMatchingIdentity() {
        FakeMetadata metadata=new FakeMetadata(); FakeToken token=new FakeToken();
        CredentialUpdateState updater=new CredentialUpdateState(metadata,token);
        assertTrue(updater.update("https://btcpay.example","store","device","secret"));
        assertFalse(metadata.pending); assertTrue(metadata.onboarded); assertTrue(updater.usable());
        assertEquals("secret",updater.token()); assertEquals(1,metadata.generation);
        assertFalse(metadata.fingerprint.contains("secret"));
    }

    private static final class FakeToken implements CredentialUpdateState.TokenStorage {
        String value=""; boolean failSave;
        public boolean save(String v){if(failSave)return false;value=v;return true;}
        public String load(){return value;}
    }
    private static final class FakeMetadata implements CredentialUpdateState.MetadataStorage {
        String origin="",store="",device="",fingerprint="";long generation;boolean pending,onboarded,failPending,failFinal,corruptFinalReadback;
        public CredentialUpdateState.Metadata read(){return new CredentialUpdateState.Metadata(origin,store,device,fingerprint,generation,pending,onboarded);}
        public boolean markPending(){if(failPending)return false;pending=true;onboarded=false;return true;}
        public boolean finish(CredentialUpdateState.Metadata m){if(failFinal)return false;if(corruptFinalReadback){pending=true;onboarded=false;return true;}origin=m.origin;store=m.store;device=m.device;fingerprint=m.fingerprint;generation=m.generation;pending=false;onboarded=true;return true;}
    }
}
