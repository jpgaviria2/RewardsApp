"""Offline replay of exact production submission task, admission and lifecycle methods.
Android/framework/persistence effects are explicit doubles; HTTP is an in-memory URL handler.
No server, provider, DNS, check-in or payment is contacted.
"""
from pathlib import Path
import hashlib, json, subprocess
R = Path(__file__).resolve().parents[2]
P = R / 'app/src/main/java/com/bitcoinrewards/nfcdisplay'
O = R / 'evidence/repair-response-composition'
O.mkdir(exist_ok=True)
s = (P/'MainActivity.java').read_text()
def extract(text, marker):
    start=text.index(marker); end=text.index('{', start)+1; depth=1
    while depth:
        if text[end]=='{': depth+=1
        elif text[end]=='}': depth-=1
        end+=1
    return text[start:end]
markers=['private final class SubmitInlineCheckInTask', 'private boolean canAcceptCustomerScan()',
         '@Override protected void onResume()', '@Override protected void onPause()']
parts=[extract(s,m) for m in markers]
if 'private void reconcileCompletedCheckIn()' in s:
    parts.append(extract(s,'private void reconcileCompletedCheckIn()'))
# Assert that the model's reconciliation entry is actually wired in production.
update=extract(s,'private void updateInlineScannerState()')
reconcile='reconcileCompletedCheckIn();' if 'reconcileCompletedCheckIn();' in update else ''
text=r'''package com.bitcoinrewards.nfcdisplay;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
class ActivityBase {protected void onResume(){} protected void onPause(){}}
class AsyncTask<A,B,C> {
 protected C doInBackground(A... args){return null;}
 protected void onPostExecute(C value){} protected void onCancelled(){}
 boolean isCancelled(){return false;}
}
public class MainActivity extends ActivityBase {
 boolean activityResumed=true,scannerPageEligible=true,checkInCapabilityVerified=true;
 boolean inlineSubmissionInFlight,captureConfirmationVisible,loginExchange,pendingDisplayConfigurationCommit;
 boolean blocked,collectionLaunching,collectionWaitingPage,checkInDisplayRefreshPending;
 String trustedDisplayUrl="display",inlineAuthenticationFailure,lastSubmittedValue,status;
 long captureEpoch=4;
 static final String LEGACY_INLINE_DEVICE_ID="kiosk";
 Recovery trustedDisplayRecovery; static class Recovery {boolean isRecovering(){return false;}}
 boolean collectionBlocked(){return blocked;}
 // Camera policy is executed separately by test-kiosk-camera-mode.py; this probe models admission effects.
 void refreshCameraCheckInMode(){}
 void openCollection(){} void setTutorialActive(boolean b){} void disableNfcReader(){}
 void pauseInlineRewardsScanner(){cameraRunning=false;}
 static class Hid {void reset(){}} Hid hidScanner=new Hid();
 int hides,updates,successes,reloads; boolean cameraRunning;
 String visibleHandle="";
 final CheckInCelebration celebration=new CheckInCelebration();
 final NfcReaderGate nfcReaderGate=new NfcReaderGate();
 Handler recoveryHandler=new Handler(); class Handler {Runnable pending;void postDelayed(Runnable r,long ms){pending=r;}}
 Runnable finishCelebration; Web webView=new Web();class Web {void reload(){reloads++;}void loadUrl(String s){}}
 boolean isFinishing(){return false;}
 void hideCaptureOverlays(){hides++;celebration.clear();visibleHandle="";captureConfirmationVisible=false;recoveryHandler.pending=null;finishCelebration=null;}
 void updateInlineScannerState(){RECONCILE updates++;nfcReaderGate.update(activityResumed&&!blocked,scannerPageEligible,false,inlineAuthenticationFailure!=null,inlineSubmissionInFlight||captureConfirmationVisible);cameraRunning=canAcceptCustomerScan();}
 void setRewardsScannerStatus(String t){status=t;}
 void showSuccessOverlay(String t){successes++;visibleHandle=t;captureConfirmationVisible=true;nfcReaderGate.update(true,true,false,false,true);}
 void vibrate(int n){}
 SubmitInlineCheckInTask submitTask;
 METHODS
 void start(boolean card){inlineSubmissionInFlight=true;celebration.begin(captureEpoch);submitTask=card?new SubmitInlineCheckInTask("lnurlw://card"):new SubmitInlineCheckInTask(new WalletQrPayloadParser.ParsedPayload());updateInlineScannerState();}
 void complete(){SubmitInlineCheckInTask task=submitTask;boolean ok=task.doInBackground();task.onPostExecute(ok);}
 static byte[] body(String address,boolean processed)throws Exception{return new JSONObject().put("status","checked_in").put("checkInId",Connection.id).put("expiresAt","2026-11-10T18:05:00Z").put("lightningAddress",address).put("testRewardProcessed",processed).toString().getBytes(StandardCharsets.UTF_8);}
 static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  URL.setURLStreamHandlerFactory(protocol->new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}});
  for(boolean card:new boolean[]{false,true})for(boolean processed:new boolean[]{false,true}){
   MainActivity a=new MainActivity();Connection.bytes=body("coffeelover001@TRAILSCOFFEE.COM",processed);a.start(card);a.complete();
   require(a.successes==1,"both actual route envelopes must celebrate");
   require(a.visibleHandle.equals("coffeelover001"),"confirmed server handle must reach actual callback, not raw QR");
   require(CollectionCheckInStore.captured.equals(Connection.id),"financial capture retained");
   a.onPause();require(a.visibleHandle.isEmpty()&&a.celebration.name().isEmpty(),"pause clears handle");a.onResume();require(a.reloads==1,"canceled success-delay reconciles on resume");
  }
  for(String address:new String[]{"custom.name-1@trailscoffee.com","_@trailscoffee.com"}){
   MainActivity a=new MainActivity();Connection.bytes=body(address,false);a.start(false);a.complete();
   require(a.visibleHandle.equals(address.substring(0,address.indexOf('@'))),"custom handle preserved");
  }
  for(String address:new String[]{"", "LNURL1PAYLINK", "ada@foreign.example", "ada@evil.trailscoffee.com", "ada@trailscoffee.com.evil", "ada@trail\u017fcoffee.com", "ada@@trailscoffee.com", "@trailscoffee.com", "ada@", " ada@trailscoffee.com", "ada @trailscoffee.com", "ada\n@trailscoffee.com", "ada\u202e@trailscoffee.com", "ada@trailscoffee.com\u0000", "a..b@trailscoffee.com", ".ada@trailscoffee.com", "ada.@trailscoffee.com", "<ada>@trailscoffee.com", "a".repeat(65)+"@trailscoffee.com"}){
   MainActivity a=new MainActivity();Connection.bytes=body(address,false);a.start(false);a.complete();
   require(a.successes==1&&a.visibleHandle.isEmpty(),"ineligible address must get generic confirmation: "+address);
  }
  MainActivity late=new MainActivity();Connection.bytes=body("old@trailscoffee.com",false);late.start(false);SubmitInlineCheckInTask old=late.submitTask;
  boolean ok=old.doInBackground();late.onPause();late.onResume();old.onPostExecute(ok);
  require(late.successes==0&&late.visibleHandle.isEmpty()&&late.cameraRunning&&late.nfcReaderGate.shouldSubmit()&&late.reloads==1,"stale handle suppressed, admission reconciled");
  Connection.id="22222222-1111-1111-1111-111111111111";Connection.bytes=body("new@trailscoffee.com",false);late.start(true);
  old.onPostExecute(true);require(late.inlineSubmissionInFlight&&late.successes==0,"retired task cannot consume newer operation");late.complete();
  old.onPostExecute(true);require(late.successes==1&&late.visibleHandle.equals("new"),"retired task cannot overwrite new handle");
  MainActivity kiosk=new MainActivity();
  for(int i=1;i<=1024;i++){
   Connection.id=String.format("%08x-1111-1111-1111-111111111111",i);Connection.bytes=body(i%2==0?"custom@trailscoffee.com":"foreign@example.com",false);
   kiosk.start(i%2==0);SubmitInlineCheckInTask owned=kiosk.submitTask;kiosk.complete();owned.onPostExecute(true);
   require(kiosk.successes==i,"long-running actual task must celebrate each new identity exactly once: "+i);
   require(kiosk.visibleHandle.equals(i%2==0?"custom":""),"previous customer's handle must not reach generic confirmation");
   kiosk.finishCelebration.run();require(kiosk.visibleHandle.isEmpty()&&kiosk.celebration.name().isEmpty(),"idle clears handle");
  }
  // Same current identity from another operation cannot re-celebrate, even after idle.
  kiosk.start(false);kiosk.complete();require(kiosk.successes==1024,"current identity duplicate blocked");
  MainActivity failed=new MainActivity();Connection.code=503;Connection.bytes="{\"error\":\"A customer is already checked in.\"}".getBytes(StandardCharsets.UTF_8);failed.start(true);failed.complete();
  require(failed.successes==0&&failed.visibleHandle.isEmpty(),"503 active duplicate never celebrates");Connection.code=200;
  MainActivity captureFailed=new MainActivity();CollectionCheckInStore.writable=false;Connection.bytes=body("custom@trailscoffee.com",false);captureFailed.start(false);captureFailed.complete();
  require(captureFailed.successes==0,"capture failure cannot celebrate");CollectionCheckInStore.writable=true;
  MainActivity legacy=new MainActivity();Connection.bytes="{\"success\":true,\"checkInId\":\"22222222-1111-1111-1111-111111111111\"}".getBytes(StandardCharsets.UTF_8);legacy.start(false);legacy.complete();require(legacy.successes==0,"invented success fixture is not confirmation");
  System.out.println("PASS exact task/body/callback composition: wallet+Boltcard five-field success, handle/fallback, stale/cross-task/duplicate/capture-failure, financial identity retained; no network");
 }
 static class Connection extends HttpURLConnection {
  static byte[] bytes;static int code=200;static String id="11111111-1111-1111-1111-111111111111";
  Connection(URL u){super(u);}public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
  public OutputStream getOutputStream(){return new ByteArrayOutputStream();}public int getResponseCode(){return code;}
  public int getContentLength(){return bytes.length;}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
 }
}
class SettingsActivity {
 static String getBtcpayUrl(Object c){return "https://merchant.example";}static String getStoreId(Object c){return "Store1";}
 static String buildLegacyCheckInUrl(String origin,String store){return origin+"/check-in/api";}
}
class CookieManager {static CookieManager getInstance(){return new CookieManager();}String getCookie(String url){return "auth";}}
class InlineCheckInAuthentication {
 interface Cookies {String get(String url);}static String cookieForTrustedApi(String a,String b,Cookies c){return c.get(b);}
 static boolean isLoginExpired(int code){return code==401||code==403;}static String failureMessage(int n){return "expired";}
}
class WalletQrPayloadParser {enum Kind{LNURL_PAY}static class ParsedPayload{Kind kind;String value="raw-qr-must-not-be-name@trailscoffee.com";}}
class PaymentOperationStore {PaymentOperationStore(Object c){}boolean exists(){return false;}}
'''.replace('METHODS','\n'.join(parts)).replace('RECONCILE',reconcile)
(O/'MainActivity.java').write_text(text)
store=(P/'CollectionCheckInStore.java').read_text()
store_methods='\n'.join(extract(store,m) for m in ['static boolean valid(', 'static String successId(', 'static byte[] readBody('])
(O/'CollectionCheckInStore.java').write_text('''package com.bitcoinrewards.nfcdisplay;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.UUID;import org.json.JSONObject;
class CollectionCheckInStore {
 static String captured;static boolean writable=true;
 CollectionCheckInStore(Object c){}boolean capture(String origin,String store,String id){captured=id;return writable;}
'''+store_methods+'\n}')
json_jars=list((Path.home()/'.gradle/caches/modules-2/files-2.1/org.json/json').glob('**/*.jar'))
assert len(json_jars)==1,json_jars
classpath=str(json_jars[0])
java=Path('/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin')
sources=[O/'MainActivity.java',O/'CollectionCheckInStore.java']+[P/n for n in ['CheckInCelebration.java','NfcReaderGate.java','ConfirmedCheckInResponse.java','PaymentContract.java','ExactJson.java','BoltcardCheckInEndpoint.java']]
subprocess.run([str(java/'javac'),'-cp',classpath,'-d',str(O),*map(str,sources)],check=True)
subprocess.run([str(java/'java'),'-cp',str(O)+':'+classpath,'com.bitcoinrewards.nfcdisplay.MainActivity'],check=True)
(O/'manifest.json').write_text(json.dumps({'extracted_sha256':{m:hashlib.sha256(extract(s,m).encode()).hexdigest() for m in markers},'boundary':__doc__},indent=2))
