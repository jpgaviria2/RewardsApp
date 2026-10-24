package com.bitcoinrewards.nfcdisplay;

import java.net.IDN;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

final class WalletQrPayloadParser {
    enum Kind { LIGHTNING_ADDRESS, LNURL_PAY }

    static final class ParsedPayload {
        final Kind kind;
        final String value;
        final byte[] scanBytes;
        final String scanSha256;
        final URI lnurlEndpoint;
        private final String localLabel;
        ParsedPayload(Kind kind,String value,byte[] scanBytes,URI endpoint,String label){this.kind=kind;this.value=value;this.scanBytes=scanBytes.clone();this.scanSha256=sha256(scanBytes);lnurlEndpoint=endpoint;localLabel=label;}
        String displayLabel(){return localLabel;}
    }

    private static final Pattern LIGHTNING_ADDRESS=Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9._-]{0,62}[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final String CHARSET="qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private WalletQrPayloadParser(){ }

    static ParsedPayload parse(String raw){
        byte[] exact=strictUtf8(raw);if(exact.length==0||exact.length>4096)throw invalid();
        int start=0,end=exact.length;while(start<end&&outer(exact[start]))start++;while(end>start&&outer(exact[end-1]))end--;
        if(start==end)throw invalid();byte[] framed=Arrays.copyOfRange(exact,start,end);rejectControls(framed);String value=decodeUtf8(framed);
        if(value.regionMatches(true,0,"lightning:",0,10)){value=value.substring(10);if(value.isEmpty()||value.regionMatches(true,0,"lightning:",0,10))throw invalid();}
        if(value.length()<=128&&LIGHTNING_ADDRESS.matcher(value).matches())return new ParsedPayload(Kind.LIGHTNING_ADDRESS,value.toLowerCase(Locale.ROOT),exact,null,addressLabel(value));
        URI endpoint=strictHttps(decodeUtf8(decodeLnurl(value)));
        return new ParsedPayload(Kind.LNURL_PAY,value,exact,endpoint,boundedLabel("LNURL-pay candidate • "+normalizedHost(endpoint.getHost())+safePath(endpoint)));
    }

    private static byte[] strictUtf8(String value){if(value==null)throw invalid();try{ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));byte[] out=new byte[encoded.remaining()];encoded.get(out);return out;}catch(CharacterCodingException e){throw invalid();}}
    private static String decodeUtf8(byte[] value){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();}catch(CharacterCodingException e){throw invalid();}}
    private static void rejectControls(byte[] value){for(byte item:value){int b=item&255;if(b<0x20||b==0x7f)throw invalid();}}
    private static byte[] decodeLnurl(String value){if(value.length()<13||value.length()>4096)throw invalid();boolean lower=false,upper=false;for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<33||c>126)throw invalid();lower|=c>='a'&&c<='z';upper|=c>='A'&&c<='Z';}if(lower&&upper)throw invalid();String normalized=value.toLowerCase(Locale.ROOT);int separator=normalized.lastIndexOf('1');if(separator!=5||!"lnurl".equals(normalized.substring(0,separator)))throw invalid();int count=normalized.length()-separator-1;if(count<7)throw invalid();int[] words=new int[count];for(int i=0;i<count;i++){int word=CHARSET.indexOf(normalized.charAt(separator+1+i));if(word<0)throw invalid();words[i]=word;}if(!checksum("lnurl",words))throw invalid();int payloadWords=words.length-6;byte[] out=new byte[payloadWords*5/8];int accumulator=0,bits=0,at=0;for(int i=0;i<payloadWords;i++){accumulator=(accumulator<<5)|words[i];bits+=5;if(bits>=8){bits-=8;out[at++]=(byte)((accumulator>>bits)&255);}}if(bits>=5||((accumulator<<(8-bits))&255)!=0)throw invalid();return out;}
    private static URI strictHttps(String text){try{URI uri=new URI(text);if(!uri.isAbsolute()||uri.isOpaque()||!"https".equalsIgnoreCase(uri.getScheme())||uri.getRawAuthority()==null||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw invalid();String host=normalizedHost(uri.getHost());if(isIp(host)||host.endsWith(".onion")||!dns(host))throw invalid();return uri;}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw invalid();}}
    private static String normalizedHost(String host){try{return IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);}catch(Exception e){throw invalid();}}
    private static boolean dns(String host){if(host.length()>253||host.endsWith(".")||host.indexOf('.')<1)return false;for(String label:host.split("\\.",-1)){if(label.isEmpty()||label.length()>63||label.charAt(0)=='-'||label.charAt(label.length()-1)=='-')return false;for(int i=0;i<label.length();i++){char c=label.charAt(i);if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'))return false;}}return true;}
    private static boolean isIp(String host){if(host.indexOf(':')>=0)return true;String[] labels=host.split("\\.",-1);if(labels.length!=4)return false;for(String label:labels){if(label.isEmpty()||label.length()>3)return false;for(int i=0;i<label.length();i++)if(!Character.isDigit(label.charAt(i)))return false;try{if(Integer.parseInt(label)>255)return false;}catch(Exception e){return false;}}return true;}
    private static String safePath(URI uri){String path=uri.getRawPath();if(path==null||"/".equals(path))return"";for(int offset=0;offset<path.length();){int cp=path.codePointAt(offset),type=Character.getType(cp);if(Character.isISOControl(cp)||type==Character.FORMAT||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR||type==Character.PRIVATE_USE||type==Character.UNASSIGNED||type==Character.SURROGATE)throw invalid();offset+=Character.charCount(cp);}return truncateCodePoints(path,40);}
    private static String boundedLabel(String value){return truncateCodePoints(value,256);}
    private static String truncateCodePoints(String value,int max){int count=value.codePointCount(0,value.length());if(count<=max)return value;int end=value.offsetByCodePoints(0,max-1);return value.substring(0,end)+"…";}
    private static String addressLabel(String value){int at=value.indexOf('@');if(at<=1)return"Lightning address";String local=value.substring(0,at),domain=value.substring(at+1);return local.substring(0,Math.min(3,local.length()))+"…@"+domain;}
    private static String sha256(byte[] value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder out=new StringBuilder(64);for(byte item:digest)out.append(String.format(Locale.ROOT,"%02x",item&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean checksum(String hrp,int[] data){int p=1;for(int i=0;i<hrp.length();i++)p=step(p)^(hrp.charAt(i)>>5);p=step(p);for(int i=0;i<hrp.length();i++)p=step(p)^(hrp.charAt(i)&31);for(int word:data)p=step(p)^word;return p==1;}
    private static int step(int pre){int bottom=pre>>>25,value=(pre&0x1ffffff)<<5;if((bottom&1)!=0)value^=0x3b6a57b2;if((bottom&2)!=0)value^=0x26508e6d;if((bottom&4)!=0)value^=0x1ea119fa;if((bottom&8)!=0)value^=0x3d4233dd;if((bottom&16)!=0)value^=0x2a1462b3;return value;}
    private static boolean outer(byte value){return value==' '||value=='\t'||value=='\r'||value=='\n';}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("QR must contain a Lightning address or strict HTTPS LNURL-pay code.");}
}
