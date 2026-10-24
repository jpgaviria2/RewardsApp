package com.bitcoinrewards.nfcdisplay;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

final class SecureTokenStore {
    private static final String AES_ALIAS = "rewards-scanner-token-v1";
    private static final String RSA_ALIAS = "rewards-scanner-token-rsa-v1";
    private static final String MODE = "scanner_token_mode";
    private static final String BLOB = "scanner_token_ciphertext";
    private static final String IV = "scanner_token_iv";
    private static final String WRAPPED = "scanner_token_wrapped_key";
    private static final String MAC = "scanner_token_mac";
    private static final String MODE_GCM = "gcm";
    private static final String MODE_LEGACY = "rsa-cbc-hmac";
    private final Context context;
    private final SharedPreferences prefs;

    SecureTokenStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    boolean available() { return Build.VERSION.SDK_INT >= 19; }

    boolean save(String token) {
        if (!available() || token == null || token.isEmpty()) return false;
        try {
            boolean committed = Build.VERSION.SDK_INT >= 23 ? saveGcm(token) : saveLegacy(token);
            return committed && token.equals(load());
        } catch (Exception e) {
            return false;
        }
    }

    String load() {
        if (!available()) return "";
        try {
            String mode = prefs.getString(MODE, "");
            if (MODE_GCM.equals(mode) && Build.VERSION.SDK_INT >= 23) return loadGcm();
            if (MODE_LEGACY.equals(mode)) return loadLegacy();
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @TargetApi(23)
    private boolean saveGcm(String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey());
        byte[] encrypted = cipher.doFinal(token.getBytes("UTF-8"));
        return prefs.edit()
            .putString(MODE, MODE_GCM)
            .putString(BLOB, encode(encrypted))
            .putString(IV, encode(cipher.getIV()))
            .remove(WRAPPED).remove(MAC)
            .commit();
    }

    @TargetApi(23)
    private String loadGcm() throws Exception {
        byte[] encrypted = decodeRequired(BLOB);
        byte[] iv = decodeRequired(IV);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), "UTF-8");
    }

    @TargetApi(23)
    private SecretKey aesKey() throws Exception {
        KeyStore store = androidKeyStore();
        if (!store.containsAlias(AES_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(AES_ALIAS, null)).getSecretKey();
    }

    @TargetApi(19)
    private boolean saveLegacy(String token) throws Exception {
        ensureRsaKey();
        byte[] material = new byte[64];
        new SecureRandom().nextBytes(material);
        byte[] encryptionKey = Arrays.copyOfRange(material, 0, 32);
        byte[] macKey = Arrays.copyOfRange(material, 32, 64);

        Cipher payloadCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        payloadCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"));
        byte[] iv = payloadCipher.getIV();
        byte[] encrypted = payloadCipher.doFinal(token.getBytes("UTF-8"));
        byte[] authentication = hmac(macKey, iv, encrypted);

        Certificate certificate = androidKeyStore().getCertificate(RSA_ALIAS);
        if (certificate == null) throw new IllegalStateException();
        Cipher wrapper = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        wrapper.init(Cipher.ENCRYPT_MODE, certificate.getPublicKey());
        byte[] wrapped = wrapper.doFinal(material);
        Arrays.fill(material, (byte) 0);
        Arrays.fill(encryptionKey, (byte) 0);
        Arrays.fill(macKey, (byte) 0);

        return prefs.edit()
            .putString(MODE, MODE_LEGACY)
            .putString(BLOB, encode(encrypted))
            .putString(IV, encode(iv))
            .putString(WRAPPED, encode(wrapped))
            .putString(MAC, encode(authentication))
            .commit();
    }

    @TargetApi(19)
    private String loadLegacy() throws Exception {
        byte[] encrypted = decodeRequired(BLOB);
        byte[] iv = decodeRequired(IV);
        byte[] wrapped = decodeRequired(WRAPPED);
        byte[] expectedMac = decodeRequired(MAC);
        KeyStore store = androidKeyStore();
        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry(RSA_ALIAS, null);
        if (entry == null) throw new IllegalStateException();

        Cipher wrapper = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        wrapper.init(Cipher.DECRYPT_MODE, entry.getPrivateKey());
        byte[] material = wrapper.doFinal(wrapped);
        if (material.length != 64) throw new IllegalStateException();
        byte[] encryptionKey = Arrays.copyOfRange(material, 0, 32);
        byte[] macKey = Arrays.copyOfRange(material, 32, 64);
        byte[] actualMac = hmac(macKey, iv, encrypted);
        if (!MessageDigest.isEqual(expectedMac, actualMac)) throw new IllegalStateException();

        Cipher payloadCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        payloadCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new IvParameterSpec(iv));
        byte[] clear = payloadCipher.doFinal(encrypted);
        Arrays.fill(material, (byte) 0);
        Arrays.fill(encryptionKey, (byte) 0);
        Arrays.fill(macKey, (byte) 0);
        return new String(clear, "UTF-8");
    }

    @TargetApi(19)
    private void ensureRsaKey() throws Exception {
        KeyStore store = androidKeyStore();
        if (store.containsAlias(RSA_ALIAS)) return;
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 25);
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
            .setAlias(RSA_ALIAS)
            .setSubject(new X500Principal("CN=Rewards Scanner Token"))
            .setSerialNumber(java.math.BigInteger.ONE)
            .setStartDate(start.getTime())
            .setEndDate(end.getTime())
            .setKeySize(2048)
            .build();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    private static byte[] hmac(byte[] key, byte[] iv, byte[] encrypted) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(iv);
        return mac.doFinal(encrypted);
    }

    private static KeyStore androidKeyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private byte[] decodeRequired(String key) {
        String value = prefs.getString(key, "");
        if (value.isEmpty()) throw new IllegalStateException();
        return Base64.decode(value, Base64.NO_WRAP);
    }

    private static String encode(byte[] value) { return Base64.encodeToString(value, Base64.NO_WRAP); }
}
