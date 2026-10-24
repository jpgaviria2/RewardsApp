package com.bitcoinrewards.nfcdisplay;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class TokenSecurityTest {
    @Test public void generationChangesOnlyWhenTokenChangesAndFingerprintIsStable() {
        TokenIdentity first=TokenIdentity.update(null,0,"secret-one");
        TokenIdentity same=TokenIdentity.update(first.fingerprint,first.generation,"secret-one");
        TokenIdentity changed=TokenIdentity.update(same.fingerprint,same.generation,"secret-two");
        assertEquals(1,first.generation); assertEquals(1,same.generation); assertEquals(2,changed.generation);
        assertNotEquals(first.fingerprint,changed.fingerprint); assertFalse(first.fingerprint.contains("secret"));
    }

    @Test public void settingsContainsNoLegacyPasswordApiKeyOrPlaintextTokenPersistence() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java")), StandardCharsets.UTF_8);
        String layout=new String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_settings.xml")), StandardCharsets.UTF_8);
        assertFalse(source.contains("KEY_PASSWORD")); assertFalse(source.contains("KEY_API_KEY"));
        assertFalse(source.contains("/api/v1/api-keys")); assertFalse(source.contains("Basic "));
        assertFalse(source.contains("putString(KEY_CHECK_IN_TOKEN")); assertFalse(layout.contains("input_password")); assertFalse(layout.contains("input_email"));
        assertTrue(source.contains("SecureTokenStore"));
    }

    @Test public void legacyMigrationKeepsOnlyDisplayConfigurationAndDoesNotDestroyCookies() throws Exception {
        String migration=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/LegacyCredentialMigration.java")),StandardCharsets.UTF_8);
        String purge=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/LegacyCredentialPurge.java")),StandardCharsets.UTF_8);
        String main=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java")),StandardCharsets.UTF_8);
        String settings=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java")),StandardCharsets.UTF_8);
        assertTrue(migration.contains("legacy_display_migration_complete_v3"));
        assertTrue(migration.contains("btcpay_url")); assertTrue(migration.contains("store_id"));
        assertTrue(migration.contains("password")); assertTrue(migration.contains("api_key")); assertTrue(migration.contains("session_cookies"));
        assertTrue(migration.contains("commit()")); assertFalse(migration.contains("apply()"));
        assertFalse(migration.contains("removeAllCookies")); assertFalse(purge.contains("removeAllCookies"));
        assertTrue(main.indexOf("LegacyCredentialPurge.run") < main.indexOf("setContentView"));
        assertTrue(settings.indexOf("LegacyCredentialPurge.run") < settings.indexOf("setContentView"));
        assertTrue(main.contains("displayConfigurationUsable(this)"));
        assertTrue(main.contains("scannerConfigurationUsable(this)"));
    }

    @Test public void oneTimeLoginCodeUsesProcessMemoryAndNeverIntentOrPreferences() throws Exception {
        String main=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/MainActivity.java")),StandardCharsets.UTF_8);
        String settings=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/SettingsActivity.java")),StandardCharsets.UTF_8);
        assertTrue(settings.contains("OneTimeLoginHandoff.publish(loginUrl)"));
        assertTrue(main.contains("OneTimeLoginHandoff.consume()"));
        assertFalse(settings.contains("putExtra(MainActivity"));
        assertFalse(main.contains("getStringExtra"));
        assertTrue(main.contains("isTrustedLoginUrl"));
    }

    @Test public void secureTokenStoreSupportsLegacyAndroidKeystoreOrFailsOnlyOnActualErrors() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/com/bitcoinrewards/nfcdisplay/SecureTokenStore.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("KeyPairGeneratorSpec"));
        assertTrue(source.contains("RSA/ECB/PKCS1Padding"));
        assertTrue(source.contains("AES/CBC/PKCS5Padding"));
        assertTrue(source.contains("HmacSHA256"));
        assertTrue(source.replace(" ", "").contains("Build.VERSION.SDK_INT>=19"));
        String layout=new String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_settings.xml")), StandardCharsets.UTF_8);
        assertFalse(layout.contains("Android 6 or newer"));
        assertTrue(layout.contains("Android 4.4 or newer"));
    }
}
