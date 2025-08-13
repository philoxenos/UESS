package com.psatraining.uess.Api;

import android.content.Context;

import com.psatraining.uess.Security.TokenManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final TokenManager tokenManager;

    public AuthInterceptor(Context context) {
        this.tokenManager = new TokenManager(context);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // If no token is available, proceed with the original request
        if (!tokenManager.hasValidAccessToken() && !tokenManager.hasRefreshToken()) {
            return chain.proceed(originalRequest);
        }

        // Get access token (or refresh if needed)
        String accessToken = null;
        if (tokenManager.hasValidAccessToken()) {
            accessToken = tokenManager.getAccessToken();
        } else if (tokenManager.hasRefreshToken()) {
            // This is a simplified approach - in real apps you might want to use
            // a more robust strategy to avoid blocking the main thread
            try {
                // Try to refresh token (blocking call for simplicity)
                // In production, use a proper async approach
                accessToken = tokenManager.getAccessToken();
            } catch (Exception e) {
                // If refresh fails, proceed with original request
                return chain.proceed(originalRequest);
            }
        }

        // Add the Authorization header with the access token
        if (accessToken != null) {
            Request authorizedRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + accessToken)
                    .build();
            return chain.proceed(authorizedRequest);
        }

        // If we couldn't get a token, proceed with the original request
        return chain.proceed(originalRequest);
    }
}
