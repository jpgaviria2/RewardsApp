package com.bitcoinrewards.nfcdisplay;

import org.json.JSONObject;
import java.util.UUID;

final class PendingOperation {
    enum Phase { SUBMITTING, POLLING, AWAITING_APPROVAL, CONFIRMING, CANCELLING }
    final String storeId, deviceId, credentialFingerprint, destination;
    final long tokenGeneration;
    final UUID checkInId, confirmationId;
    final int version, confirmationVersion;
    final Phase phase;

    private PendingOperation(String s,String d,String f,long g,String destination,UUID checkIn,UUID confirmation,
                             int protocolVersion,int confirmationVersion,Phase phase){
        this.storeId=s;this.deviceId=d;this.credentialFingerprint=f;this.tokenGeneration=g;this.destination=destination;
        this.checkInId=checkIn;this.confirmationId=confirmation;this.version=protocolVersion;
        this.confirmationVersion=confirmationVersion;this.phase=phase;
    }
    static PendingOperation prepared(String s,String d,String f,long g,String destination,UUID checkIn,int version){
        validate(s,d,f,g,destination,checkIn,version);return new PendingOperation(s,d,f,g,destination,checkIn,null,version,0,Phase.SUBMITTING);
    }
    static PendingOperation started(String s,String d,String f,long g,String destination,UUID checkIn,int version){
        validate(s,d,f,g,destination,checkIn,version);return new PendingOperation(s,d,f,g,destination,checkIn,null,version,0,Phase.POLLING);
    }
    PendingOperation submitted(){if(phase!=Phase.SUBMITTING)throw bad();return copy(null,0,Phase.POLLING);}
    PendingOperation awaiting(UUID confirmation){return awaiting(confirmation,1);}
    PendingOperation awaiting(UUID confirmation,int serverVersion){if(confirmation==null||serverVersion<1)throw bad();
        if(phase==Phase.CONFIRMING||phase==Phase.CANCELLING)return this;
        return copy(confirmation,serverVersion,Phase.AWAITING_APPROVAL);}
    PendingOperation confirming(){if(confirmationId==null||confirmationVersion<1)throw bad();return copy(confirmationId,confirmationVersion,Phase.CONFIRMING);}
    PendingOperation cancelling(){if(confirmationId==null||confirmationVersion<1)throw bad();return copy(confirmationId,confirmationVersion,Phase.CANCELLING);}
    PendingOperation reconciled(UUID confirmation,int serverVersion,Phase requested){
        if(confirmation==null||serverVersion<1)throw bad();
        Phase next=(phase==Phase.CONFIRMING||phase==Phase.CANCELLING)?phase:requested;
        return copy(confirmation,serverVersion,next);
    }
    private PendingOperation copy(UUID confirmation,int serverVersion,Phase next){return new PendingOperation(storeId,deviceId,credentialFingerprint,
        tokenGeneration,destination,checkInId,confirmation,version,serverVersion,next);}
    boolean matchesIdentity(String store,String device,String fingerprint,long generation){return storeId.equals(store)&&deviceId.equals(device)&&credentialFingerprint.equals(fingerprint)&&tokenGeneration==generation;}
    JSONObject toJson(){try{return new JSONObject().put("storeId",storeId).put("deviceId",deviceId).put("credentialFingerprint",credentialFingerprint)
        .put("tokenGeneration",tokenGeneration).put("destination",destination).put("checkInId",checkInId.toString())
        .put("confirmationId",confirmationId==null?JSONObject.NULL:confirmationId.toString()).put("protocolVersion",version)
        .put("confirmationVersion",confirmationVersion).put("phase",phase.name());}catch(Exception e){throw bad();}}
    static PendingOperation fromJson(String raw){JSONObject j=ExactJson.object(raw);requireKeys(j,"storeId","deviceId","credentialFingerprint","tokenGeneration","destination","checkInId","confirmationId","protocolVersion","confirmationVersion","phase");
        try{UUID c=j.isNull("confirmationId")?null:exactUuid(j.getString("confirmationId"));PendingOperation p=new PendingOperation(j.getString("storeId"),j.getString("deviceId"),j.getString("credentialFingerprint"),exactLong(j,"tokenGeneration"),j.getString("destination"),exactUuid(j.getString("checkInId")),c,exactInt(j,"protocolVersion"),(int)exactLong(j,"confirmationVersion"),Phase.valueOf(j.getString("phase")));validateText(p.storeId,256);validateText(p.deviceId,256);validateText(p.credentialFingerprint,128);validateText(p.destination,4096);if(p.tokenGeneration<1||p.version!=CheckInApiContract.VERSION||p.confirmationVersion<0||((p.phase==Phase.AWAITING_APPROVAL||p.phase==Phase.CONFIRMING||p.phase==Phase.CANCELLING)&&(p.confirmationId==null||p.confirmationVersion<1))||((p.phase==Phase.SUBMITTING||p.phase==Phase.POLLING)&&(p.confirmationId!=null||p.confirmationVersion!=0)))throw bad();return p;}catch(Exception e){throw bad();}}
    static void requireKeys(JSONObject j,String... keys){if(j.length()!=keys.length)throw bad();for(String k:keys)if(!j.has(k))throw bad();}
    static long exactLong(JSONObject j,String k){Object v=j.opt(k);if(!(v instanceof Long||v instanceof Integer))throw bad();return((Number)v).longValue();}
    static int exactInt(JSONObject j,String k){long v=exactLong(j,k);if(v<1||v>Integer.MAX_VALUE)throw bad();return(int)v;}
    static UUID exactUuid(String value){if(value==null||!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw bad();UUID parsed=UUID.fromString(value);if(!parsed.toString().equals(value)||parsed.variant()!=2||parsed.version()<1||parsed.version()>5||(parsed.getMostSignificantBits()==0&&parsed.getLeastSignificantBits()==0))throw bad();return parsed;}
    static void validateText(String s,int max){if(s==null||s.isEmpty()||s.length()>max)throw bad();boolean meaningful=false;for(int offset=0;offset<s.length();){int cp=s.codePointAt(offset);if(Character.isSurrogate(s.charAt(offset))&&Character.charCount(cp)==1)throw bad();int type=Character.getType(cp);if(Character.isISOControl(cp)||type==Character.FORMAT||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR||type==Character.PRIVATE_USE||type==Character.UNASSIGNED||type==Character.SURROGATE)throw bad();if(type!=Character.NON_SPACING_MARK&&type!=Character.COMBINING_SPACING_MARK&&type!=Character.ENCLOSING_MARK&&!Character.isWhitespace(cp)&&type!=Character.SPACE_SEPARATOR)meaningful=true;offset+=Character.charCount(cp);}if(!meaningful)throw bad();}
    private static void validate(String s,String d,String f,long g,String destination,UUID checkIn,int version){validateText(s,256);validateText(d,256);validateText(f,128);validateText(destination,4096);if(g<1||version!=CheckInApiContract.VERSION||checkIn==null)throw bad();}
    static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid operation data");}
}
