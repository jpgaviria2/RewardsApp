package com.bitcoinrewards.nfcdisplay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.TextView;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.camera.CameraSettings;

public class RewardsProfileScanActivity extends Activity implements RewardsScanCoordinator.Listener {
    private static final int CAMERA_PERMISSION_REQUEST=4201;
    private DecoratedBarcodeView barcode; private TextView status; private boolean front=true; private RewardsScanCoordinator coordinator;
    @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_rewards_profile_scan);barcode=findViewById(R.id.barcode_scanner);status=findViewById(R.id.scan_status);coordinator=RewardsScanCoordinator.get(getApplicationContext());Button close=findViewById(R.id.btn_close_scan);close.setOnClickListener(v->finish());Button flip=findViewById(R.id.btn_flip_camera);flip.setOnClickListener(v->{front=!front;if(!coordinator.hasOperation())startCamera();});}
    @Override protected void onResume(){super.onResume();coordinator.attach(this);if(coordinator.hasOperation()){lock("Resuming the locked reward operation…");return;}if(!SettingsActivity.scannerConfigurationUsable(this)){lock("Provision this scanner with a scoped device token in settings.");return;}if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION_REQUEST);else startCamera();}
    @Override protected void onPause(){coordinator.detach(this);if(barcode!=null)barcode.pause();super.onPause();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==CAMERA_PERMISSION_REQUEST&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startCamera();else lock("Camera permission is required.");}
    private void startCamera(){if(coordinator.hasOperation())return;CameraSettings c=new CameraSettings();c.setRequestedCameraId(front?1:0);c.setAutoFocusEnabled(true);c.setContinuousFocusEnabled(true);barcode.setCameraSettings(c);status.setText("Show the customer’s wallet payout QR.");barcode.decodeContinuous(callback);barcode.resume();}
    private final BarcodeCallback callback=new BarcodeCallback(){@Override public void barcodeResult(BarcodeResult result){if(coordinator.hasOperation()||result==null||result.getText()==null)return;try{WalletQrPayloadParser.ParsedPayload payload=WalletQrPayloadParser.parse(result.getText());if(coordinator.begin(payload)){barcode.pause();vibrate();}else lock("Check-in could not be started safely.");}catch(IllegalArgumentException e){status.setText(WalletQrPayloadParser.userFacingRejection(result.getText()));}}};
    @Override public void onStatus(String message){status.setText(message);}
    @Override public void onLocked(String message){lock(message);}
    @Override public void onRetryRequired(){showRetryCancel();}
    @Override public void onConfirmation(CheckInApiContract.ConfirmationResponse result,String localDestinationLabel){String secondary=result.label.isEmpty()?"":"\nServer-verified metadata: "+result.label;new AlertDialog.Builder(this).setTitle("Confirm sats reward").setMessage("Send exactly "+result.amountSatoshis+" sats to "+localDestinationLabel+"?"+secondary).setPositiveButton("Confirm and pay",(d,w)->coordinator.confirm()).setNegativeButton("Cancel",(d,w)->showRetryCancel()).setCancelable(false).show();}
    @Override public void onTerminal(String message){status.setText(message);finish();}
    private void showRetryCancel(){lock("This reward operation remains locked until the server reports a terminal result.");new AlertDialog.Builder(this).setTitle("Operation still locked").setMessage("Retry by reopening this screen, or request cancellation and keep waiting for the server’s authoritative cancelled/unpaid result.").setPositiveButton("Keep waiting",(d,w)->coordinator.resumeOrReport()).setNegativeButton("Cancel this operation",(d,w)->coordinator.requestCancellation()).setCancelable(false).show();}
    private void lock(String message){if(barcode!=null)barcode.pause();status.setText(message);}
    private void vibrate(){try{Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null)v.vibrate(80);}catch(Exception ignored){}}
}
