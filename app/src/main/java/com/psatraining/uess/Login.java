package com.psatraining.uess;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.psatraining.uess.Authentication.AuthManager;
import com.psatraining.uess.Authentication.GoogleAuthProvider;
import com.psatraining.uess.Model.UserProfile;
import com.psatraining.uess.Utility.QRUtility;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;


public class Login extends AppCompatActivity {

    private MaterialButton btnGoogleSignIn;
    private TextView txtForgotPassword;
    //private View progressOverlay;
    private GoogleAuthProvider googleAuthProvider;
    private AuthManager authManager;
    private CompositeDisposable disposables = new CompositeDisposable();

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getData() != null) {
                    handleGoogleSignInResult(result.getData());
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        ImageView qrImageView = findViewById(R.id.qrcode);
        QRUtility.showDeviceQRCode(this, qrImageView);

        // Initialize UI components
        btnGoogleSignIn = findViewById(R.id.button);
        txtForgotPassword = findViewById(R.id.textView5);
        //progressOverlay = findViewById(R.id.progress_overlay);

        // Initialize auth components
        try {
            googleAuthProvider = new GoogleAuthProvider(this);
            authManager = new AuthManager(getApplicationContext());

            // Setup click listeners
            btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
            txtForgotPassword.setOnClickListener(v -> navigateToForgotPassword());

            // Check for existing session
            checkAuthentication();

        } catch (Exception e) {
            Toast.makeText(this, "Initialization error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAuthentication() {
//        showLoading(true);

        disposables.add(authManager.isAuthenticated()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isAuthenticated -> {
                            if (isAuthenticated) {
                                navigateToMainActivity();
                            } else {
//                                showLoading(false);
                            }
                        },
                        error -> {
//                            showLoading(false);
                            Toast.makeText(Login.this, "Session verification failed", Toast.LENGTH_SHORT).show();
                        }
                ));
    }

    private void startGoogleSignIn() {
        googleSignInLauncher.launch(googleAuthProvider.getSignInIntent());
    }

    private void handleGoogleSignInResult(Intent data) {
//        showLoading(true);

        disposables.add(googleAuthProvider.getIdTokenFromIntent(data)
                .flatMap(idToken -> authManager.authenticateWithGoogle(idToken))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::onAuthenticationSuccess,
                        this::onAuthenticationError
                ));
    }

    private void onAuthenticationSuccess(UserProfile userProfile) {
//        showLoading(false);
        navigateToMainActivity();
    }

    private void onAuthenticationError(Throwable error) {
//        showLoading(false);

        if (error.getMessage() != null && error.getMessage().contains("404")) {
            // User not found, needs registration
            Toast.makeText(this, "Account not found. Registration required.", Toast.LENGTH_LONG).show();
            openRegistrationInBrowser();
        } else {
            Toast.makeText(this, "Authentication failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openRegistrationInBrowser() {
        disposables.add(googleAuthProvider.getLastSignedInAccountIdToken()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        idToken -> {
                            Uri registrationUri = Uri.parse("http://10.0.2.2:5173/register")
                                    .buildUpon()
                                    .appendQueryParameter("token", idToken)
                                    .appendQueryParameter("mobile", "true")
                                    .build();

                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, registrationUri);
                            startActivity(browserIntent);
                        },
                        error -> Toast.makeText(Login.this, "Failed to start registration", Toast.LENGTH_SHORT).show()
                ));
    }

    private void navigateToForgotPassword() {
        Intent intent = new Intent(this, ForgotPassword.class);
        startActivity(intent);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, Homepage.class);
        startActivity(intent);
        finish();
    }

//    private void showLoading(boolean show) {
//        progressOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
//        btnGoogleSignIn.setEnabled(!show);
//        txtForgotPassword.setEnabled(!show);
//    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAuthentication();
//        if (progressOverlay.getVisibility() != View.VISIBLE) {
//
//        }
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}