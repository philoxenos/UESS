package com.psatraining.uess.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.psatraining.uess.model.User;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

@Dao
public interface UserDao {
    
    @Insert
    Completable insert(User user);
    
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    Maybe<User> getUserByEmail(String email);
    
    @Query("UPDATE users SET hashedPassword = :newHashedPassword WHERE email = :email")
    Completable updatePassword(String email, String newHashedPassword);
}
