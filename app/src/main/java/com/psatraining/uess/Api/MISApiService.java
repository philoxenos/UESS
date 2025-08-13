package com.psatraining.uess.Api;

import com.psatraining.uess.Model.AuthResponse;
import com.psatraining.uess.Model.UserProfileResponse;

import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface MISApiService {
    @POST("auth/google")
    Single<AuthResponse> googleSignIn(@Body Map<String, String> request);

    @POST("auth/refresh")
    Single<AuthResponse> refreshToken(@Body Map<String, String> request);

    @POST("auth/forgot-password")
    Single<Void> forgotPassword(@Body Map<String, String> request);

    @GET("me")
    Single<UserProfileResponse> getProfile(@Header("Authorization") String token);
}
