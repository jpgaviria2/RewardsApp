package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import static org.junit.Assert.*;

public class WalletQrPayloadParserTest {
    @Test public void lightningAddressIsCanonicalizedWithoutChangingScanEvidenceOrLabel(){
        String raw=" \tLiGhTnInG:Alice@Example.COM\r\n";
        WalletQrPayloadParser.ParsedPayload parsed=WalletQrPayloadParser.parse(raw);
        assertEquals(WalletQrPayloadParser.Kind.LIGHTNING_ADDRESS,parsed.kind);assertEquals("alice@example.com",parsed.value);
        assertEquals("Alice",parsed.displayLabel());assertNull(parsed.lnurlEndpoint);
        assertArrayEquals(raw.getBytes(StandardCharsets.UTF_8),parsed.scanBytes);assertEquals(sha256(raw),parsed.scanSha256);
    }

    @Test public void lnurlPayDecodesToStrictHttpsAndKeepsFramedValue(){
        String lnurl=encode("https://pay.example/lnurlp/alice");String raw=" \tLiGhTnInG:"+lnurl+"\r\n";
        WalletQrPayloadParser.ParsedPayload parsed=WalletQrPayloadParser.parse(raw);
        assertEquals(WalletQrPayloadParser.Kind.LNURL_PAY,parsed.kind);assertEquals(lnurl,parsed.value);
        assertEquals("https://pay.example/lnurlp/alice",parsed.lnurlEndpoint.toString());
        assertEquals("Wallet",parsed.displayLabel());
        assertArrayEquals(raw.getBytes(StandardCharsets.UTF_8),parsed.scanBytes);assertEquals(sha256(raw),parsed.scanSha256);
    }

    @Test public void uppercaseBech32IsAcceptedWithoutChangingSubmittedDestination(){
        String upper=encode("https://pay.example/lnurlp/alice").toUpperCase(Locale.ROOT);
        WalletQrPayloadParser.ParsedPayload parsed=WalletQrPayloadParser.parse(upper);
        assertEquals(upper,parsed.value);assertEquals(WalletQrPayloadParser.Kind.LNURL_PAY,parsed.kind);
    }

    @Test public void decodedUppercaseHttpsSchemeIsAcceptedAndDestinationIsPreserved(){
        String decoded="HTTPS://pay.example/lnurlp/alice";String lnurl=encode(decoded);
        WalletQrPayloadParser.ParsedPayload parsed=WalletQrPayloadParser.parse(lnurl);
        assertEquals(lnurl,parsed.value);assertEquals(decoded,parsed.lnurlEndpoint.toString());
        assertEquals("Wallet",parsed.displayLabel());
    }

    @Test public void decodedMixedCaseHostIsValidatedInCanonicalForm(){
        String decoded="https://Pay.Example/lnurlp/alice";String lnurl=encode(decoded);
        WalletQrPayloadParser.ParsedPayload parsed=WalletQrPayloadParser.parse(lnurl);
        assertEquals(lnurl,parsed.value);assertEquals(decoded,parsed.lnurlEndpoint.toString());
        assertEquals("Wallet",parsed.displayLabel());
    }

    @Test public void displayLabelNeverLeaksLnurlEndpointOrCreatesControls(){
        WalletQrPayloadParser.ParsedPayload encodedControl=WalletQrPayloadParser.parse(encode("https://pay.example/lnurlp/alice%0Aadmin"));
        assertEquals("Wallet",encodedControl.displayLabel());
        assertFalse(encodedControl.displayLabel().contains("\n"));

        StringBuilder endpoint=new StringBuilder("https://pay.example/");
        for(int i=0;i<50;i++)endpoint.append("😀");
        String label=WalletQrPayloadParser.parse(encode(endpoint.toString())).displayLabel();
        assertEquals("Wallet",label);
        assertNoUnpairedSurrogates(label);
    }

    @Test public void lightningAddressLabelUsesLocalPartOnlyAndIsBounded(){
        assertEquals("coffee.rewards",WalletQrPayloadParser.parse("coffee.rewards@example.com").displayLabel());
        assertEquals("abcdefghijklmnopqrstuvw…",WalletQrPayloadParser.parse("abcdefghijklmnopqrstuvwxyz@example.com").displayLabel());
    }

    @Test public void onlyAsciiOuterWhitespaceAndOneWrapperAreAllowed(){
        String lnurl=encode("https://pay.example/lnurlp/alice");
        assertEquals(lnurl,WalletQrPayloadParser.parse(" \t\r\n"+lnurl+" \t\r\n").value);
        rejected("lightning:lightning:"+lnurl);rejected("lightning: "+lnurl);rejected("\u000b"+lnurl);rejected(lnurl+"\u000c");rejected("\u00a0"+lnurl);
    }

