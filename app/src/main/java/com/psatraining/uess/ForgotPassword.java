package com.psatraining.uess;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.psatraining.uess.Authentication.AuthManager;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ForgotPassword extends AppCompatActivity {

    private TextInputLayout emailInputLayout;
    private TextInputEditText emailEditText;
    private MaterialButton resetButton;
    private LinearProgressIndicator progressIndicator;

    private AuthManager authManager;
    private CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forgot_password_activity);

        // Initialize views
        emailInputLayout = findViewById(R.id.email_input_layout);
        emailEditText = findViewById(R.id.email_edit_text);
        resetButton = findViewById(R.id.reset_button);

        // Initialize auth manager
        authManager = new AuthManager(getApplicationContext());

        // Set up click listener
        resetButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();

            if (email.isEmpty()) {
                emailInputLayout.setError("Email is required");
                return;
            }

            if (!isValidEmail(email)) {
                emailInputLayout.setError("Please enter a valid email");
                return;
            }

            emailInputLayout.setError(null);
            initiatePasswordReset(email);
        });
    }

    private void initiatePasswordReset(String email) {
        showLoading(true);

        disposables.add(authManager.initiatePasswordReset(email)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            showLoading(false);
                            // Show success message
                            Toast.makeText(this, "Password reset link sent to " + email, Toast.LENGTH_LONG).show();
                            // Open browser with reset instructions
                            openResetInstructions(email);
                        },
                        error -> {
                            showLoading(false);
                            // Always show success to prevent email enumeration
                            Toast.makeText(this, "Password reset link sent to " + email, Toast.LENGTH_LONG).show();
                            // Still open browser anyway
                            openResetInstructions(email);
                        }
                ));
    }

    private void openResetInstructions(String email) {
        Uri uri = Uri.parse("http://10.0.2.2:5173/reset-password")
                .buildUpon()
                .appendQueryParameter("email", email)
                .appendQueryParameter("mobile", "true")
                .build();

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(browserIntent);
        finish();
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        emailEditText.setEnabled(!show);
        resetButton.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}
