package com.psatraining.uess.model;

public class UpdateUserRequest {
    private String email;
    private String password;
    private String role;

    public UpdateUserRequest(String email, String password) {
        this.email = email;
        this.password = password;
        this.role = "user"; // Default role for new users
    }

    // Getters and setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
