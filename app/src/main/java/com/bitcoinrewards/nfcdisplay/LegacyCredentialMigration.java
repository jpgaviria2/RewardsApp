package com.bitcoinrewards.nfcdisplay;

/** Crash-safe ordered migration for pre-2.13 display settings and legacy secrets. */
final class LegacyCredentialMigration {
    static final String COMPLETE = "legacy_display_migration_complete_v3";
    static final String[] SECRET_KEYS = {"email", "password", "api_key", "auth_method", "session_cookies"};

    interface Preferences {
        String getString(String key, String fallback);
        boolean getBoolean(String key, boolean fallback);
        Editor edit();
    }

    interface Editor {
        Editor putString(String key, String value);
        Editor putBoolean(String key, boolean value);
        Editor remove(String key);
        boolean commit();
    }

    static boolean run(Preferences current, Preferences legacy) {
        if (current.getBoolean(COMPLETE, false)) return true;

        String origin = validOriginOrEmpty(current.getString(SettingsActivity.KEY_BTCPAY_URL, ""));
        String store = validStoreOrEmpty(current.getString(SettingsActivity.KEY_STORE_ID, ""));
        if (origin.isEmpty()) origin = validOriginOrEmpty(legacy.getString("btcpay_url", ""));
        if (store.isEmpty()) store = validStoreOrEmpty(legacy.getString("store_id", ""));

        Editor currentEdit = current.edit();
        if (!origin.isEmpty()) currentEdit.putString(SettingsActivity.KEY_BTCPAY_URL, origin);
        if (!store.isEmpty()) currentEdit.putString(SettingsActivity.KEY_STORE_ID, store);
        removeSecrets(currentEdit);
        if (!currentEdit.commit()) return false;

        Editor legacyEdit = legacy.edit();
        removeSecrets(legacyEdit);
        if (!legacyEdit.commit()) return false;

        return current.edit().putBoolean(COMPLETE, true).commit();
    }

    private static void removeSecrets(Editor editor) {
        for (String key : SECRET_KEYS) editor.remove(key);
    }

    private static String validOriginOrEmpty(String value) {
        try { return ApiOrigin.requireConfigured(value == null ? "" : value.trim()).toString(); }
        catch (Exception e) { return ""; }
    }

    private static String validStoreOrEmpty(String value) {
        try {
            String store = value == null ? "" : value.trim();
            PendingOperation.validateText(store, 256);
            return store;
        } catch (Exception e) { return ""; }
    }

    private LegacyCredentialMigration() { }
}
