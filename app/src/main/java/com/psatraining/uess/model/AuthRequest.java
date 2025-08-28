package com.psatraining.uess.model;

public class AuthRequest {
    private String email;
    private String name;
    private String surname;
    private String createdAt;

    public AuthRequest(String email, String name, String surname, String createdAt) {
        this.email = email;
        this.name = name;
        this.surname = surname;
        this.createdAt = createdAt;
    }

    // Only getEmail() is actually used in the codebase
    public String getEmail() {
        return email;
    }

    // These getters are kept for JSON serialization by Retrofit
    public String getName() {
        return name;
    }

    public String getSurname() {
        return surname;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
