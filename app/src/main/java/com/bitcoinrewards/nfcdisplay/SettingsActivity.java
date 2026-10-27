package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int STORE_QR_SCAN_REQUEST = 4400;
    private static final int LOGIN_CODE_SCAN_REQUEST = 4401;
    private static final String STATE_STORE_QR_SETUP_PENDING = "store_qr_setup_pending";
    private static final String STATE_CONFIRMING_STORE_QR = "confirming_store_qr";
    private static final String STATE_PENDING_STORE_ORIGIN = "pending_store_origin";
    private static final String STATE_PENDING_STORE_ID = "pending_store_id";
    private static final String STATE_PRE_STORE_QR_ORIGIN = "pre_store_qr_origin";
    private static final String STATE_PRE_STORE_QR_STORE_ID = "pre_store_qr_store_id";
    static final String PREFS_NAME = "rewards_nfc_prefs";
    static final String KEY_BTCPAY_URL = "btcpay_url", KEY_STORE_ID = "store_id", KEY_CHECK_IN_DEVICE_ID = "check_in_device_id";
    static final String KEY_TOKEN_FINGERPRINT = "scanner_token_fingerprint", KEY_TOKEN_GENERATION = "scanner_token_generation", KEY_ONBOARDED = "onboarded";
    static final String KEY_CREDENTIAL_UPDATE_PENDING = "credential_update_pending";

    private EditText url, store, device, token;
    private TextView status;
    private boolean storeQrSetupPending;
    private boolean confirmingStoreQr;
    private String pendingStoreOrigin, pendingStoreId, preStoreQrOrigin, preStoreQrStoreId;

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
        storeQrSetupPending = state != null && state.getBoolean(STATE_STORE_QR_SETUP_PENDING, false);
        confirmingStoreQr = state != null && state.getBoolean(STATE_CONFIRMING_STORE_QR, false);
        pendingStoreOrigin = state == null ? null : state.getString(STATE_PENDING_STORE_ORIGIN);
        pendingStoreId = state == null ? null : state.getString(STATE_PENDING_STORE_ID);
        preStoreQrOrigin = state == null ? null : state.getString(STATE_PRE_STORE_QR_ORIGIN);
        preStoreQrStoreId = state == null ? null : state.getString(STATE_PRE_STORE_QR_STORE_ID);
        if (storeQrSetupPending && pendingStoreOrigin != null && pendingStoreId != null) {
            url.setText(pendingStoreOrigin);
            store.setText(pendingStoreId);
        } else {
            url.setText(p.getString(KEY_BTCPAY_URL, ""));
            store.setText(p.getString(KEY_STORE_ID, ""));
        }
        device.setText(p.getString(KEY_CHECK_IN_DEVICE_ID, ""));

        Button scanStore = findViewById(R.id.btn_scan_store_qr);
        scanStore.setOnClickListener(v -> startStoreQrSetup());
        Button scanLogin = findViewById(R.id.btn_scan_login_code);
        scanLogin.setOnClickListener(v -> startLoginScan(false));
        Button saveDisplay = findViewById(R.id.btn_save);
        saveDisplay.setOnClickListener(v -> saveDisplayAndLaunch());
        Button saveScanner = findViewById(R.id.btn_save_scanner);
        saveScanner.setOnClickListener(v -> saveScannerProvisioning());

        if (getIntent().getBooleanExtra("show_scanner_provisioning", false)) {
            status.setText("Provision a scoped rewards-scanner device ID and token below. Display login remains separate.");
            status.setVisibility(View.VISIBLE);
            getIntent().removeExtra("show_scanner_provisioning");
        }
        if (confirmingStoreQr && pendingStoreOrigin != null && pendingStoreId != null) {
            confirmStoreQr(new StoreQrPayloadParser.ParsedPayload(pendingStoreOrigin, pendingStoreId));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == STORE_QR_SCAN_REQUEST) {
            if (resultCode != RESULT_OK) {
                OneTimeStoreHandoff.clear();
                clearPreStoreQrFields();
                showStatus("Store QR scan cancelled. Existing configuration was not changed.");
                return;
            }
            String rawStore = OneTimeStoreHandoff.consume();
            try {
                StoreQrPayloadParser.ParsedPayload parsed = StoreQrPayloadParser.parse(rawStore);
                rawStore = null;
                confirmStoreQr(parsed);
            } catch (Exception e) {
                storeQrSetupPending = false;
                clearPreStoreQrFields();
                showStatus("Store QR rejected. Scan the BTCPay Store ID QR or canonical HTTPS Bitcoin Rewards display URL.");
            }
            return;
        }
        if (requestCode != LOGIN_CODE_SCAN_REQUEST) return;
        if (resultCode != RESULT_OK) {
            OneTimeLoginHandoff.clear();
            if (storeQrSetupPending) {
                restorePreStoreQrFields();
                clearPendingStoreConfirmation();
                showStatus("BTCPay login QR scan cancelled. The scanned Store QR was not saved.");
                storeQrSetupPending = false;
            }
            return;
        }
        String raw = OneTimeLoginHandoff.consume();
        boolean fromStoreQr = storeQrSetupPending;
        try {
            String configured = fromStoreQr && pendingStoreOrigin != null
                ? pendingStoreOrigin
                : url.getText().toString().trim();
            LoginCodePayloadParser.ParsedPayload parsed = LoginCodePayloadParser.parse(raw, configured);
            raw = null;
            url.setText(parsed.origin);
            String storeId = fromStoreQr && pendingStoreId != null
                ? pendingStoreId
                : store.getText().toString().trim();
            String origin = fromStoreQr
                ? ApiOrigin.requireConfigured(parsed.origin).toString()
                : persistDisplayConfiguration(parsed.origin, storeId);
            PendingOperation.validateText(storeId, 256);
            String displayUrl = buildDisplayUrl(origin, storeId);
            String loginUrl = LoginCodePayloadParser.buildTrustedLoginUrl(parsed, displayUrl);
            if (fromStoreQr) OneTimeDisplayLoginHandoff.publishWithPendingConfiguration(loginUrl, origin, storeId);
            else OneTimeDisplayLoginHandoff.publish(loginUrl);
            clearPendingStoreConfirmation();
            clearPreStoreQrFields();
            Intent main = new Intent(this, MainActivity.class);
            // MainActivity uses the standard launch mode, so CLEAR_TOP replaces any stale expired instance.
            main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(main);
            finish();
        } catch (Exception e) {
            if (fromStoreQr) {
                restorePreStoreQrFields();
                clearPendingStoreConfirmation();
            }
            showStatus(fromStoreQr
                ? "Login QR rejected. The scanned Store QR was not saved; scan a fresh BTCPay login QR and try again."
                : "Login QR rejected. Use a fresh HTTPS BTCPay login QR for the configured server and Store ID.");
            storeQrSetupPending = false;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(STATE_STORE_QR_SETUP_PENDING, storeQrSetupPending);
        state.putBoolean(STATE_CONFIRMING_STORE_QR, confirmingStoreQr);
        state.putString(STATE_PENDING_STORE_ORIGIN, pendingStoreOrigin);
        state.putString(STATE_PENDING_STORE_ID, pendingStoreId);
        state.putString(STATE_PRE_STORE_QR_ORIGIN, preStoreQrOrigin);
        state.putString(STATE_PRE_STORE_QR_STORE_ID, preStoreQrStoreId);
        super.onSaveInstanceState(state);
    }

    private void startStoreQrSetup() {
        storeQrSetupPending = false;
        confirmingStoreQr = false;
        pendingStoreOrigin = null;
        pendingStoreId = null;
        preStoreQrOrigin = url.getText().toString();
        preStoreQrStoreId = store.getText().toString();
        OneTimeStoreHandoff.clear();
        OneTimeDisplayLoginHandoff.clear();
        Intent scan = new Intent(this, LoginCodeScanActivity.class);
        scan.putExtra(LoginCodeScanActivity.EXTRA_STORE_QR_MODE, true);
        startActivityForResult(scan, STORE_QR_SCAN_REQUEST);
    }

    private void confirmStoreQr(StoreQrPayloadParser.ParsedPayload parsed) {
        confirmingStoreQr = true;
        pendingStoreOrigin = parsed.origin;
        pendingStoreId = parsed.storeId;
        boolean hasOrigin = !parsed.origin.isEmpty();
        String confirmation = hasOrigin
            ? "BTCPay server: " + parsed.origin + "\nStore ID: " + parsed.storeId
            : "Store ID: " + parsed.storeId + "\nThe BTCPay server will be read from the login QR.";
        new AlertDialog.Builder(this)
            .setTitle("Confirm Store QR")
            .setMessage(confirmation)
            .setPositiveButton("Scan login QR", (dialog, which) -> {
                confirmingStoreQr = false;
                url.setText(hasOrigin ? parsed.origin : "");
                store.setText(parsed.storeId);
                showStatus(hasOrigin
                    ? "Store QR confirmed. Scan a fresh BTCPay login QR to finish setup."
                    : "Store ID confirmed. Scan a fresh BTCPay login QR to detect the server and finish setup.");
                startLoginScan(true);
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                clearPendingStoreConfirmation();
                clearPreStoreQrFields();
                storeQrSetupPending = false;
                showStatus("Store QR setup cancelled. Existing configuration was not changed.");
            })
            .setOnCancelListener(dialog -> {
                clearPendingStoreConfirmation();
                clearPreStoreQrFields();
                storeQrSetupPending = false;
                showStatus("Store QR setup cancelled. Existing configuration was not changed.");
            })
            .show();
    }

    private void clearPendingStoreConfirmation() {
        confirmingStoreQr = false;
        pendingStoreOrigin = null;
        pendingStoreId = null;
    }

    private void restorePreStoreQrFields() {
        if (preStoreQrOrigin != null) url.setText(preStoreQrOrigin);
        if (preStoreQrStoreId != null) store.setText(preStoreQrStoreId);
        clearPreStoreQrFields();
    }

    private void clearPreStoreQrFields() {
        preStoreQrOrigin = null;
        preStoreQrStoreId = null;
    }

    private void startLoginScan(boolean afterStoreQr) {
        storeQrSetupPending = afterStoreQr;
        OneTimeLoginHandoff.clear();
        OneTimeDisplayLoginHandoff.clear();
        startActivityForResult(new Intent(this, LoginCodeScanActivity.class), LOGIN_CODE_SCAN_REQUEST);
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    private void saveDisplayAndLaunch() {
        try {
            persistDisplayConfiguration(url.getText().toString(), store.getText().toString());
            OneTimeDisplayLoginHandoff.clear();
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
        SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String oldOrigin = p.getString(KEY_BTCPAY_URL, "");
        String oldStore = p.getString(KEY_STORE_ID, "");
        String origin = commitDisplayConfiguration(this, requestedOrigin, requestedStore);
        String storeId = requestedStore == null ? "" : requestedStore.trim();
        if (!origin.equals(oldOrigin) || !storeId.equals(oldStore)) {
            device.setText("");
            token.setText("");
        }
        return origin;
    }

    static String commitDisplayConfiguration(Context context, String requestedOrigin, String requestedStore) {
        String origin = ApiOrigin.requireConfigured(requestedOrigin == null ? "" : requestedOrigin.trim()).toString();
        String storeId = requestedStore == null ? "" : requestedStore.trim();
        PendingOperation.validateText(storeId, 256);
        if (storeId.isEmpty()) throw new IllegalArgumentException();
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String oldOrigin = p.getString(KEY_BTCPAY_URL, "");
        String oldStore = p.getString(KEY_STORE_ID, "");
        SharedPreferences.Editor editor = p.edit().putString(KEY_BTCPAY_URL, origin).putString(KEY_STORE_ID, storeId);
        if (!origin.equals(oldOrigin) || !storeId.equals(oldStore)) {
            editor.remove(KEY_CHECK_IN_DEVICE_ID).remove(KEY_TOKEN_FINGERPRINT).remove(KEY_TOKEN_GENERATION)
                .putBoolean(KEY_ONBOARDED, false).putBoolean(KEY_CREDENTIAL_UPDATE_PENDING, false);
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
