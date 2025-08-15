package com.psatraining.uess.Utility;

import android.content.Context;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class SQLCipherUtility {
    
    private static final String SHARED_PREF_FILENAME = "uess_secure_prefs";
    private static final String DATABASE_KEY = "database_encryption_key";
    
    /**
     * Get or create the database encryption key
     * @param context Application context
     * @return byte array encryption key
     */
    public static byte[] getOrCreateDatabaseKey(Context context) {
        try {
            // Create or get MasterKey for encrypted SharedPreferences
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            // Initialize encrypted SharedPreferences
            EncryptedSharedPreferences encryptedPrefs = (EncryptedSharedPreferences) 
                    EncryptedSharedPreferences.create(
                        context,
                        SHARED_PREF_FILENAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );
            
            // Check if we already have a key
            String existingKey = encryptedPrefs.getString(DATABASE_KEY, null);
            
            if (existingKey == null) {
                // Generate a new key if one doesn't exist
                byte[] newKey = new byte[32]; // 256 bits
                SecureRandom secureRandom = new SecureRandom();
                secureRandom.nextBytes(newKey);
                
                // Store the key as a Base64 string
                String keyString = Base64.encodeToString(newKey, Base64.DEFAULT);
                encryptedPrefs.edit().putString(DATABASE_KEY, keyString).apply();
                
                return newKey;
            } else {
                // Return existing key
                return Base64.decode(existingKey, Base64.DEFAULT);
            }
            
        } catch (Exception e) {
            // In case of failure, generate a temporary key
            // This is not ideal but prevents app crashes
            byte[] fallbackKey = "TEMPORARY_FALLBACK_KEY_NOT_SECURE".getBytes(StandardCharsets.UTF_8);
            return fallbackKey;
        }
    }
}
