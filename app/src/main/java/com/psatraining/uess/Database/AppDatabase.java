package com.psatraining.uess.Database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.psatraining.uess.Model.User;

import net.sqlcipher.database.SupportFactory;

import java.security.SecureRandom;

@Database(entities = {User.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = createDatabase(context);
                }
            }
        }
        return INSTANCE;
    }

    private static AppDatabase createDatabase(Context context) {
        try {
            // Get master key for encryption
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            // Create or open encrypted preferences
            EncryptedSharedPreferences encryptedPrefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    "uess_db_key",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            // Get or generate secure passphrase
            String passphraseKey = "db_passphrase";
            String passphrase = encryptedPrefs.getString(passphraseKey, null);

            if (passphrase == null) {
                byte[] randomBytes = new byte[32];
                new SecureRandom().nextBytes(randomBytes);
                passphrase = bytesToHex(randomBytes);
                encryptedPrefs.edit().putString(passphraseKey, passphrase).apply();
            }

            // Create SQLCipher factory
            SupportFactory factory = new SupportFactory(passphrase.getBytes());

            // Build encrypted Room database
            return Room.databaseBuilder(context, AppDatabase.class, "uess.db")
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encrypted database", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
