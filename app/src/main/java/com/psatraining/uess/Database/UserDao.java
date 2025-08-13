package com.psatraining.uess.Database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.psatraining.uess.Model.User;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Query("SELECT * FROM users LIMIT 1")
    User getUser();

    @Query("DELETE FROM users")
    void deleteAllUsers();

    // RxJava versions for async operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertUserRx(User user);

    @Query("SELECT * FROM users LIMIT 1")
    Maybe<User> getUserRx();
}
