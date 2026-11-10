#!/usr/bin/env python3
"""Offline Android getter/Canvas/animator doubles; executes real confetti drawing.
Not Android instrumentation, native layout acceptance, or a real check-in.
"""
from pathlib import Path
import os, subprocess, tempfile, html, xml.etree.ElementTree as ET, base64

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'evidence' / 'preview'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')) / 'bin'
stubs = {
'android/content/Context.java': 'package android.content; public class Context {}',
'android/util/AttributeSet.java': 'package android.util; public interface AttributeSet {}',
'android/util/DisplayMetrics.java': 'package android.util; public class DisplayMetrics {public float density=3f;}',
'android/content/res/Resources.java': 'package android.content.res; public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}',
'android/view/View.java': '''package android.view; public class View {
public static final int VISIBLE=0,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_NO=2; public int visibility=8;
public View(android.content.Context c,android.util.AttributeSet a){} public void setImportantForAccessibility(int n){}
public void setVisibility(int n){visibility=n;} public boolean isAttachedToWindow(){return true;}
public void invalidate(){} public int getWidth(){return 1080;} public int getHeight(){return 2400;}
public android.content.res.Resources getResources(){return new android.content.res.Resources();}
protected void onDetachedFromWindow(){} protected void onDraw(android.graphics.Canvas c){}
}''',
'android/view/animation/LinearInterpolator.java': 'package android.view.animation; public class LinearInterpolator {}',
'android/animation/ValueAnimator.java': '''package android.animation; import java.util.*; public class ValueAnimator {
public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator a);} public static ValueAnimator last;
public long duration; public boolean canceled; private float value; private List<AnimatorUpdateListener> listeners=new ArrayList<>();
public static ValueAnimator ofFloat(float a,float b){last=new ValueAnimator();return last;}
public void setDuration(long n){duration=n;} public void setInterpolator(Object i){} public Object getAnimatedValue(){return value;}
public void addUpdateListener(AnimatorUpdateListener l){listeners.add(l);} public void removeAllUpdateListeners(){listeners.clear();}
public void cancel(){canceled=true;} public void start(){dispatch(0f);} public void dispatch(float f){value=f;for(var l:new ArrayList<>(listeners))l.onAnimationUpdate(this);}
}''',
'android/graphics/Paint.java': '''package android.graphics; public class Paint {public static final int ANTI_ALIAS_FLAG=1;public int color,alpha=255;
public Paint(int f){}public void setColor(int n){color=n;alpha=(n>>>24);}public void setAlpha(int n){alpha=n;}}''',
'android/graphics/Canvas.java': '''package android.graphics; public class Canvas {public int count;public StringBuilder svg=new StringBuilder();private float r,x,y;
public void save(){}public void restore(){}public void rotate(float a,float b,float c){r=a;x=b;y=c;}
public void drawRect(float a,float b,float c,float d,Paint p){count++;svg.append(String.format(java.util.Locale.ROOT,
"<rect x=\\\"%.2f\\\" y=\\\"%.2f\\\" width=\\\"%.2f\\\" height=\\\"%.2f\\\" fill=\\\"#%06x\\\" opacity=\\\"%.3f\\\" transform=\\\"rotate(%.2f %.2f %.2f)\\\"/>",a,b,c-a,d-b,p.color&0xffffff,p.alpha/255f,r,x,y));}}''',
'com/bitcoinrewards/nfcdisplay/RenderConfetti.java': '''package com.bitcoinrewards.nfcdisplay;
import android.animation.ValueAnimator; import android.graphics.Canvas; import java.nio.file.*;
public class RenderConfetti extends CheckInConfettiView {
public RenderConfetti(){super(new android.content.Context(),null);} static void require(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args)throws Exception {
RenderConfetti v=new RenderConfetti(); Canvas idle=new Canvas();v.onDraw(idle);require(idle.count==0);
v.start();ValueAnimator first=ValueAnimator.last;require(v.visibility==0&&first.duration==1400);first.dispatch(.4f);
Canvas frame=new Canvas();v.onDraw(frame);require(frame.count==64);
Files.writeString(Path.of(args[0]),"<svg xmlns=\\\"http://www.w3.org/2000/svg\\\" viewBox=\\\"0 0 1080 2400\\\">"+frame.svg+"</svg>");
v.stop();require(v.visibility==8&&first.canceled);first.dispatch(.7f);Canvas stopped=new Canvas();v.onDraw(stopped);require(stopped.count==0);
v.start();ValueAnimator.last.dispatch(1f);require(v.visibility==8);Canvas ended=new Canvas();v.onDraw(ended);require(ended.count==0);
v.start();v.onDetachedFromWindow();require(v.visibility==8&&ValueAnimator.last.canceled);
System.out.println("PASS: idle 0 particles; active 64; stop/end/detach 0; stale callback removed; duration 1400ms");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='confetti-render-', dir=OUT) as tmp:
    tmp = Path(tmp)
    for name, content in stubs.items():
        path = tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    production = ROOT / 'app/src/main/java/com/bitcoinrewards/nfcdisplay/CheckInConfettiView.java'
    # The real production View is final: harness calls its protected methods from the same package.
    runner = tmp / 'com/bitcoinrewards/nfcdisplay/RenderConfetti.java'
    text = runner.read_text().replace('public class RenderConfetti extends CheckInConfettiView {', 'public class RenderConfetti {')
    text = text.replace('public RenderConfetti(){super(new android.content.Context(),null);}', '')
    text = text.replace('RenderConfetti v=new RenderConfetti();', 'CheckInConfettiView v=new CheckInConfettiView(new android.content.Context(),null);')
    runner.write_text(text)
    files = [str(p) for p in tmp.rglob('*.java')] + [str(production)]
    subprocess.run([str(JAVA / 'javac'), '-d', str(tmp), *files], check=True, timeout=30)
    subprocess.run([str(JAVA / 'java'), '-cp', str(tmp), 'com.bitcoinrewards.nfcdisplay.RenderConfetti', str(OUT / 'confetti.svg')], check=True, timeout=15)

strings = {e.attrib['name']: e.text or '' for e in ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()}
colors = {e.attrib['name']: e.text or '' for e in ET.parse(ROOT / 'app/src/main/res/values/colors.xml').getroot()}
poster = base64.b64encode((ROOT / 'app/src/main/res/drawable-nodpi/wallet_rewards_tutorial_poster.jpg').read_bytes()).decode()
confetti = (OUT / 'confetti.svg').read_text()
def s(key): return html.escape(strings[key])
preview = f'''<!doctype html><meta charset="utf-8"><title>Offline scanner UI preview</title>
<style>body{{background:#18221e;color:#f4eddf;font-family:Arial,sans-serif;margin:24px}}main{{display:flex;gap:30px}}section{{width:360px}}.screen{{height:800px;position:relative;overflow:hidden;background:{colors['rewards_green']};text-align:center;border:1px solid #64786c}}.nav{{background:#f4eddf;color:{colors['rewards_green']};font-weight:bold;font-size:21px;height:48px;line-height:48px}}h2{{font-size:27px;margin:14px 0 3px}}p{{font-size:15px;margin:5px 0}}.prompt{{margin:10px 14px;height:180px;display:grid;place-items:center;border-radius:12px;background:{colors['rewards_cream']};color:{colors['rewards_green']};font-size:48px;font-weight:bold}}.actions{{display:flex;gap:8px;margin:9px 14px}}.actions div{{flex:1;border-radius:8px;padding:10px 2px;background:#f4eddf;color:#173f2f;font-size:16px;font-weight:bold}}small{{font-size:12px;font-weight:normal}}.status{{margin:8px 14px;font-size:14px}}.poster{{height:310px;border:5px solid #f4eddf;border-radius:10px;margin-top:12px}}.success{{height:100%;display:grid;place-items:center;color:{colors['rewards_success']};font-size:32px;line-height:1.4;font-weight:bold;padding:24px;box-sizing:border-box}}svg{{position:absolute;inset:0;width:100%;height:100%;pointer-events:none}}</style>
<h1>Offline XML-derived preview</h1><p>Layout approximation, not Android/physical acceptance. Confetti SVG executes the actual View drawing via Canvas/animator doubles.</p>
<main><section><h3>Idle</h3><div class="screen"><div class="nav">Customer rewards</div><h2>{s('rewards_headline')}</h2><p>{s('rewards_subtitle')}</p><div class="prompt">{s('scan_here')}</div><div class="actions"><div>NFC • TAP CARD<br><small>{s('tap_instruction')}</small></div><div>▦ • SHOW QR<br><small>{s('qr_instruction')}</small></div></div><div class="status">{s('scanner_ready')}</div><p>{s('tutorial_title')}</p><img class="poster" src="data:image/jpeg;base64,{poster}"></div></section>
<section><h3>Confirmed fixture (generic: name contract blocked)</h3><div class="screen"><div class="success">Checked in<br>You’re ready to earn sats</div>{confetti}</div></section></main>'''
(OUT / 'index.html').write_text(preview)
print('Preview:', OUT / 'index.html')
