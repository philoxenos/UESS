package com.psatraining.uess.Authentication;

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class GoogleAuthProvider {
    private static final String WEB_CLIENT_ID = "904774983485-set6ptkk5s0gq40a9uec8uuc31d0as2c.apps.googleusercontent.com"; // Replace with your client ID
    private final Context context;
    private final GoogleSignInClient googleSignInClient;

    public GoogleAuthProvider(Context context) {
        this.context = context;

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(context, gso);
    }

    public Intent getSignInIntent() {
        return googleSignInClient.getSignInIntent();
    }

    public Single<String> getIdTokenFromIntent(Intent data) {
        return Single.fromCallable(() -> {
            try {
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data)
                        .getResult(ApiException.class);

                if (account != null && account.getIdToken() != null) {
                    return account.getIdToken();
                } else {
                    throw new Exception("Failed to obtain ID token");
                }
            } catch (ApiException e) {
                throw new Exception("Google sign-in failed: " + e.getStatusCode());
            }
        }).subscribeOn(Schedulers.io());
    }

    public Single<String> getLastSignedInAccountIdToken() {
        return Single.fromCallable(() -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);

            if (account != null && account.getIdToken() != null) {
                return account.getIdToken();
            } else {
                throw new Exception("No authenticated Google account found");
            }
        }).subscribeOn(Schedulers.io());
    }

    public @NonNull Single<Object> silentSignIn() {
        return Single.create(emitter ->
                googleSignInClient.silentSignIn()
                        .addOnSuccessListener(account -> {
                            if (account != null && account.getIdToken() != null) {
                                emitter.onSuccess(account.getIdToken());
                            } else {
                                emitter.onError(new Exception("Failed to obtain ID token"));
                            }
                        })
                        .addOnFailureListener(emitter::onError)
        ).subscribeOn(Schedulers.io());
    }

    public void signOut() {
        googleSignInClient.signOut();
    }
}