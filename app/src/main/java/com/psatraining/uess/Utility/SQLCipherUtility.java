package com.psatraining.uess.Utility;

import android.content.Context;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for SQLCipher database encryption
 */
public class SQLCipherUtility {
    
    /**
     * Get the database encryption key
     * Using a fixed key for development to ensure database can be opened consistently
     * @param context Application context
     * @return byte array encryption key
     */
    public static byte[] getOrCreateDatabaseKey(Context context) {
        // For consistency, always use the same passphrase 
        // This ensures we can always open existing databases
        return "G3rmf1ght".getBytes(StandardCharsets.UTF_8);
    }
}