    @Test public void rejectsMalformedBech32AndUnsupportedQrFamilies(){
        String lnurl=encode("https://pay.example/lnurlp/alice");
        rejected(lnurl.substring(0,lnurl.length()-1)+"q");rejected("lnURL"+lnurl.substring(5));
        rejected("lnbc1invoice");rejected("lno1offer");rejected("https://pay.example/lnurlp/alice");
        rejected("lnurlp://pay.example/alice");rejected("lnurlw://pay.example/withdraw");rejected("nostr:npub1x");rejected("bitcoin:bc1qx");
    }

    @Test public void bolt11InvoicesRemainRejectedWithNarrowUserGuidance(){
        String invoice="lightning:LNBC1P5FIXEDINVOICE";
        rejected(invoice);
        assertTrue(WalletQrPayloadParser.isBolt11Invoice(invoice));
        assertEquals("Fixed Lightning invoices cannot be used for rewards. Show a Lightning address or LNURL-pay QR.",WalletQrPayloadParser.userFacingRejection(invoice));
        assertFalse(WalletQrPayloadParser.isBolt11Invoice("alice@example.com"));
        assertFalse(WalletQrPayloadParser.isBolt11Invoice(encode("https://pay.example/lnurlp/alice")));
    }

    @Test public void rejectsDecodedUnsafeUris(){
        rejected(encode("http://pay.example/lnurlp/alice"));rejected(encode("https://user@pay.example/lnurlp/alice"));
        rejected(encode("https://pay.example:8443/lnurlp/alice"));rejected(encode("https://pay.example/lnurlp/alice#x"));
        rejected(encode("https://127.0.0.1/lnurlp/alice"));rejected(encode("https://[::1]/lnurlp/alice"));
        rejected(encode("https://hidden.onion/lnurlp/alice"));rejected(encode("https://-bad.example/lnurlp/alice"));
        rejected(encode("https://localhost/lnurlp/alice"));
        assertEquals("https://pay.example",WalletQrPayloadParser.parse(encode("https://pay.example")).lnurlEndpoint.toString());
    }

    @Test public void enforcesExactUtf8ByteLimitAndMalformedUtf16(){
        String lnurl=encode("https://pay.example/lnurlp/alice");String exact=lnurl+spaces(4096-lnurl.length());
        assertEquals(4096,WalletQrPayloadParser.parse(exact).scanBytes.length);rejected(exact+" ");rejected("\ud800"+lnurl);rejected(null);rejected(" \t\r\n");
    }

    private static String encode(String value){return encodeBytes(value.getBytes(StandardCharsets.UTF_8));}
    private static String encodeBytes(byte[] bytes){ArrayList<Integer> data=new ArrayList<>();int accumulator=0,bits=0;for(byte item:bytes){accumulator=(accumulator<<8)|(item&255);bits+=8;while(bits>=5){bits-=5;data.add((accumulator>>bits)&31);}}if(bits>0)data.add((accumulator<<(5-bits))&31);int[] values=new int[data.size()+6];for(int i=0;i<data.size();i++)values[i]=data.get(i);int polymod=1;for(char c:"lnurl".toCharArray())polymod=step(polymod)^(c>>5);polymod=step(polymod);for(char c:"lnurl".toCharArray())polymod=step(polymod)^(c&31);for(int value:values)polymod=step(polymod)^value;polymod^=1;for(int i=0;i<6;i++)values[data.size()+i]=(polymod>>(5*(5-i)))&31;String charset="qpzry9x8gf2tvdw0s3jn54khce6mua7l";StringBuilder result=new StringBuilder("lnurl1");for(int value:values)result.append(charset.charAt(value));return result.toString();}
    private static int step(int pre){int bottom=pre>>>25,value=(pre&0x1ffffff)<<5;if((bottom&1)!=0)value^=0x3b6a57b2;if((bottom&2)!=0)value^=0x26508e6d;if((bottom&4)!=0)value^=0x1ea119fa;if((bottom&8)!=0)value^=0x3d4233dd;if((bottom&16)!=0)value^=0x2a1462b3;return value;}
    private static String sha256(String value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte item:digest)out.append(String.format(Locale.ROOT,"%02x",item&255));return out.toString();}catch(Exception e){throw new AssertionError(e);}}
    private static String spaces(int count){StringBuilder out=new StringBuilder();while(count-->0)out.append(' ');return out.toString();}
    private static void assertNoUnpairedSurrogates(String value){for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){assertTrue(i+1<value.length()&&Character.isLowSurrogate(value.charAt(i+1)));i++;}else assertFalse(Character.isLowSurrogate(c));}}
    private static void rejected(String value){assertThrows(IllegalArgumentException.class,()->WalletQrPayloadParser.parse(value));}
}
