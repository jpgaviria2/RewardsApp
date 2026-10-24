package com.bitcoinrewards.nfcdisplay;

/** Two-phase publication of an encrypted token and the metadata that identifies it. */
final class CredentialUpdateState {
    interface TokenStorage { boolean save(String value); String load(); }
    interface MetadataStorage { Metadata read(); boolean markPending(); boolean finish(Metadata value); }
    static final class Metadata {
        final String origin,store,device,fingerprint; final long generation; final boolean pending,onboarded;
        Metadata(String origin,String store,String device,String fingerprint,long generation,boolean pending,boolean onboarded){this.origin=clean(origin);this.store=clean(store);this.device=clean(device);this.fingerprint=clean(fingerprint);this.generation=generation;this.pending=pending;this.onboarded=onboarded;}
        Metadata published(String nextOrigin,String nextStore,String nextDevice,TokenIdentity identity){return new Metadata(nextOrigin,nextStore,nextDevice,identity.fingerprint,identity.generation,false,true);}
        private static String clean(String value){return value==null?"":value;}
    }
    private final MetadataStorage metadata; private final TokenStorage token;
    CredentialUpdateState(MetadataStorage metadata,TokenStorage token){this.metadata=metadata;this.token=token;}

    boolean update(String origin,String store,String device,String requestedToken){
        Metadata before=metadata.read();
        String value=requestedToken==null||requestedToken.isEmpty()?token.load():requestedToken;
        if(value.isEmpty())return false;
        TokenIdentity identity=TokenIdentity.update(before.fingerprint,before.generation,value);
        if(!metadata.markPending())return false;
        Metadata marked=metadata.read();
        if(!marked.pending||marked.onboarded)return false;
        if(!token.save(value)||!value.equals(token.load()))return false;
        Metadata wanted=before.published(origin,store,device,identity);
        if(!metadata.finish(wanted)){ensurePending();return false;}
        Metadata actual=metadata.read();
        if(actual.pending||!actual.onboarded||!same(wanted,actual)){ensurePending();return false;}
        return true;
    }
    boolean usable(){Metadata m=metadata.read();return !m.pending&&m.onboarded&&!m.origin.isEmpty()&&!m.store.isEmpty()&&!m.device.isEmpty()&&!m.fingerprint.isEmpty()&&m.generation>0&&!token.load().isEmpty();}
    String token(){return usable()?token.load():"";}
    private void ensurePending(){metadata.markPending();}
    private static boolean same(Metadata a,Metadata b){return a.origin.equals(b.origin)&&a.store.equals(b.store)&&a.device.equals(b.device)&&a.fingerprint.equals(b.fingerprint)&&a.generation==b.generation;}
}
