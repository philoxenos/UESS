package com.psatraining.uess.api;

import com.psatraining.uess.model.AuthRequest;
import com.psatraining.uess.model.AuthResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthService {
    @POST("authenticate")
    Call<AuthResponse> authenticateUser(@Body AuthRequest request);
}
