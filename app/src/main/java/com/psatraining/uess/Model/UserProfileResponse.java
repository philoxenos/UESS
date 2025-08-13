package com.psatraining.uess.Model;

import com.google.gson.annotations.SerializedName;

public class UserProfileResponse {
    @SerializedName("user")
    private UserProfile user;

    public UserProfile getUser() {
        return user;
    }
}
