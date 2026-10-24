package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    static final String PREFS_NAME="rewards_nfc_prefs";
    static final String KEY_BTCPAY_URL="btcpay_url",KEY_STORE_ID="store_id",KEY_CHECK_IN_DEVICE_ID="check_in_device_id";
    static final String KEY_TOKEN_FINGERPRINT="scanner_token_fingerprint",KEY_TOKEN_GENERATION="scanner_token_generation",KEY_ONBOARDED="onboarded";
    static final String KEY_CREDENTIAL_UPDATE_PENDING="credential_update_pending";
    private EditText url,store,device,token; private TextView status;
    @Override protected void onCreate(Bundle state){super.onCreate(state);LegacyCredentialPurge.run(this,()->initialize(state));}
    private void initialize(Bundle state){setContentView(R.layout.activity_settings);SharedPreferences p=getSharedPreferences(PREFS_NAME,MODE_PRIVATE);url=findViewById(R.id.input_btcpay_url);store=findViewById(R.id.input_store_id);device=findViewById(R.id.input_check_in_device_id);token=findViewById(R.id.input_check_in_token);status=findViewById(R.id.status_text);url.setText(p.getString(KEY_BTCPAY_URL,""));store.setText(p.getString(KEY_STORE_ID,""));device.setText(p.getString(KEY_CHECK_IN_DEVICE_ID,""));Button save=findViewById(R.id.btn_save);save.setOnClickListener(v->save());}
    private void save(){try{String origin=ApiOrigin.requireConfigured(url.getText().toString().trim()).toString();String storeId=store.getText().toString().trim(),deviceId=device.getText().toString().trim(),value=token.getText().toString();PendingOperation.validateText(storeId,256);PendingOperation.validateText(deviceId,256);SecureTokenStore secure=new SecureTokenStore(this);if(!secure.available())throw new IllegalStateException();CredentialUpdateState update=new CredentialUpdateState(new PreferenceMetadata(this),new SecureStorage(secure));if(!update.update(origin,storeId,deviceId,value))throw new IllegalStateException();startActivity(new Intent(this,MainActivity.class));finish();}catch(Exception e){status.setText("Configuration could not be saved securely.");}}
    private static boolean pending(Context c){return c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getBoolean(KEY_CREDENTIAL_UPDATE_PENDING,false);}
    static boolean isOnboarded(Context c){return !pending(c)&&c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getBoolean(KEY_ONBOARDED,false);}
    static String getBtcpayUrl(Context c){return pending(c)?"":c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getString(KEY_BTCPAY_URL,"");}
    static String getStoreId(Context c){return pending(c)?"":c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getString(KEY_STORE_ID,"");}
    static String getCheckInDeviceId(Context c){return pending(c)?"":c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getString(KEY_CHECK_IN_DEVICE_ID,"");}
    static String getCheckInToken(Context c){return pending(c)?"":new SecureTokenStore(c).load();}
    static String getCredentialFingerprint(Context c){return pending(c)?"":c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getString(KEY_TOKEN_FINGERPRINT,"");}
    static long getTokenGeneration(Context c){return pending(c)?0:c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE).getLong(KEY_TOKEN_GENERATION,0);}
    static boolean configurationUsable(Context c){return isOnboarded(c)&&!getBtcpayUrl(c).isEmpty()&&!getStoreId(c).isEmpty()&&!getCheckInDeviceId(c).isEmpty()&&!getCheckInToken(c).isEmpty()&&!getCredentialFingerprint(c).isEmpty()&&getTokenGeneration(c)>0;}
    static String getDisplayUrl(Context c){try{return getBtcpayUrl(c)+"/plugins/bitcoin-rewards/"+PathSegmentCodec.encode(getStoreId(c))+"/display";}catch(Exception e){return null;}}
    private static final class SecureStorage implements CredentialUpdateState.TokenStorage{private final SecureTokenStore secure;SecureStorage(SecureTokenStore secure){this.secure=secure;}public boolean save(String value){return secure.save(value);}public String load(){return secure.load();}}
    private static final class PreferenceMetadata implements CredentialUpdateState.MetadataStorage{private final SharedPreferences p;PreferenceMetadata(Context c){p=c.getSharedPreferences(PREFS_NAME,MODE_PRIVATE);}public CredentialUpdateState.Metadata read(){return new CredentialUpdateState.Metadata(p.getString(KEY_BTCPAY_URL,""),p.getString(KEY_STORE_ID,""),p.getString(KEY_CHECK_IN_DEVICE_ID,""),p.getString(KEY_TOKEN_FINGERPRINT,""),p.getLong(KEY_TOKEN_GENERATION,0),p.getBoolean(KEY_CREDENTIAL_UPDATE_PENDING,false),p.getBoolean(KEY_ONBOARDED,false));}public boolean markPending(){return p.edit().putBoolean(KEY_CREDENTIAL_UPDATE_PENDING,true).putBoolean(KEY_ONBOARDED,false).commit();}public boolean finish(CredentialUpdateState.Metadata m){return p.edit().putString(KEY_BTCPAY_URL,m.origin).putString(KEY_STORE_ID,m.store).putString(KEY_CHECK_IN_DEVICE_ID,m.device).putString(KEY_TOKEN_FINGERPRINT,m.fingerprint).putLong(KEY_TOKEN_GENERATION,m.generation).putBoolean(KEY_CREDENTIAL_UPDATE_PENDING,false).putBoolean(KEY_ONBOARDED,true).commit();}}
}
