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

    // Getters and setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
