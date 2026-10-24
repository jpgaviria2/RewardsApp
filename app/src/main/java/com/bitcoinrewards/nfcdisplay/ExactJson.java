package com.bitcoinrewards.nfcdisplay;

import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

final class ExactJson {
    private final String text; private int at;
    private ExactJson(String text) { this.text = text; }
    static JSONObject object(String text) {
        if (text == null || text.length() > 65536) throw invalid();
        ExactJson scanner = new ExactJson(text); scanner.ws(); scanner.value(true); scanner.ws();
        if (scanner.at != text.length()) throw invalid();
        try { return new JSONObject(text); } catch (Exception e) { throw invalid(); }
    }
    private void value(boolean root) {
        ws(); if (at >= text.length()) throw invalid(); char c = text.charAt(at);
        if (c == '{') objectValue(); else if (c == '[' && !root) array(); else if (c == '"') string();
        else if (c == 't') literal("true"); else if (c == 'f') literal("false"); else if (c == 'n') literal("null");
        else if (c == '-' || digit(c)) number(); else throw invalid();
    }
    private void objectValue() {
        at++; ws(); Set<String> keys = new HashSet<>(); if (take('}')) return;
        while (true) { ws(); String key = string(); if (!keys.add(key)) throw invalid(); ws(); need(':'); value(false); ws();
            if (take('}')) return; need(','); }
    }
    private void array() { at++; ws(); if (take(']')) return; while (true) { value(false); ws(); if (take(']')) return; need(','); } }
    private String string() {
        need('"'); StringBuilder b = new StringBuilder();
        while (at < text.length()) { char c = text.charAt(at++); if (c == '"') return b.toString(); if (c < 0x20) throw invalid();
            if (c == '\\') { if (at >= text.length()) throw invalid(); char e=text.charAt(at++);
                if (e=='"'||e=='\\'||e=='/') b.append(e);
                else if(e=='b') b.append('\b'); else if(e=='f') b.append('\f'); else if(e=='n') b.append('\n'); else if(e=='r') b.append('\r'); else if(e=='t') b.append('\t');
                else if (e=='u') { if (at+4>text.length()) throw invalid(); int decoded=0; for(int i=0;i<4;i++){int digit=Character.digit(text.charAt(at+i),16);if(digit<0)throw invalid();decoded=(decoded<<4)|digit;}b.append((char)decoded);at+=4; } else throw invalid(); }
            else b.append(c); }
        throw invalid();
    }
    private void number() { if(take('-') && at>=text.length()) throw invalid(); if(take('0')) { if(at<text.length()&&digit(text.charAt(at))) throw invalid(); }
        else { if(at>=text.length()||!digit19(text.charAt(at))) throw invalid(); while(at<text.length()&&digit(text.charAt(at))) at++; }
        if(take('.')) { if(at>=text.length()||!digit(text.charAt(at))) throw invalid(); while(at<text.length()&&digit(text.charAt(at))) at++; }
        if(at<text.length()&&(text.charAt(at)=='e'||text.charAt(at)=='E')) { at++; if(at<text.length()&&(text.charAt(at)=='+'||text.charAt(at)=='-')) at++; if(at>=text.length()||!digit(text.charAt(at))) throw invalid(); while(at<text.length()&&digit(text.charAt(at))) at++; } }
    private void literal(String s) { if(!text.regionMatches(at,s,0,s.length())) throw invalid(); at+=s.length(); }
    private void ws(){ while(at<text.length() && " \t\r\n".indexOf(text.charAt(at))>=0) at++; }
    private boolean take(char c){ if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false; }
    private void need(char c){ if(!take(c))throw invalid(); }
    private static boolean digit(char c){return c>='0'&&c<='9';} private static boolean digit19(char c){return c>='1'&&c<='9';}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid JSON response");}
}
