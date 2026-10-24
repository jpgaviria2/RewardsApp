package com.bitcoinrewards.nfcdisplay;

final class PathSegmentCodec {
    private static final char[] HEX="0123456789ABCDEF".toCharArray();
    private PathSegmentCodec() { }
    static String encode(String value) {
        if(value==null)throw new IllegalArgumentException("Invalid path segment");
        try {
            byte[] bytes=value.getBytes("UTF-8");
            StringBuilder out=new StringBuilder(bytes.length);
            for(byte item:bytes){int b=item&255;if(unreserved(b))out.append((char)b);else out.append('%').append(HEX[b>>>4]).append(HEX[b&15]);}
            return out.toString();
        } catch(Exception e) { throw new IllegalArgumentException("Invalid path segment"); }
    }
    private static boolean unreserved(int b){return(b>='a'&&b<='z')||(b>='A'&&b<='Z')||(b>='0'&&b<='9')||b=='-'||b=='.'||b=='_'||b=='~';}
}
