package com.psatraining.uess.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Model class to represent a user in the application
 */
@Entity(tableName = "users")
public class User {
    
    @PrimaryKey
    @NonNull
    private String userId;
    
    private String name;
    private String surname;
    private String email;
    private String hashedPassword;
    private long createdAt;

    public User() {
    }

    public User(String userId, String name, String surname, String email, String hashedPassword, long createdAt) {
        this.userId = userId;
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
