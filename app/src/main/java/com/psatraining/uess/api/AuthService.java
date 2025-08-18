package com.psatraining.uess.api;

import com.psatraining.uess.model.AuthRequest;
import com.psatraining.uess.model.AuthResponse;
import com.psatraining.uess.model.UpdateUserRequest;
import com.psatraining.uess.model.UpdateUserResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface AuthService {
    @POST("authenticate")
    Call<AuthResponse> authenticateUser(@Body AuthRequest request);
    
    @PUT("update")
    Call<UpdateUserResponse> updateUser(@Body UpdateUserRequest request);
}
