#!/usr/bin/env python3
"""Replay real camera permission/startup branches with explicit Android doubles."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[2];O=R/'evidence/camera-mode';O.mkdir(parents=True,exist_ok=True)
s=(R/'app/src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java').read_text()
def method(sig):
 a=s.index(sig);i=s.index('{',a);d=1;j=i+1
 while d:
  if s[j]=='{':d+=1
  if s[j]=='}':d-=1
  j+=1
 return s[a:j]
settings=(R/'app/src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java').read_text()
start=settings.index('    static boolean isCameraCheckInEnabled(Context context)')
end=settings.index('\n    private void saveDisplayAndLaunch()',start)
setting= settings[start:end]
initial=s[s.index('        cameraCheckInEnabled = SettingsActivity.isCameraCheckInEnabled(this);'):s.index('        WebSettings webSettings')]
code='''package com.bitcoinrewards.nfcdisplay;
class Context {protected void onResume(){}static final int MODE_PRIVATE=0;boolean enabled;class Prefs{boolean getBoolean(String key,boolean fallback){return enabled?true:fallback;}}Prefs getSharedPreferences(String name,int mode){return new Prefs();}}
class SettingsActivity {static final String PREFS_NAME="prefs";'''+setting+'''}
class CameraModeReplay extends Context {
 boolean cameraCheckInEnabled,activityResumed=true,scannerPageEligible=true,inlineSubmissionInFlight,captureConfirmationVisible,collectionLaunching,loginExchange,pendingDisplayConfigurationCommit,inlineScannerRunning;
 boolean collectionWaitingPage;String trustedDisplayUrl="display";
 static class PaymentOperationStore{PaymentOperationStore(Object c){}boolean exists(){return false;}}
 class Web{void loadUrl(String url){}}Web webView=new Web();void openCollection(){}
 String inlineAuthenticationFailure;int permissionRequests,decodes,resumes,nfcUpdates,discoveries;
 static final int CAMERA_PERMISSION_REQUEST=5501;
 static class Manifest{static class permission{static final String CAMERA="camera";}}
 static class Build{static class VERSION{static final int SDK_INT=34;}static class VERSION_CODES{static final int M=23;}}
 static class PackageManager{static final int PERMISSION_GRANTED=0;}
 int checkSelfPermission(String p){return 1;}void requestPermissions(String[]p,int request){permissionRequests++;}
 static class View {static final int VISIBLE=0,GONE=8;int v;int getVisibility(){return v;}void setVisibility(int n){v=n;}void post(Runnable r){r.run();}}
 static class R{static class id{static final int main_split_layout=1;}}
 View findViewById(int n){return new View();}void applyResponsiveExperienceLayout(View v){}
 View rewardsScannerPanel=new View(),collectPaymentButton=new View();
 class Decoder{void decodeContinuous(Object x){decodes++;}void resume(){resumes++;}} Decoder inlineRewardsScanner=new Decoder();Object inlineRewardsCallback=new Object();
 class Hid{void reset(){}}Hid hidScanner=new Hid();
 class Selection{boolean hasCamera(){return true;}}Selection rewardsCameraSelection=new Selection();
 void reconcileCompletedCheckIn(){}boolean canAcceptCustomerScan(){return true;}boolean collectionBlocked(){return false;}
 void updateNfcReaderState(){nfcUpdates++;}void setTutorialActive(boolean a){}void pauseInlineRewardsScanner(){inlineScannerRunning=false;}void setRewardsScannerStatus(String s){}boolean hasCameraPermission(){return true;}void selectRewardsCamera(){}void configureInlineRewardsCamera(){}void pulseTapCard(){}
 void discoverRewardsCameras(){discoveries++;}
 void initializeCameraMode(){'''+initial+'''}
'''+method('private void requestCameraPermissionIfNeeded()')+'\n'+method('private void updateInlineScannerState()')+'\n'+method('@Override protected void onResume()')+'\n'+(method('private void refreshCameraCheckInMode()') if 'private void refreshCameraCheckInMode()' in s else '')+'''
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[]a){
  CameraModeReplay external=new CameraModeReplay();external.initializeCameraMode();external.requestCameraPermissionIfNeeded();external.updateInlineScannerState();
  require(external.discoveries==0,"default must not enumerate cameras");
  require(external.permissionRequests==0,"HID-only kiosk must not request camera permission");
  require(external.decodes==0&&external.resumes==0,"HID cue must not start hidden camera decoding");
  require(external.nfcUpdates==1,"NFC remains updated in HID mode");
  CameraModeReplay camera=new CameraModeReplay();camera.enabled=true;camera.initializeCameraMode();camera.updateInlineScannerState();
  require(camera.discoveries==1,"explicit setting reaches camera discovery");
  require(camera.permissionRequests==1&&camera.decodes==1&&camera.resumes==1,"explicit camera mode retains camera scanning");
  camera.pauseInlineRewardsScanner();camera.enabled=false;camera.onResume();
  require(!camera.cameraCheckInEnabled&&camera.resumes==1&&!camera.inlineScannerRunning,"older MainActivity must honor persisted camera revocation on resume");
  CameraModeReplay busy=new CameraModeReplay();busy.cameraCheckInEnabled=true;busy.inlineSubmissionInFlight=true;busy.updateInlineScannerState();require(busy.resumes==0,"submission blocks camera mode");
  System.out.println("PASS: exact production startup branches; HID default zero permission/decode/resume, NFC retained; explicit camera opt-in works; busy guard preserved");
 }}'''
(O/'CameraModeReplay.java').write_text(code)
subprocess.run(['javac','-d',str(O),str(O/'CameraModeReplay.java')],check=True)
subprocess.run(['java','-cp',str(O),'com.bitcoinrewards.nfcdisplay.CameraModeReplay'],check=True)
