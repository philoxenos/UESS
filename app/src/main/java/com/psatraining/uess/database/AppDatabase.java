package com.psatraining.uess.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.psatraining.uess.model.User;
import com.psatraining.uess.Utility.CryptoHelper;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {User.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase instance;
    
    public abstract UserDao userDao();
    
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            // Get encryption key
            byte[] passphrase = CryptoHelper.getOrCreateDatabaseKey(context);
            
            // Use SQLCipher to encrypt the database
            SupportFactory factory = new SupportFactory(passphrase);
            
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "uess_database")
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
