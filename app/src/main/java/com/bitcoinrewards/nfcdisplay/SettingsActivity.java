package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int LOGIN_CODE_SCAN_REQUEST = 4401;
    static final String PREFS_NAME = "rewards_nfc_prefs";
    static final String KEY_BTCPAY_URL = "btcpay_url", KEY_STORE_ID = "store_id", KEY_CHECK_IN_DEVICE_ID = "check_in_device_id";
    static final String KEY_TOKEN_FINGERPRINT = "scanner_token_fingerprint", KEY_TOKEN_GENERATION = "scanner_token_generation", KEY_ONBOARDED = "onboarded";
    static final String KEY_CREDENTIAL_UPDATE_PENDING = "credential_update_pending";

    private EditText url, store, device, token;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LegacyCredentialPurge.run(this, () -> initialize(state));
    }

    private void initialize(Bundle state) {
        setContentView(R.layout.activity_settings);
        SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        url = findViewById(R.id.input_btcpay_url);
        store = findViewById(R.id.input_store_id);
        device = findViewById(R.id.input_check_in_device_id);
        token = findViewById(R.id.input_check_in_token);
        status = findViewById(R.id.status_text);
        url.setText(p.getString(KEY_BTCPAY_URL, ""));
        store.setText(p.getString(KEY_STORE_ID, ""));
        device.setText(p.getString(KEY_CHECK_IN_DEVICE_ID, ""));

        Button scanLogin = findViewById(R.id.btn_scan_login_code);
        scanLogin.setOnClickListener(v -> startActivityForResult(new Intent(this, LoginCodeScanActivity.class), LOGIN_CODE_SCAN_REQUEST));
        Button saveDisplay = findViewById(R.id.btn_save);
        saveDisplay.setOnClickListener(v -> saveDisplayAndLaunch());
        Button saveScanner = findViewById(R.id.btn_save_scanner);
        saveScanner.setOnClickListener(v -> saveScannerProvisioning());

        if (getIntent().getBooleanExtra("show_scanner_provisioning", false)) {
            status.setText("Provision a scoped rewards-scanner device ID and token below. Display login remains separate.");
            status.setVisibility(View.VISIBLE);
            getIntent().removeExtra("show_scanner_provisioning");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != LOGIN_CODE_SCAN_REQUEST || resultCode != RESULT_OK) return;
        String raw = OneTimeLoginHandoff.consume();
        try {
            String configured = url.getText().toString().trim();
            LoginCodePayloadParser.ParsedPayload parsed = LoginCodePayloadParser.parse(raw, configured);
            raw = null;
            url.setText(parsed.origin);
            String origin = persistDisplayConfiguration(parsed.origin, store.getText().toString());
            String displayUrl = buildDisplayUrl(origin, store.getText().toString().trim());
            String loginUrl = LoginCodePayloadParser.buildTrustedLoginUrl(parsed, displayUrl);
            OneTimeLoginHandoff.publish(loginUrl);
            Intent main = new Intent(this, MainActivity.class);
            // MainActivity uses the standard launch mode, so CLEAR_TOP replaces any stale expired instance.
            main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(main);
            finish();
        } catch (Exception e) {
            status.setText("Login QR rejected. Use a fresh HTTPS BTCPay login QR for the configured server and enter the Store ID.");
            status.setVisibility(View.VISIBLE);
        }
    }

    private void saveDisplayAndLaunch() {
        try {
            persistDisplayConfiguration(url.getText().toString(), store.getText().toString());
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } catch (Exception e) {
            status.setText("Enter a valid HTTPS BTCPay server URL and Store ID.");
            status.setVisibility(View.VISIBLE);
        }
    }

    private void saveScannerProvisioning() {
        try {
            String origin = persistDisplayConfiguration(url.getText().toString(), store.getText().toString());
            String storeId = store.getText().toString().trim();
            String deviceId = device.getText().toString().trim();
            String value = token.getText().toString();
            PendingOperation.validateText(deviceId, 256);
            if (deviceId.isEmpty()) throw new IllegalArgumentException();
            if (value.isEmpty() && !scannerConfigurationUsable(this)) throw new IllegalArgumentException();
            SecureTokenStore secure = new SecureTokenStore(this);
            if (!secure.available()) throw new IllegalStateException();
            CredentialUpdateState update = new CredentialUpdateState(new PreferenceMetadata(this), new SecureStorage(secure));
            if (!update.update(origin, storeId, deviceId, value)) throw new IllegalStateException();
            token.setText("");
            status.setText("Scoped rewards scanner provisioning saved. The display login session remains in WebView cookies.");
            status.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            status.setText("Scanner provisioning requires the current display server/store, a device ID, and a scoped token.");
            status.setVisibility(View.VISIBLE);
        }
    }

    private String persistDisplayConfiguration(String requestedOrigin, String requestedStore) {
        String origin = ApiOrigin.requireConfigured(requestedOrigin == null ? "" : requestedOrigin.trim()).toString();
        String storeId = requestedStore == null ? "" : requestedStore.trim();
        PendingOperation.validateText(storeId, 256);
        if (storeId.isEmpty()) throw new IllegalArgumentException();
        SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String oldOrigin = p.getString(KEY_BTCPAY_URL, "");
        String oldStore = p.getString(KEY_STORE_ID, "");
        SharedPreferences.Editor editor = p.edit().putString(KEY_BTCPAY_URL, origin).putString(KEY_STORE_ID, storeId);
        if (!origin.equals(oldOrigin) || !storeId.equals(oldStore)) {
            editor.remove(KEY_CHECK_IN_DEVICE_ID).remove(KEY_TOKEN_FINGERPRINT).remove(KEY_TOKEN_GENERATION)
                .putBoolean(KEY_ONBOARDED, false).putBoolean(KEY_CREDENTIAL_UPDATE_PENDING, false);
            device.setText("");
            token.setText("");
        }
        if (!editor.commit()) throw new IllegalStateException();
        return origin;
    }

    private static boolean pending(Context c) {
        return c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_CREDENTIAL_UPDATE_PENDING, false);
    }

    static boolean isOnboarded(Context c) {
        return !pending(c) && c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false);
    }

    static String getBtcpayUrl(Context c) {
        return c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_BTCPAY_URL, "");
    }

    static String getStoreId(Context c) {
        return c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_STORE_ID, "");
    }

    static String getCheckInDeviceId(Context c) {
        return pending(c) ? "" : c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CHECK_IN_DEVICE_ID, "");
    }

    static String getCheckInToken(Context c) {
        return pending(c) ? "" : new SecureTokenStore(c).load();
    }

    static String getCredentialFingerprint(Context c) {
        return pending(c) ? "" : c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TOKEN_FINGERPRINT, "");
    }

    static long getTokenGeneration(Context c) {
        return pending(c) ? 0 : c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_TOKEN_GENERATION, 0);
    }

    static boolean displayConfigurationUsable(Context c) {
        try {
            String storeId = getStoreId(c);
            PendingOperation.validateText(storeId, 256);
            return !storeId.isEmpty() && ApiOrigin.requireConfigured(getBtcpayUrl(c)) != null && getDisplayUrl(c) != null;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean scannerConfigurationUsable(Context c) {
        return displayConfigurationUsable(c) && isOnboarded(c) && !getCheckInDeviceId(c).isEmpty() && !getCheckInToken(c).isEmpty()
            && !getCredentialFingerprint(c).isEmpty() && getTokenGeneration(c) > 0;
    }

    static String getDisplayUrl(Context c) {
        try { return ApiOrigin.requireConfigured(getBtcpayUrl(c)).toString() + "/plugins/bitcoin-rewards/" + PathSegmentCodec.encode(getStoreId(c)) + "/display"; }
        catch (Exception e) { return null; }
    }

    static String getLegacyCheckInUrl(Context c) {
        try { return buildLegacyCheckInUrl(getBtcpayUrl(c), getStoreId(c)); }
        catch (Exception e) { return null; }
    }

    static String buildLegacyCheckInUrl(String origin, String storeId) {
        PendingOperation.validateText(storeId, 256);
        if (storeId == null || storeId.isEmpty()) throw new IllegalArgumentException();
        return ApiOrigin.requireConfigured(origin).toString() + "/plugins/bitcoin-rewards/" + PathSegmentCodec.encode(storeId) + "/check-in/api";
    }

    private static String buildDisplayUrl(String origin, String storeId) {
        return ApiOrigin.requireConfigured(origin).toString() + "/plugins/bitcoin-rewards/" + PathSegmentCodec.encode(storeId) + "/display";
    }

    private static final class SecureStorage implements CredentialUpdateState.TokenStorage {
        private final SecureTokenStore secure;
        SecureStorage(SecureTokenStore secure) { this.secure = secure; }
        public boolean save(String value) { return secure.save(value); }
        public String load() { return secure.load(); }
    }

    private static final class PreferenceMetadata implements CredentialUpdateState.MetadataStorage {
        private final SharedPreferences p;
        PreferenceMetadata(Context c) { p = c.getSharedPreferences(PREFS_NAME, MODE_PRIVATE); }
        public CredentialUpdateState.Metadata read() { return new CredentialUpdateState.Metadata(p.getString(KEY_BTCPAY_URL, ""), p.getString(KEY_STORE_ID, ""), p.getString(KEY_CHECK_IN_DEVICE_ID, ""), p.getString(KEY_TOKEN_FINGERPRINT, ""), p.getLong(KEY_TOKEN_GENERATION, 0), p.getBoolean(KEY_CREDENTIAL_UPDATE_PENDING, false), p.getBoolean(KEY_ONBOARDED, false)); }
        public boolean markPending() { return p.edit().putBoolean(KEY_CREDENTIAL_UPDATE_PENDING, true).putBoolean(KEY_ONBOARDED, false).commit(); }
        public boolean finish(CredentialUpdateState.Metadata m) { return p.edit().putString(KEY_BTCPAY_URL, m.origin).putString(KEY_STORE_ID, m.store).putString(KEY_CHECK_IN_DEVICE_ID, m.device).putString(KEY_TOKEN_FINGERPRINT, m.fingerprint).putLong(KEY_TOKEN_GENERATION, m.generation).putBoolean(KEY_CREDENTIAL_UPDATE_PENDING, false).putBoolean(KEY_ONBOARDED, true).commit(); }
    }
}
