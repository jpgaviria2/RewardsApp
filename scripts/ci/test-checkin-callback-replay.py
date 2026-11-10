from pathlib import Path
import subprocess, json, hashlib
R=Path(__file__).resolve().parents[2]
O=R/'evidence'/'repair-callback-probe'; O.mkdir(exist_ok=True)
s=(R/'app/src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java').read_text()
def method(marker):
 start=s.index(marker); brace=s.index('{',start); depth=1; end=brace+1
 while depth:
  if s[end]=='{': depth+=1
  elif s[end]=='}': depth-=1
  end+=1
 return s[start:end]
callback=method('@Override protected void onPostExecute(Boolean ok)')
admission=method('private boolean canAcceptCustomerScan()')
extra='\n'.join(method(marker) for marker in ['private void reconcileCompletedCheckIn()'] if marker in s)
resume=method('@Override protected void onResume()')
pause=method('@Override protected void onPause()')
# Exact callback/admission and actual celebration + NFC gate. UI/framework and
# lifecycle entry state are doubles; not a native Android lifecycle execution.
text='''package com.bitcoinrewards.nfcdisplay;
class ActivityBase {protected void onResume(){}protected void onPause(){}}
public class CallbackProbe extends ActivityBase {
 boolean activityResumed=true,scannerPageEligible=true,checkInCapabilityVerified=true;
 boolean inlineSubmissionInFlight,captureConfirmationVisible,loginExchange,pendingDisplayConfigurationCommit,blocked,collectionLaunching,collectionWaitingPage,checkInDisplayRefreshPending;
 String trustedDisplayUrl="display";
 static class PaymentOperationStore {PaymentOperationStore(Object c){}boolean exists(){return false;}}
 void openCollection(){}void setTutorialActive(boolean b){}void disableNfcReader(){}void pauseInlineRewardsScanner(){cameraRunning=false;}
 static class Hid {void reset(){}}Hid hidScanner=new Hid();
 class ParentActivity {protected void onResume(){}protected void onPause(){}}
 String inlineAuthenticationFailure,lastSubmittedValue,status; long captureEpoch=4;
 Recovery trustedDisplayRecovery=null; static class Recovery {boolean isRecovering(){return false;}}
 boolean collectionBlocked(){return blocked;}
 int hides,updates,successes,reloads; boolean cameraRunning;
 final CheckInCelebration celebration=new CheckInCelebration();
 final NfcReaderGate gate=new NfcReaderGate(); final NfcReaderGate nfcReaderGate=gate;
 Handler recoveryHandler=new Handler(); class Handler {Runnable pending;void postDelayed(Runnable r,long ms){pending=r;}}
 Runnable finishCelebration; Web webView=new Web();class Web {void reload(){reloads++;}void loadUrl(String s){}}
 boolean isFinishing(){return false;}
 void hideCaptureOverlays(){hides++;celebration.clear();captureConfirmationVisible=false;recoveryHandler.pending=null;}
 void updateInlineScannerState(){EXTRA_UPDATE updates++;gate.update(activityResumed,scannerPageEligible,false,false,inlineSubmissionInFlight||captureConfirmationVisible);cameraRunning=activityResumed&&!inlineSubmissionInFlight&&!captureConfirmationVisible;}
 void setRewardsScannerStatus(String t){status=t;}
 void showSuccessOverlay(String t){successes++;captureConfirmationVisible=true;gate.update(true,true,false,false,true);}
 void vibrate(int n){}
 static class WalletQrPayloadParser {enum Kind{LNURL_PAY}static class ParsedPayload{Kind kind;}}
 static class InlineCheckInAuthentication {static String failureMessage(int n){return "failure";}}
 Task submitTask;
 class Parent {protected void onPostExecute(Boolean ok){}}
 class Task extends Parent {long requestEpoch=captureEpoch;boolean loginExpired;String confirmedCustomerHandle="";String confirmedCheckInId="11111111-1111-1111-1111-111111111111",boltcardUrl;int responseCode=200;WalletQrPayloadParser.ParsedPayload payload;
 CALLBACK
 }
 ADMISSION
 EXTRA
 RESUME
 PAUSE
 void start(){inlineSubmissionInFlight=true;celebration.begin(captureEpoch);submitTask=new Task();updateInlineScannerState();}
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  CallbackProbe normal=new CallbackProbe();normal.start();normal.submitTask.onPostExecute(true);
  require(normal.successes==1,"normal callback reaches celebration after clearing in-flight");
  System.out.println("PASS normal callback reachable: success=1; no in-flight self-block");
  CallbackProbe late=new CallbackProbe();late.start();
  late.onPause();late.onResume();late.submitTask.onPostExecute(true);
  require(late.canAcceptCustomerScan()&&late.cameraRunning&&late.gate.shouldSubmit(),"stale completion must republish camera/NFC admission");
  require(late.successes==0&&late.reloads==1,"stale completion reconciles without celebration");
  require(late.celebration.name().isEmpty(),"no stale name");
  CallbackProbe background=new CallbackProbe();background.start();background.onPause();background.submitTask.onPostExecute(true);
  require(background.reloads==0&&background.successes==0,"no background effects");
  background.onResume();require(background.reloads==1&&background.cameraRunning&&background.gate.shouldSubmit(),"foreground reconciliation");
  CallbackProbe delayed=new CallbackProbe();delayed.start();delayed.submitTask.onPostExecute(true);delayed.onPause();
  require(delayed.recoveryHandler.pending==null,"pause cancels timer");delayed.onResume();
  require(delayed.reloads==1&&delayed.successes==1,"resume replaces canceled reload without re-celebrating");
  CallbackProbe blocked=new CallbackProbe();blocked.start();blocked.blocked=true;blocked.submitTask.onPostExecute(true);
  require(blocked.reloads==0&&blocked.successes==0,"no blocked financial surface reload");blocked.blocked=false;blocked.onResume();
  require(blocked.reloads==1,"reconcile after financial surface releases");

  System.out.println("PASS exact callback/admission/resume/pause: stale foreground/background, canceled-delay, financial-blocked recovery; no stale celebration/name");
 }
}
'''.replace('CALLBACK',callback).replace('ADMISSION',admission).replace('EXTRA_UPDATE', 'reconcileCompletedCheckIn();' if 'reconcileCompletedCheckIn();' in method('private void updateInlineScannerState()') else '').replace('EXTRA',extra).replace('RESUME',resume).replace('PAUSE',pause)
p=O/'CallbackProbe.java';p.write_text(text)
stub=O/'CollectionCheckInStore.java';stub.write_text('package com.bitcoinrewards.nfcdisplay; final class CollectionCheckInStore {static boolean valid(String id){return id!=null&&id.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")&&!id.equals("00000000-0000-0000-0000-000000000000");}}')
java=Path('/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin')
sources=[p,stub]+[R/'app/src/main/java/com/bitcoinrewards/nfcdisplay'/n for n in ['CheckInCelebration.java','NfcReaderGate.java']]
subprocess.run([str(java/'javac'),'-d',str(O),*map(str,sources)],check=True)
subprocess.run([str(java/'java'),'-cp',str(O),'com.bitcoinrewards.nfcdisplay.CallbackProbe'],check=True)
(O/'manifest.json').write_text(json.dumps({'callback_sha256':hashlib.sha256(callback.encode()).hexdigest(),'admission_sha256':hashlib.sha256(admission.encode()).hexdigest(),'boundary':'exact extracted callback/admission; production celebration/NFC gate; lifecycle entry state and UI/update effects modeled'},indent=2))
