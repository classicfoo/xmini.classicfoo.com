package com.classicfoo.xmini;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores OAuth tokens encrypted with a key that never leaves Android Keystore. */
final class TokenVault {
    private static final String PREFS = "oauth_vault";
    private static final String ALIAS = "com.classicfoo.xmini.oauth";
    private final SharedPreferences preferences;

    TokenVault(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized Bundle load() throws Exception {
        String encryptedAccess = preferences.getString("access", null);
        if (encryptedAccess == null) return null;
        String refresh = preferences.getString("refresh", null);
        return new Bundle(decrypt(encryptedAccess), refresh == null ? null : decrypt(refresh),
                preferences.getLong("expires_at", 0L));
    }

    synchronized void save(String accessToken, String refreshToken, long expiresAtMillis) throws Exception {
        SharedPreferences.Editor editor = preferences.edit()
                .putString("access", encrypt(accessToken))
                .putLong("expires_at", expiresAtMillis);
        if (refreshToken == null || refreshToken.isEmpty()) {
            editor.remove("refresh");
        } else {
            editor.putString("refresh", encrypt(refreshToken));
        }
        editor.apply();
    }

    synchronized void clear() {
        preferences.edit().clear().apply();
    }

    private SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private String encrypt(String clearText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(clearText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(cipher.getIV());
        output.write(encrypted);
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private String decrypt(String stored) throws Exception {
        byte[] bytes = Base64.decode(stored, Base64.NO_WRAP);
        if (bytes.length < 13) throw new IllegalStateException("Stored token is incomplete");
        byte[] iv = Arrays.copyOfRange(bytes, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(bytes, 12, bytes.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    }

    static final class Bundle {
        final String accessToken;
        final String refreshToken;
        final long expiresAtMillis;

        Bundle(String accessToken, String refreshToken, long expiresAtMillis) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
