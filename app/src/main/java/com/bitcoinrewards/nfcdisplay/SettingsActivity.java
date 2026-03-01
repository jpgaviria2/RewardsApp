package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import android.content.Intent;

/**
 * Settings/Onboarding screen for configuring the BTCPay rewards display.
 * Shows on first launch or when accessed via the settings gear icon.
 */
public class SettingsActivity extends Activity {
    public static final String PREFS_NAME = "RewardsNfcPrefs";
    public static final String KEY_BTCPAY_URL = "btcpay_url";
    public static final String KEY_STORE_ID = "store_id";
    public static final String KEY_REFRESH_SECONDS = "refresh_seconds";
    public static final String KEY_NFC_ENABLED = "nfc_enabled";
    public static final String KEY_ONBOARDED = "onboarded";

    private EditText inputUrl;
    private EditText inputStoreId;
    private EditText inputRefreshSeconds;
    private Switch switchNfc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        inputUrl = findViewById(R.id.input_btcpay_url);
        inputStoreId = findViewById(R.id.input_store_id);
        inputRefreshSeconds = findViewById(R.id.input_refresh_seconds);
        switchNfc = findViewById(R.id.switch_nfc_enabled);
        Button btnSave = findViewById(R.id.btn_save);

        // Load existing settings
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        inputUrl.setText(prefs.getString(KEY_BTCPAY_URL, ""));
        inputStoreId.setText(prefs.getString(KEY_STORE_ID, ""));
        inputRefreshSeconds.setText(String.valueOf(prefs.getInt(KEY_REFRESH_SECONDS, 10)));
        switchNfc.setChecked(prefs.getBoolean(KEY_NFC_ENABLED, true));

        btnSave.setOnClickListener(v -> saveAndLaunch());
    }

    private void saveAndLaunch() {
        String url = inputUrl.getText().toString().trim();
        String storeId = inputStoreId.getText().toString().trim();
        String refreshStr = inputRefreshSeconds.getText().toString().trim();

        // Validate
        if (url.isEmpty()) {
            inputUrl.setError("BTCPay Server URL is required");
            inputUrl.requestFocus();
            return;
        }
        if (storeId.isEmpty()) {
            inputStoreId.setError("Store ID is required");
            inputStoreId.requestFocus();
            return;
        }

        // Clean URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        int refreshSeconds = 10;
        try {
            refreshSeconds = Integer.parseInt(refreshStr);
            if (refreshSeconds < 3) refreshSeconds = 3;
            if (refreshSeconds > 300) refreshSeconds = 300;
        } catch (NumberFormatException ignored) {}

        // Save
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_BTCPAY_URL, url);
        editor.putString(KEY_STORE_ID, storeId);
        editor.putInt(KEY_REFRESH_SECONDS, refreshSeconds);
        editor.putBoolean(KEY_NFC_ENABLED, switchNfc.isChecked());
        editor.putBoolean(KEY_ONBOARDED, true);
        editor.apply();

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();

        // Launch main display
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Check if onboarding is complete
     */
    public static boolean isOnboarded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ONBOARDED, false);
    }

    /**
     * Get the display URL from settings
     */
    public static String getDisplayUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_BTCPAY_URL, "");
        String storeId = prefs.getString(KEY_STORE_ID, "");
        if (url.isEmpty() || storeId.isEmpty()) return null;
        return url + "/plugins/bitcoin-rewards/" + storeId + "/display";
    }

    /**
     * Check if NFC is enabled in settings
     */
    public static boolean isNfcEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NFC_ENABLED, true);
    }
}
