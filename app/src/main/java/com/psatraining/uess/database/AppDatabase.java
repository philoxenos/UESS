package com.psatraining.uess.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.psatraining.uess.model.User;
import com.psatraining.uess.Utility.SQLCipherUtility;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {User.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase instance;
    
    public abstract UserDao userDao();
    
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            // Get encryption key
            byte[] passphrase = SQLCipherUtility.getOrCreateDatabaseKey(context);
            
            // Use SQLCipher to encrypt the database
            SupportFactory factory = new SupportFactory(passphrase);
            
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "uess_database")
                    .openHelperFactory(factory)
                    .build();
        }
        return instance;
    }
}
