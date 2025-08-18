package com.psatraining.uess.model;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    private String status;
    private boolean exists;
    private User user;

    public String getStatus() {
        return status;
    }

    public boolean isExists() {
        return exists;
    }

    public User getUser() {
        return user;
    }

    public static class User {
        private String email;
        private String name;
        private String surname;
        private String createdAt;
        private String role;
        private String password;

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }

        public String getSurname() {
            return surname;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getRole() {
            return role;
        }
        
        public String getPassword() {
            return password;
        }
    }
}
