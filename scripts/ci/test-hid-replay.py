#!/usr/bin/env python3
"""Offline adapter replay. Test doubles cover platform getters, not Android's key map.
Compiles the actual production adapter/framer/parser; never launches an Activity,
opens a socket, or accesses a device. No new Gradle dependencies are required.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')) / 'bin'
SOURCES = {
    'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION { public static int SDK_INT=36; } }''',
    'android/os/SystemClock.java': '''package android.os;
public class SystemClock { public static long now=1000; public static long uptimeMillis(){return now;} }''',
    'android/view/InputDevice.java': '''package android.view;
public class InputDevice {
 public static final int SOURCE_KEYBOARD=257;
 public int id=7, vendor=0x045e, product=0x0040; public boolean external=true, virtual=false;
 public String descriptor="RD-scanner";
 public int getId(){return id;} public int getVendorId(){return vendor;} public int getProductId(){return product;}
 public boolean isExternal(){if(android.os.Build.VERSION.SDK_INT<29)throw new AssertionError("API29 getter called on legacy system");return external;} public boolean isVirtual(){return virtual;}
 public String getDescriptor(){return descriptor;}
}''',
    'android/view/KeyEvent.java': '''package android.view;
public class KeyEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MULTIPLE=2,KEYCODE_ENTER=66,KEYCODE_NUMPAD_ENTER=160,KEYCODE_SHIFT_LEFT=59,KEYCODE_SHIFT_RIGHT=60;
 public InputDevice device; public int action=0,key=29,unicode='a',meta=0,repeat=0; public long time;
 public boolean keyboard=true,ctrl=false,alt=false,command=false,canceled=false;
 public KeyEvent(InputDevice d,int c){device=d;unicode=c;time=++android.os.SystemClock.now;}
 public InputDevice getDevice(){return device;} public int getDeviceId(){return device==null?-1:device.id;}
 public boolean isFromSource(int source){return keyboard;} public int getAction(){return action;}
 public int getKeyCode(){return key;} public boolean isCtrlPressed(){return ctrl;} public boolean isAltPressed(){return alt;}
 public boolean isMetaPressed(){return command;} public boolean isCanceled(){return canceled;}
 public int getMetaState(){return meta;} public long getEventTime(){return time;} public int getRepeatCount(){return repeat;}
 public static int unicodeReads; public int getUnicodeChar(int state){unicodeReads++;return state==1 && unicode>='a' && unicode<='z'?Character.toUpperCase(unicode):unicode;}
}''',
    'com/bitcoinrewards/nfcdisplay/HidReplay.java': '''package com.bitcoinrewards.nfcdisplay;
import android.view.*;
import android.os.*;
import java.util.*;
public class HidReplay {
 static InputDevice scanner; static HidScannerInput input; static List<String> raw; static int cases;
 static void reset(){scanner=new InputDevice();input=new HidScannerInput();raw=new ArrayList<>();Build.VERSION.SDK_INT=36;}
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static boolean send(KeyEvent e,boolean enabled){return input.handle(e,enabled,raw::add);}
 static KeyEvent key(int c){return new KeyEvent(scanner,c);}
 static void text(String s,boolean enabled){for(char c:s.toCharArray()){KeyEvent e=key(c);check(send(e,enabled),"scanner down consumed");e.action=KeyEvent.ACTION_UP;check(send(e,enabled),"scanner up consumed");}}
 static void enter(boolean enabled){KeyEvent e=key(0);e.key=KeyEvent.KEYCODE_ENTER;check(send(e,enabled),"Enter down consumed");e.action=KeyEvent.ACTION_UP;check(send(e,enabled),"Enter up consumed");}
 static void passed(String name){cases++;System.out.println("PASS "+name);}
 public static void main(String[] args){
  reset();text("lightning:",true);KeyEvent a=key('a');a.meta=1;send(a,true);text("lice.Test-_Z@Example.COM",true);enter(true);
  check(raw.equals(Arrays.asList("lightning:Alice.Test-_Z@Example.COM")),"exact Unicode output");
  check(WalletQrPayloadParser.parse(raw.get(0)).value.equals("alice.test-_z@example.com"),"existing strict parser");passed("exact Unicode and shared parser");
  reset();text("alice@example.com",true);check(raw.isEmpty(),"no delimiter must not submit");enter(true);check(raw.size()==1,"complete only");passed("delimiter only");
  reset();for(int bad=0;bad<7;bad++){InputDevice d=new InputDevice();if(bad==0)d.virtual=true;if(bad==1)d.external=false;if(bad==2)d.id=-1;if(bad==3)d.vendor=1;if(bad==4)d.product=1;KeyEvent e=new KeyEvent(d,'a');if(bad==5)e.keyboard=false;if(bad==6)e.device=null;check(send(e,true)==(bad==1),"only identified scanner consumed, even without external attestation "+bad);}check(raw.isEmpty(),"no non scanner callbacks");passed("virtual ADB soft keyboard and unrelated devices");
  reset();text("alice@",true);text("blocked",false);text("example.com",true);enter(true);check(raw.isEmpty(),"busy frame rejected");text("bob@example.com",true);enter(true);check(raw.size()==1,"busy recovery");passed("busy and recovery");
  reset();text("alice.",false);text("bob@example.com",true);enter(true);
  check(raw.isEmpty(),"disabled prefix must never submit valid suffix bob@example.com");
  text("alice.bob@example.com",true);enter(true);
  check(raw.equals(Arrays.asList("alice.bob@example.com")),"next complete frame recovers");
  check(WalletQrPayloadParser.parse(raw.get(0)).value.equals("alice.bob@example.com"),"recovered destination uses real parser");passed("disabled frame start rejects suffix and recovers");
  reset();text("alice@example.com",true);enter(false);check(raw.isEmpty(),"dialog Enter cannot pay");passed("locked dialog consumes Enter without callback");
  reset();text("alice@",true);input.reset();text("example.com",true);enter(true);check(raw.isEmpty(),"pause invalidates partial");passed("pause reset");
  reset();text("alice@",true);SystemClock.now+=501;text("example.com",true);enter(true);check(raw.isEmpty(),"stale suffix");passed("timeout");
  reset();text("alice@",true);KeyEvent repeat=key('a');repeat.repeat=1;send(repeat,true);text("example.com",true);enter(true);check(raw.isEmpty(),"repeat poisons");passed("repeat");
  reset();for(int i=0;i<4097;i++)send(key('a'),true);enter(true);check(raw.isEmpty(),"overflow rejected");passed("overflow");
  reset();text("alice@",true);scanner.id++;text("example.com",true);enter(true);check(raw.isEmpty(),"device switch rejected");passed("source change");
  reset();text("alice@",true);KeyEvent ctrl=key('x');ctrl.ctrl=true;send(ctrl,true);text("example.com",true);enter(true);check(raw.isEmpty(),"command rejected");passed("control command");
  reset();text("alice@",true);KeyEvent up=key('x');up.action=KeyEvent.ACTION_UP;up.canceled=true;send(up,true);text("example.com",true);enter(true);check(raw.isEmpty(),"cancelled key-up must invalidate frame");passed("cancelled key-up");
  reset();text("alice@",true);KeyEvent busyUp=key('x');busyUp.action=KeyEvent.ACTION_UP;send(busyUp,false);text("example.com",true);enter(true);check(raw.isEmpty(),"busy key-up invalidates frame");passed("busy key-up");
  reset();text("alice@",true);scanner.id++;KeyEvent changedUp=key('x');changedUp.action=KeyEvent.ACTION_UP;send(changedUp,true);scanner.id--;text("example.com",true);enter(true);check(raw.isEmpty(),"key-up source transition invalidates frame");passed("key-up source change");
  reset();text("alice@",true);KeyEvent shift=key(0);shift.key=KeyEvent.KEYCODE_SHIFT_LEFT;send(shift,false);text("example.com",true);enter(true);check(raw.isEmpty(),"busy modifier invalidates frame");passed("busy shift modifier");
  reset();text("alice@example.com",true);KeyEvent numpad=key(0);numpad.key=KeyEvent.KEYCODE_NUMPAD_ENTER;send(numpad,true);check(raw.size()==1,"numpad Enter submits frame");passed("numpad delimiter");
  reset();text("alice@",true);KeyEvent multiple=key('x');multiple.action=KeyEvent.ACTION_MULTIPLE;send(multiple,true);text("example.com",true);enter(true);check(raw.isEmpty(),"ACTION_MULTIPLE cannot inject text");passed("multiple action rejected");
  reset();text("alice@",true);KeyEvent stale=key('x');stale.time-=501;send(stale,true);text("example.com",true);enter(true);check(raw.isEmpty(),"queued stale key cannot complete frame");passed("stale queued event");
  for(int first:new int[]{0,1,2,3,4}){
   reset();KeyEvent blocked=key('a');
   if(first==1)blocked.action=KeyEvent.ACTION_UP;
   if(first==2 || first==3)blocked.key=KeyEvent.KEYCODE_SHIFT_LEFT;
   if(first==3)blocked.action=KeyEvent.ACTION_UP;
   if(first==4)blocked.action=KeyEvent.ACTION_MULTIPLE;
   KeyEvent.unicodeReads=0;send(blocked,false);check(KeyEvent.unicodeReads==0,"blocked event decoded "+first);
   input.reset();text("bob@example.com",true);enter(true);check(raw.isEmpty(),"blocked first event/lifecycle suffix "+first);
   text("alice.bob@example.com",true);enter(true);check(raw.equals(Arrays.asList("alice.bob@example.com")),"first event recovery "+first);
   passed("blocked first event and lifecycle recovery "+first);
  }
  reset();text("alice.",false);KeyEvent.unicodeReads=0;enter(false);enter(false);enter(false);
  check(KeyEvent.unicodeReads==0 && raw.isEmpty(),"consume-only delimiters never decode/submit");
  text("alice.bob@example.com",true);enter(true);check(raw.equals(Arrays.asList("alice.bob@example.com")),"consume-only Enter clears discard including UP");passed("multiple blocked delimiters recover immediately");
  reset();text("alice.",false);scanner.id++;KeyEvent sourceUp=key('x');sourceUp.action=KeyEvent.ACTION_UP;send(sourceUp,true);
  text("bob@example.com",true);enter(true);check(raw.isEmpty(),"blocked prefix source transition suffix");
  text("alice.bob@example.com",true);enter(true);check(raw.equals(Arrays.asList("alice.bob@example.com")),"source transition recovery");passed("blocked prefix source-UP transition");
  reset();text("alice.",false);scanner.external=false;text("blocked",true);scanner.external=true;text("bob@example.com",true);enter(true);
  check(raw.isEmpty(),"external attestation transition cannot admit suffix");text("alice.bob@example.com",true);enter(true);check(raw.size()==1,"attestation recovery");passed("blocked attestation transition");
  for(int api=19;api<29;api++){reset();Build.VERSION.SDK_INT=api;KeyEvent.unicodeReads=0;text("alice@example.com",true);enter(true);check(raw.isEmpty() && KeyEvent.unicodeReads==0,"legacy API must never decode or submit "+api);}passed("API19-28 scanner text and Enter consumed without submission");
  reset();scanner.external=false;KeyEvent.unicodeReads=0;text("alice@example.com",true);enter(true);check(raw.isEmpty() && KeyEvent.unicodeReads==0,"missing external attestation must consume without decode/submit");passed("unattested identified scanner is consume-only");
  System.out.println(cases+" replay cases passed; no network or device operations");
 }
}'''
}

production = ROOT / 'app/src/main/java/com/bitcoinrewards/nfcdisplay'
# Compile exact production dispatch bodies/listener expressions with getter doubles.
# The focused-control sink models delivery only; this is NOT Android UI testing.
import re
import xml.etree.ElementTree as ET
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
names = {e.attrib['{http://schemas.android.com/apk/res/android}name'].lstrip('.')
         for e in manifest.findall('.//activity')}
assert names == {'MainActivity', 'RewardsProfileScanActivity', 'PaymentActivity',
                 'NativePosActivity', 'SettingsActivity', 'LoginCodeScanActivity'}, names
SOURCES['android/app/Activity.java'] = '''package android.app;
import android.view.KeyEvent;
public class Activity { public int focusedEvents; public boolean dispatchKeyEvent(KeyEvent e){focusedEvents++;return false;} }'''
assembly = []
for name in sorted(names):
    source = (production / (name + '.java')).read_text()
    match = re.search(r'public boolean dispatchKeyEvent\(KeyEvent event\)\s*\{', source)
    assert match, name + ' missing scanner dispatch'
    start = match.start()
    end = match.end()
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    dispatch = source[start:end]
    listeners = re.findall(r'\.setOnKeyListener\(\(dialog, keyCode, event\) -> (hidScanner\.handle\(event, false, raw -> \{ \}\))\)', source)
    assert source.count('new AlertDialog.Builder') == len(listeners), name + ' unguarded dialog'
    dialogs = ''.join('boolean dialog%d(KeyEvent event){return %s;}\n' % (i, expr) for i, expr in enumerate(listeners))
    field = re.search(r'private final HidScannerInput hidScanner\s*=\s*([^;]+);', source)
    assert field, name + ' missing real input construction'
    pause_reset = ''
    if name in {'MainActivity', 'RewardsProfileScanActivity'}:
        pause = source[source.index('protected void onPause()'):]
        pause = pause[:pause.index('super.onPause()')]
        reset = re.search(r'hidScanner\.reset\(\);', pause)
        assert reset, name + ' missing partial-frame lifecycle invalidation'
        pause_reset = 'void pauseScan(){' + reset.group(0) + '}'
    dialogs += pause_reset
    shim = name + 'DispatchReplay'
    SOURCES['com/bitcoinrewards/nfcdisplay/' + shim + '.java'] = '''package com.bitcoinrewards.nfcdisplay;
import android.view.KeyEvent;
class %s extends android.app.Activity {
 final HidScannerInput hidScanner=%s; int callbacks; boolean eligible;
 java.util.List<String> destinations=new java.util.ArrayList<>();
 boolean canAcceptCustomerScan(){return eligible;}
 void acceptHidWalletScan(String raw){WalletQrPayloadParser.ParsedPayload p=WalletQrPayloadParser.parse(raw);if(p!=null){callbacks++;destinations.add(p.value);}}
 %s
 %s
}''' % (shim, field.group(1), dispatch, dialogs)
    assembly.append('''{
 %s surface=new %s();
 for(int api:new int[]{19,28,29,36}){Build.VERSION.SDK_INT=api;
 for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}){
 for(int code:new int[]{29,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER}){
 KeyEvent e=new KeyEvent(new InputDevice(),'x');e.action=action;e.key=code;
 check(surface.dispatchKeyEvent(e),"%s dispatch leak");
 %s
 }}}
 check(surface.focusedEvents==0 && surface.callbacks==0,"%s focused financial control/callback reached");
 KeyEvent keyboard=new KeyEvent(new InputDevice(),'x');keyboard.device.vendor=1;
 check(!surface.dispatchKeyEvent(keyboard) && surface.focusedEvents==1,"ordinary keyboard preserved");
 passed("%s source-assembled dispatch and dialogs");
}''' % (shim, shim, name, '\n'.join('check(surface.dialog%d(e),"dialog scanner leak");' % i for i in range(len(listeners))), name, name))
replay_path = 'com/bitcoinrewards/nfcdisplay/HidReplay.java'
SOURCES[replay_path] = SOURCES[replay_path].replace(
    '  System.out.println(cases+',
    '  KeyEvent.unicodeReads=0;\n' + '\n'.join(assembly) +
    '\n check(KeyEvent.unicodeReads==0,"consume-only surfaces must not decode or parse");\n  System.out.println(cases+')

# Cross-Activity regression uses exact production field initializers, dispatch
# bodies and dialog expressions above, not hand-wired adapter instances.
SOURCES[replay_path] = SOURCES[replay_path].replace(
    ' public static void main(String[] args){', '''
 static void route(android.app.Activity surface,String value){
  for(char c:value.toCharArray()){KeyEvent e=key(c);check(surface.dispatchKeyEvent(e),"routed DOWN");e.action=KeyEvent.ACTION_UP;check(surface.dispatchKeyEvent(e),"routed UP");}
 }
 static void delimiter(android.app.Activity surface){KeyEvent e=key(0);e.key=KeyEvent.KEYCODE_ENTER;check(surface.dispatchKeyEvent(e),"routed Enter DOWN");e.action=KeyEvent.ACTION_UP;check(surface.dispatchKeyEvent(e),"routed Enter UP");}
 public static void main(String[] args){''')
cross = '''
 reset();
 MainActivityDispatchReplay main=new MainActivityDispatchReplay(); main.eligible=true;
 SettingsActivityDispatchReplay settings=new SettingsActivityDispatchReplay();
 delimiter(main);delimiter(settings); // observed boundaries, no synthetic clearing of shared state
 KeyEvent.unicodeReads=0;route(settings,"alice.");
 check(KeyEvent.unicodeReads==0,"Settings must not decode blocked prefix");
 route(main,"bob@example.com");delimiter(main);
 check(main.destinations.isEmpty(),"cross-Activity blocked prefix submitted wrong destination: "+main.destinations);
 route(main,"alice.bob@example.com");delimiter(main);
 check(main.destinations.equals(Arrays.asList("alice.bob@example.com")),"cross-Activity recovery");
 passed("actual Settings/Main composition rejects valid suffix and recovers");
'''
cross += '''
 // Enabled Main -> disabled Settings -> enabled Main, including rapid pause.
 main.destinations.clear();route(main,"alice.");main.pauseScan();
 route(settings,"ignored");main.pauseScan();main.pauseScan();
 route(main,"bob@example.com");delimiter(main);
 check(main.destinations.isEmpty(),"round trip resurrected partial");
 route(main,"alice.bob@example.com");delimiter(main);
 check(main.destinations.size()==1,"round trip recovery");
 passed("enabled-disabled-enabled and rapid lifecycle invalidation");
 // No keys on the intervening screen: lifecycle still poisons the partial.
 main.destinations.clear();route(main,"alice.");main.pauseScan();
 route(main,"bob@example.com");delimiter(main);
 check(main.destinations.isEmpty(),"pause without blocked keys resurrected partial");
 passed("pause without intervening scanner input");
 // Consume-only delimiter establishes a boundary for the next full scan.
 route(settings,"blocked");delimiter(settings);delimiter(settings);
 route(main,"alice.bob@example.com");delimiter(main);
 check(main.destinations.size()==1,"blocked delimiter recovery");
 passed("cross-Activity blocked delimiter recovery");
 RewardsProfileScanActivityDispatchReplay profile=new RewardsProfileScanActivityDispatchReplay();profile.eligible=true;
 route(main,"alice.");main.pauseScan();route(profile,"bob@example.com");delimiter(profile);
 check(profile.destinations.isEmpty(),"Main/profile lifecycle suffix");
 route(profile,"alice.bob@example.com");delimiter(profile);
 check(profile.destinations.equals(Arrays.asList("alice.bob@example.com")),"eligible profile recovery");
 profile.destinations.clear();route(profile,"alice.");profile.pauseScan();main.destinations.clear();
 route(main,"bob@example.com");delimiter(main);check(main.destinations.isEmpty(),"profile/Main lifecycle suffix");
 passed("both eligible Activities share lifecycle invalidation");
'''
for name in sorted(names):
    shim = name + 'DispatchReplay'
    cross += '''{
 %s other=new %s();
 check(main.hidScanner==other.hidScanner,"%s must share process owner");
 main.destinations.clear();
 delimiter(main);route(main,"alice.");main.pauseScan();
 KeyEvent crossUp=key('x');crossUp.action=KeyEvent.ACTION_UP;scanner.id++;
 check(other.dispatchKeyEvent(crossUp),"cross source-UP consumed");scanner.id--;
 route(main,"bob@example.com");delimiter(main);
 check(main.destinations.isEmpty(),"%s source-UP retained suffix");
 route(main,"alice.bob@example.com");delimiter(main);
 check(main.destinations.size()==1,"source-UP recovery");
 main.destinations.clear();route(settings,"alice.");
 scanner.external=false;check(other.dispatchKeyEvent(key('x')),"unattested consumed");
 scanner.external=true;route(main,"bob@example.com");delimiter(main);
 check(main.destinations.isEmpty(),"cross attestation retained suffix");
 route(main,"alice.bob@example.com");delimiter(main);
 check(main.destinations.size()==1,"attestation recovery");
 passed("%s shared owner/source-UP/attestation composition");
}''' % (shim, shim, name, name, name)
    source = (production / (name + '.java')).read_text()
    for i in range(source.count('new AlertDialog.Builder')):
        cross += '''{
 %s dialogOwner=new %s();main.destinations.clear();delimiter(main);
 KeyEvent.unicodeReads=0;
 for(char c:"alice.".toCharArray()){KeyEvent e=key(c);check(dialogOwner.dialog%d(e),"dialog prefix consumed");e.action=KeyEvent.ACTION_UP;check(dialogOwner.dialog%d(e),"dialog UP consumed");}
 check(KeyEvent.unicodeReads==0,"dialog must not decode");
 route(main,"bob@example.com");delimiter(main);
 check(main.destinations.isEmpty(),"%s dialog %d prefix escaped");
 route(main,"alice.bob@example.com");delimiter(main);
 check(main.destinations.size()==1,"dialog recovery");
 passed("%s dialog %d cross-Activity framing");
}''' % (shim, shim, i, i, name, i, name, i)
diagnostic = (ROOT / 'app/src/debug/java/com/bitcoinrewards/nfcdisplay/HidDiagnosticActivity.java').read_text()
diagnostic_field = re.search(r'private final HidScannerInput input\s*=\s*([^;]+);', diagnostic)
assert diagnostic_field
cross += 'check(main.hidScanner==' + diagnostic_field.group(1) + ',"debug diagnostic must share owner");passed("debug diagnostic real initializer shares process owner");\n'
SOURCES[replay_path] = SOURCES[replay_path].replace('  System.out.println(cases+', cross + '\n  System.out.println(cases+')

with tempfile.TemporaryDirectory(prefix='hid-replay-', dir=ROOT.parent) as temp:
    work = Path(temp)
    files = []
    for relative, source in SOURCES.items():
        path = work / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        files.append(str(path))
    production = ROOT / 'app/src/main/java/com/bitcoinrewards/nfcdisplay'
    files += [str(production / (name + '.java')) for name in ('HidScannerInput', 'HidScanFramer', 'WalletQrPayloadParser')]
    subprocess.run([str(JAVA / 'javac'), '-d', str(work), *files], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(work), 'com.bitcoinrewards.nfcdisplay.HidReplay'], check=True)
