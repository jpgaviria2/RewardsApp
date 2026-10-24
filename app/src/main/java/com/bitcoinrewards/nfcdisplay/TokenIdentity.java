package com.bitcoinrewards.nfcdisplay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class TokenIdentity {
    final String fingerprint; final long generation;
    TokenIdentity(String fingerprint,long generation){this.fingerprint=fingerprint;this.generation=generation;}
    static TokenIdentity update(String currentFingerprint,long currentGeneration,String token){
        if(token==null||token.trim().isEmpty()||token.length()>4096)throw new IllegalArgumentException("Invalid scanner token");
        String next=fingerprint(token); long generation=next.equals(currentFingerprint)?currentGeneration:currentGeneration+1;
        if(generation<1)generation=1; return new TokenIdentity(next,generation);
    }
    static String fingerprint(String token){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:digest)b.append(String.format("%02x",v&255));return b.toString();}catch(Exception e){throw new IllegalStateException();}}
}
