#!/usr/bin/env python3
"""Offline exact-method replay; View/animator/Handler are explicit framework doubles."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'evidence/kiosk-presentation';OUT.mkdir(parents=True,exist_ok=True)
s=(ROOT/'app/src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java').read_text()
def method(sig):
 start=s.index(sig);p=s.index('{',start);depth=1;end=p+1
 while depth:
  if s[end]=='{':depth+=1
  if s[end]=='}':depth-=1
  end+=1
 return s[start:end]
methods='\n'.join(method(sig) for sig in ['private void setRewardsScannerStatus','private void showSuccessOverlay','private void hideCaptureOverlays'])
code='''package com.bitcoinrewards.nfcdisplay;
// Admission is tested in existing response/callback composition, never invoked by this UI-only replay.
class CollectionCheckInStore {static boolean valid(String s){throw new AssertionError("Unexpected admission in UI replay");}}
class KioskPresentationReplay {
 static class View {static final int GONE=8,VISIBLE=0;int visibility=GONE;String text="";void setText(String s){text=s;}void setText(int r){text="ready";}void setVisibility(int v){visibility=v;}void setScaleX(float x){}void setScaleY(float y){}void setAlpha(float a){}View animate(){return this;}View alpha(float x){return this;}View scaleX(float x){return this;}View scaleY(float y){return this;}View setDuration(long x){return this;}void start(){}void cancel(){}}
 static class R {static class string {static final int scanner_ready=1;}}
 static class Handler{void removeCallbacks(Runnable r){}}
 static class Confetti{boolean running;void start(){running=true;}void stop(){running=false;}}
 Handler recoveryHandler=new Handler();Runnable finishCelebration;CheckInCelebration celebration=new CheckInCelebration();
 View rewardsScannerStatus=new View(),successOverlay=new View(),submissionOverlay=new View();Confetti confettiView=new Confetti();boolean captureConfirmationVisible;
 void updateNfcReaderState(){}
'''+methods+'''
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[]a){KioskPresentationReplay p=new KioskPresentationReplay();
 p.setRewardsScannerStatus("Ready — tap a card or show your sats wallet QR");require(p.rewardsScannerStatus.visibility==View.GONE,"routine status hidden");
 p.setRewardsScannerStatus("Could not reach BTCPay to save this rewards profile.");require(p.rewardsScannerStatus.visibility==View.VISIBLE,"actual errors remain visible");
 p.hideCaptureOverlays();require(p.rewardsScannerStatus.visibility==View.GONE,"idle clear removes stale error banner");
 p.submissionOverlay.visibility=View.VISIBLE;p.showSuccessOverlay("coffeeloverfixture");
 require(p.successOverlay.visibility==View.VISIBLE&&p.submissionOverlay.visibility==View.GONE,"confirmed replaces submission");
 require(p.captureConfirmationVisible&&p.confettiView.running&&p.successOverlay.text.contains("coffeeloverfixture"),"full card confirms handle with confetti");
 p.hideCaptureOverlays();require(!p.captureConfirmationVisible&&!p.confettiView.running&&p.successOverlay.visibility==View.GONE&&p.successOverlay.text.isEmpty()&&p.submissionOverlay.text.isEmpty(),"clear removes all personal text and effects");
 System.out.println("PASS: exact presentation methods; routine/error visibility, exclusive full-card confirmation, handle/confetti lifecycle clearing; framework doubles only");
 }}'''
(OUT/'KioskPresentationReplay.java').write_text(code)
source=ROOT/'app/src/main/java/com/bitcoinrewards/nfcdisplay/CheckInCelebration.java'
subprocess.run(['javac','-d',str(OUT),str(source),str(OUT/'KioskPresentationReplay.java')],check=True)
subprocess.run(['java','-cp',str(OUT),'com.bitcoinrewards.nfcdisplay.KioskPresentationReplay'],check=True)
