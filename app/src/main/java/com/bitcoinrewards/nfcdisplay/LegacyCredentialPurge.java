package com.bitcoinrewards.nfcdisplay;

import android.app.Activity;
import android.content.SharedPreferences;

/** One-time migration of non-secret display configuration from pre-2.13 preferences. */
final class LegacyCredentialPurge {
    private static final String LEGACY_PREFS = "RewardsNfcPrefs";

    static void run(Activity activity, Runnable ready) {
        SharedPreferences current = activity.getSharedPreferences(SettingsActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        SharedPreferences legacy = activity.getSharedPreferences(LEGACY_PREFS, Activity.MODE_PRIVATE);
        if (LegacyCredentialMigration.run(new AndroidPreferences(current), new AndroidPreferences(legacy))) ready.run();
    }

    private static final class AndroidPreferences implements LegacyCredentialMigration.Preferences {
        private final SharedPreferences preferences;

        AndroidPreferences(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override public String getString(String key, String fallback) {
            return preferences.getString(key, fallback);
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            return preferences.getBoolean(key, fallback);
        }

        @Override public LegacyCredentialMigration.Editor edit() {
            return new AndroidEditor(preferences.edit());
        }
    }

    private static final class AndroidEditor implements LegacyCredentialMigration.Editor {
        private final SharedPreferences.Editor editor;

        AndroidEditor(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        @Override public LegacyCredentialMigration.Editor putString(String key, String value) {
            editor.putString(key, value);
            return this;
        }

        @Override public LegacyCredentialMigration.Editor putBoolean(String key, boolean value) {
            editor.putBoolean(key, value);
            return this;
        }

        @Override public LegacyCredentialMigration.Editor remove(String key) {
            editor.remove(key);
            return this;
        }

        @Override public boolean commit() {
            return editor.commit();
        }
    }

    private LegacyCredentialPurge() { }
}
