package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class LegacyCredentialMigrationTest {
    @Test public void directPre213UpgradeMigratesDisplayAndRemovesSecretsInOrder() {
        FakePreferences current = new FakePreferences();
        FakePreferences legacy = legacyInstall();

        assertTrue(LegacyCredentialMigration.run(current, legacy));

        assertEquals("https://btcpay.example", current.string(SettingsActivity.KEY_BTCPAY_URL));
        assertEquals("store-one", current.string(SettingsActivity.KEY_STORE_ID));
        assertSecretsRemoved(current);
        assertSecretsRemoved(legacy);
        assertTrue(current.bool(LegacyCredentialMigration.COMPLETE));
        assertEquals(2, current.commits);
        assertEquals(1, legacy.commits);
    }

    @Test public void interruptedMigrationDoesNotMarkCompleteAndRetryFinishes() {
        FakePreferences current = new FakePreferences();
        FakePreferences legacy = legacyInstall();
        legacy.failNextCommit = true;

        assertFalse(LegacyCredentialMigration.run(current, legacy));
        assertEquals("https://btcpay.example", current.string(SettingsActivity.KEY_BTCPAY_URL));
        assertSecretsRemoved(current);
        assertFalse(current.bool(LegacyCredentialMigration.COMPLETE));
        assertFalse(legacy.string("password").isEmpty());

        assertTrue(LegacyCredentialMigration.run(current, legacy));
        assertSecretsRemoved(legacy);
        assertTrue(current.bool(LegacyCredentialMigration.COMPLETE));
    }

    @Test public void completedMigrationIsIdempotent() {
        FakePreferences current = new FakePreferences();
        FakePreferences legacy = legacyInstall();
        assertTrue(LegacyCredentialMigration.run(current, legacy));
        int currentCommits = current.commits;
        int legacyCommits = legacy.commits;

        assertTrue(LegacyCredentialMigration.run(current, legacy));

        assertEquals(currentCommits, current.commits);
        assertEquals(legacyCommits, legacy.commits);
    }

    @Test public void version2130StateKeepsCurrentDisplayAndPreservesWebViewCookies() {
        FakePreferences current = new FakePreferences();
        current.values.put(SettingsActivity.KEY_BTCPAY_URL, "https://current.example");
        current.values.put(SettingsActivity.KEY_STORE_ID, "current-store");
        current.values.put("session_cookies", "obsolete-preference-copy");
        FakePreferences legacy = legacyInstall();

        assertTrue(LegacyCredentialMigration.run(current, legacy));

        assertEquals("https://current.example", current.string(SettingsActivity.KEY_BTCPAY_URL));
        assertEquals("current-store", current.string(SettingsActivity.KEY_STORE_ID));
        assertSecretsRemoved(current);
        assertSecretsRemoved(legacy);
    }

    @Test public void completionCommitFailureRemainsRetryable() {
        FakePreferences current = new FakePreferences();
        FakePreferences legacy = legacyInstall();
        current.failCommitNumber = 2;

        assertFalse(LegacyCredentialMigration.run(current, legacy));
        assertFalse(current.bool(LegacyCredentialMigration.COMPLETE));
        assertSecretsRemoved(current);
        assertSecretsRemoved(legacy);

        current.failCommitNumber = -1;
        assertTrue(LegacyCredentialMigration.run(current, legacy));
        assertTrue(current.bool(LegacyCredentialMigration.COMPLETE));
    }

    private static FakePreferences legacyInstall() {
        FakePreferences legacy = new FakePreferences();
        legacy.values.put("btcpay_url", "https://btcpay.example/");
        legacy.values.put("store_id", "store-one");
        for (String key : LegacyCredentialMigration.SECRET_KEYS) legacy.values.put(key, "secret-" + key);
        return legacy;
    }

    private static void assertSecretsRemoved(FakePreferences preferences) {
        for (String key : LegacyCredentialMigration.SECRET_KEYS) assertFalse(preferences.values.containsKey(key));
    }

    private static final class FakePreferences implements LegacyCredentialMigration.Preferences {
        final Map<String, Object> values = new HashMap<>();
        int commits;
        int failCommitNumber = -1;
        boolean failNextCommit;

        String string(String key) { return getString(key, ""); }
        boolean bool(String key) { return getBoolean(key, false); }

        @Override public String getString(String key, String fallback) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : fallback;
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : fallback;
        }

        @Override public LegacyCredentialMigration.Editor edit() {
            return new FakeEditor(this);
        }
    }

    private static final class FakeEditor implements LegacyCredentialMigration.Editor {
        private final FakePreferences preferences;
        private final Map<String, Object> writes = new HashMap<>();
        private final Map<String, Boolean> removals = new HashMap<>();

        FakeEditor(FakePreferences preferences) { this.preferences = preferences; }

        @Override public LegacyCredentialMigration.Editor putString(String key, String value) {
            writes.put(key, value);
            removals.remove(key);
            return this;
        }

        @Override public LegacyCredentialMigration.Editor putBoolean(String key, boolean value) {
            writes.put(key, value);
            removals.remove(key);
            return this;
        }

        @Override public LegacyCredentialMigration.Editor remove(String key) {
            removals.put(key, true);
            writes.remove(key);
            return this;
        }

        @Override public boolean commit() {
            preferences.commits++;
            if (preferences.failNextCommit || preferences.commits == preferences.failCommitNumber) {
                preferences.failNextCommit = false;
                return false;
            }
            for (String key : removals.keySet()) preferences.values.remove(key);
            preferences.values.putAll(writes);
            return true;
        }
    }
}
