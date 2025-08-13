package com.psatraining.uess.Authentication;

import android.content.Context;

import com.psatraining.uess.Api.ApiClient;
import com.psatraining.uess.Api.MISApiService;
import com.psatraining.uess.Database.AppDatabase;
import com.psatraining.uess.Database.UserDao;
import com.psatraining.uess.Model.AuthResponse;
import com.psatraining.uess.Model.User;
import com.psatraining.uess.Model.UserProfile;
import com.psatraining.uess.Security.TokenManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AuthManager {
    private final MISApiService apiService;
    private final TokenManager tokenManager;
    private final UserDao userDao;

    public AuthManager(Context context) {
        apiService = ApiClient.getInstance();
        tokenManager = new TokenManager(context);
        userDao = AppDatabase.getInstance(context).userDao();
    }

    public Single<Boolean> isAuthenticated() {
        return Single.fromCallable(() -> {
            // Check if we have a valid access token
            if (tokenManager.hasValidAccessToken()) {
                return true;
            }

            // Try to refresh the token if we have a refresh token
            if (tokenManager.hasRefreshToken()) {
                return refreshToken()
                        .onErrorReturnItem(false)
                        .blockingGet();
            }

            return false;
        }).subscribeOn(Schedulers.io());
    }

    public Single<Boolean> refreshToken() {
        return Single.fromCallable(() -> {
            String refreshToken = tokenManager.getRefreshToken();
            if (refreshToken == null) {
                return false;
            }

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("refresh_token", refreshToken);

            AuthResponse response = apiService.refreshToken(requestBody).blockingGet();
            tokenManager.saveTokens(
                    response.getAccessToken(),
                    response.getRefreshToken(),
                    response.getExpiresIn()
            );

            return true;
        }).subscribeOn(Schedulers.io());
    }

    public Single<UserProfile> authenticateWithGoogle(String idToken) {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("id_token", idToken);

        return apiService.googleSignIn(requestBody)
                .flatMap(response -> {
                    // Save tokens
                    tokenManager.saveTokens(
                            response.getAccessToken(),
                            response.getRefreshToken(),
                            response.getExpiresIn()
                    );

                    // Save user to database
                    return saveUserToDatabase(response.getUser())
                            .andThen(Single.just(response.getUser()));
                })
                .subscribeOn(Schedulers.io());
    }

    private Completable saveUserToDatabase(UserProfile profile) {
        return Completable.fromAction(() -> {
            User user = new User();
            user.setId(profile.getId());
            user.setEmail(profile.getEmail());
            user.setName(profile.getName());
            user.setRoles(String.join(",", profile.getRoles()));

            userDao.insertUser(user);
        }).subscribeOn(Schedulers.io());
    }

    public Single<UserProfile> getCurrentUser() {
        return userDao.getUserRx()
                .map(this::mapUserToUserProfile)
                .toSingle()
                .subscribeOn(Schedulers.io());
    }

    private UserProfile mapUserToUserProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setEmail(user.getEmail());
        profile.setName(user.getName());

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            profile.setRoles(Arrays.asList(user.getRoles().split(",")));
        }

        return profile;
    }

    public Completable initiatePasswordReset(String email) {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("email", email);

        return apiService.forgotPassword(requestBody)
                .ignoreElement()
                .subscribeOn(Schedulers.io());
    }

    public Completable logout() {
        return Completable.fromAction(() -> {
            tokenManager.clearTokens();
            userDao.deleteAllUsers();
        }).subscribeOn(Schedulers.io());
    }

    public Single<String> getAccessToken() {
        return Single.fromCallable(() -> {
            if (tokenManager.hasValidAccessToken()) {
                return tokenManager.getAccessToken();
            }

            if (tokenManager.hasRefreshToken()) {
                boolean refreshed = refreshToken().blockingGet();
                if (refreshed) {
                    return tokenManager.getAccessToken();
                }
            }

            throw new IllegalStateException("No valid access token available");
        }).subscribeOn(Schedulers.io());
    }
}