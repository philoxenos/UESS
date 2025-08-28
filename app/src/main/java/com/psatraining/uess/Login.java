package com.psatraining.uess;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.textfield.TextInputEditText;
import com.psatraining.uess.Utility.PasswordHasherUtility;
import com.psatraining.uess.api.AuthService;
import com.psatraining.uess.api.RetrofitClient;
import com.psatraining.uess.database.AppDatabase;
import com.psatraining.uess.model.AuthRequest;
import com.psatraining.uess.model.AuthResponse;
import com.psatraining.uess.Utility.QRUtility;
import com.psatraining.uess.model.User;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Login extends AppCompatActivity {
    private static final String TAG = "Login";
    private static final int REQ_ONE_TAP = 100;
    private static final int REQ_PASSWORD_RESET = 101;
    
    private SignInClient signInClient;
    private BeginSignInRequest beginSignInRequest;
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        initializeViews();
        initializeGoogleSignIn();
        database = AppDatabase.getInstance(getApplicationContext());
    }

    private void initializeViews() {
        ImageView qrCodeIcon = findViewById(R.id.qr_code_icon);
        EditText emailInput = findViewById(R.id.email_edit_text);
        EditText passwordInput = findViewById(R.id.password_edit_text);
        Button signInButton = findViewById(R.id.submit_button);
        Button googleSignInButton = findViewById(R.id.button);
        TextView forgotPasswordLink = findViewById(R.id.textView5);

        qrCodeIcon.setOnClickListener(v -> showQrCodeDialog());
        signInButton.setOnClickListener(v -> handleEmailPasswordLogin(emailInput, passwordInput));
        googleSignInButton.setOnClickListener(this::buttonGoogleSignIn);
        forgotPasswordLink.setOnClickListener(v -> initiatePasswordReset());
    }

    private void initializeGoogleSignIn() {
        signInClient = Identity.getSignInClient(this);
        beginSignInRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                        .setSupported(true)
                        .build())
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.google_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                .setAutoSelectEnabled(true)
                .build();
    }

    private void handleEmailPasswordLogin(EditText emailInput, EditText passwordInput) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        if (validateInputs(email, password, emailInput, passwordInput)) {
            String hashedPassword = PasswordHasherUtility.hashPassword(password);
            validateUserCredentials(email, hashedPassword);
        }
    }

    private boolean validateInputs(String email, String password, EditText emailInput, EditText passwordInput) {
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return false;
        }
        return true;
    }

    private void showQrCodeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.qr_dialog, null);
        builder.setView(dialogView);

        ImageView qrImageView = dialogView.findViewById(R.id.dialog_qrcode);
        QRUtility.showDeviceQRCode(this, qrImageView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        dialog.show();
    }

    private void buttonGoogleSignIn(View view) {
        signInClient.beginSignIn(beginSignInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        startIntentSenderForResult(result.getPendingIntent().getIntentSender(), 
                                REQ_ONE_TAP, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(TAG, "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.d(TAG, "Google Sign-In failed: " + e.getLocalizedMessage());
                    showToast("Sign-In Error: " + e.getLocalizedMessage());
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQ_ONE_TAP) {
            handleGoogleSignInResult(data);
        } else if (requestCode == REQ_PASSWORD_RESET) {
            handlePasswordResetResult(data);
        }
    }

    private void handleGoogleSignInResult(@Nullable Intent data) {
        try {
            SignInCredential credential = signInClient.getSignInCredentialFromIntent(data);
            String idToken = credential.getGoogleIdToken();
            String email = credential.getId();
            String givenName = credential.getGivenName();
            String familyName = credential.getFamilyName();

            if (idToken != null) {
                Log.d(TAG, "Got ID token and email: " + email);
                AuthRequest authRequest = createAuthRequest(email,
                        givenName != null ? givenName : "",
                        familyName != null ? familyName : "");
                authenticateUser(authRequest);
            }
        } catch (ApiException e) {
            Log.e(TAG, "Error getting sign-in credentials: " + e.getMessage(), e);
            showToast("Authentication failed: " + e.getLocalizedMessage());
        }
    }

    private void handlePasswordResetResult(@Nullable Intent data) {
        try {
            SignInCredential credential = signInClient.getSignInCredentialFromIntent(data);
            String email = credential.getId();

            if (email != null) {
                Log.d(TAG, "Got email for password reset: " + email);
                checkEmailForPasswordReset(email);
            }
        } catch (ApiException e) {
            Log.e(TAG, "Error getting email for password reset: " + e.getMessage(), e);
            showToast("Failed to get email: " + e.getLocalizedMessage());
        }
    }
    
    private void authenticateUser(AuthRequest authRequest) {
        authenticateWithBackend(authRequest);
    }

    private void authenticateWithBackend(AuthRequest authRequest) {
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        Call<AuthResponse> call = authService.authenticateUser(authRequest);
        
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                handleAuthResponse(response, authRequest.getEmail());
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e(TAG, "Network error", t);
                showErrorDialog("Internet Connection Required", 
                    "You need an internet connection to sign in with Google. Please check your connection and try again.");
            }
        });
    }

    private void handleAuthResponse(Response<AuthResponse> response, String email) {
        if (response.isSuccessful() && response.body() != null) {
            AuthResponse authResponse = response.body();
            
            if ("success".equals(authResponse.getStatus())) {
                if (authResponse.isExists()) {
                    Log.d(TAG, "User exists in backend, checking local database");
                    checkUserLocallyAfterBackendAuth(email);
                } else {
                    Log.d(TAG, "User doesn't exist in backend");
                    showErrorDialog("Authentication Error", 
                        "The email " + email + " is not authorized to access this application. Please contact an administrator.");
                }
            } else {
                showToast("Authentication failed. Please try again.");
            }
        } else {
            String errorMessage = getServerErrorMessage(response.code());
            showErrorDialog("Server Error", errorMessage);
            Log.e(TAG, "Server error: " + response.code() + " " + response.message());
        }
    }
    
    @SuppressLint("CheckResult")
    private void checkUserLocallyAfterBackendAuth(String email) {
        AlertDialog loadingDialog = createLoadingDialog("Checking Account", "Please wait...");
        loadingDialog.show();
        
        try {
            initializeDatabaseIfNeeded();
            
            if (database == null) {
                loadingDialog.dismiss();
                handleUserNotFoundLocally(email);
                return;
            }
            
            database.userDao().getUserByEmail(email)
                .subscribe(
                    user -> {
                        loadingDialog.dismiss();
                        if (user != null) {
                            navigateToHomepage("Welcome back!");
                        } else {
                            handleUserNotFoundLocally(email);
                        }
                    },
                    error -> {
                        loadingDialog.dismiss();
                        Log.e(TAG, "Database error during local check after backend auth", error);
                        handleUserNotFoundLocally(email);
                    },
                    () -> {
                        loadingDialog.dismiss();
                        handleUserNotFoundLocally(email);
                    }
                );
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in checkUserLocallyAfterBackendAuth", e);
            handleUserNotFoundLocally(email);
        }
    }

    private void handleUserNotFoundLocally(String email) {
        Log.d(TAG, "User not found locally, showing password setup dialog");
        showToast("Email verified. Please set up your password.");
        showPasswordRegistrationDialog(email);
    }

    private void navigateToHomepage(String message) {
        Log.d(TAG, "User exists locally, proceeding to dashboard");
        showToast(message);
        Intent intent = new Intent(Login.this, Homepage.class);
        startActivity(intent);
        finish();
    }

    private void initializeDatabaseIfNeeded() {
        if (database == null) {
            database = AppDatabase.getInstance(getApplicationContext());
        }
    }
    
    private void showPasswordLoginDialog(String email) {
        AlertDialog dialog = createPasswordDialog(email, "Login", false);
        Button registerButton = dialog.findViewById(R.id.register_button);
        TextInputEditText passwordEditText = dialog.findViewById(R.id.password_edit_text);
        
        registerButton.setText("Login");
        registerButton.setOnClickListener(v -> {
            String password = passwordEditText.getText().toString().trim();
            
            if (TextUtils.isEmpty(password)) {
                passwordEditText.setError("Password is required");
                return;
            }
            
            String hashedPassword = PasswordHasherUtility.hashPassword(password);
            dialog.dismiss();
            validateUserCredentials(email, hashedPassword);
        });
        
        dialog.show();
    }
    
    private void showPasswordRegistrationDialog(String email) {
        AlertDialog dialog = createPasswordDialog(email, "Set Up Password", true);
        Button registerButton = dialog.findViewById(R.id.register_button);
        TextInputEditText passwordEditText = dialog.findViewById(R.id.password_edit_text);
        TextInputEditText confirmPasswordEditText = dialog.findViewById(R.id.confirm_password_edit_text);
        
        registerButton.setText("Set Password");
        registerButton.setOnClickListener(v -> {
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();
            
            if (!validatePasswordInputs(password, confirmPassword, passwordEditText, confirmPasswordEditText)) {
                return;
            }
            
            String hashedPassword = PasswordHasherUtility.hashPassword(password);
            saveUserToLocalDatabase(email, hashedPassword, dialog);
        });
        
        dialog.show();
    }

    private AlertDialog createPasswordDialog(String email, String title, boolean showConfirmPassword) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.register_dialog, null);
        builder.setView(dialogView);
        
        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView emailTextView = dialogView.findViewById(R.id.email_text);
        com.google.android.material.textfield.TextInputLayout confirmPasswordLayout = 
                dialogView.findViewById(R.id.confirm_password_input_layout);
        
        titleTextView.setText(title);
        emailTextView.setText("Email: " + email);
        
        if (!showConfirmPassword) {
            confirmPasswordLayout.setVisibility(View.GONE);
        }
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        return dialog;
    }

    private boolean validatePasswordInputs(String password, String confirmPassword, 
                                         TextInputEditText passwordEditText, 
                                         TextInputEditText confirmPasswordEditText) {
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return false;
        }
        
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            return false;
        }
        
        return true;
    }
    
    private void saveUserToLocalDatabase(String email, String hashedPassword, AlertDialog dialog) {
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        AuthRequest checkRequest = createAuthRequest(email, "", "");
        
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        checkCall.enqueue(new Callback<AuthResponse>() {
            @SuppressLint("CheckResult")
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists() 
                            && authResponse.getUser() != null) {
                        saveUserFromBackendResponse(authResponse.getUser(), email, hashedPassword, dialog);
                    } else {
                        dialog.dismiss();
                        showToast("Failed to get user details from server");
                    }
                } else {
                    dialog.dismiss();
                    showToast("Server error retrieving user data");
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                dialog.dismiss();
                showToast("Network error. Please check your connection.");
            }
        });
    }

    @SuppressLint("CheckResult")
    private void saveUserFromBackendResponse(AuthResponse.User backendUser, String email, 
                                           String hashedPassword, AlertDialog dialog) {
        initializeDatabaseIfNeeded();
        
        User localUser = new User(
                UUID.randomUUID().toString(),
                backendUser.getName() != null ? backendUser.getName() : "",
                backendUser.getSurname() != null ? backendUser.getSurname() : "",
                email,
                backendUser.getRole() != null ? backendUser.getRole() : "user",
                hashedPassword,
                System.currentTimeMillis()
        );
        
        database.userDao().insert(localUser)
            .subscribe(
                () -> {
                    dialog.dismiss();
                    showToast("Password set successfully!");
                    navigateToHomepage("");
                },
                error -> {
                    dialog.dismiss();
                    showToast("Error saving to local database: " + error.getMessage());
                    Log.e(TAG, "Database error", error);
                }
            );
    }
    
    @SuppressLint("CheckResult")
    private void validateUserCredentials(String email, String hashedPassword) {
        AlertDialog loadingDialog = createLoadingDialog("Authenticating", "Please wait...");
        loadingDialog.show();
        
        try {
            initializeDatabaseIfNeeded();
            
            if (database == null) {
                loadingDialog.dismiss();
                showAccountNotExistDialog();
                return;
            }
            
            database.userDao().getUserByEmail(email)
                .subscribe(
                    user -> {
                        loadingDialog.dismiss();
                        if (user != null && hashedPassword.equals(user.getHashedPassword())) {
                            showToast("Login successful");
                            navigateToHomepage("");
                        } else if (user != null) {
                            showToast("Invalid password");
                        } else {
                            showAccountNotExistDialog();
                        }
                    },
                    error -> {
                        loadingDialog.dismiss();
                        Log.e(TAG, "Database error", error);
                        showAccountNotExistDialog();
                    },
                    () -> {
                        loadingDialog.dismiss();
                        showAccountNotExistDialog();
                    }
                );
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in validateUserCredentials", e);
            showAccountNotExistDialog();
        }
    }

    private void showAccountNotExistDialog() {
        showErrorDialog("Login Error", 
            "Account does not exist. Please connect to the internet and sign in with Google to authenticate.");
    }

    private void initiatePasswordReset() {
        signInClient.beginSignIn(beginSignInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        startIntentSenderForResult(result.getPendingIntent().getIntentSender(), 
                                REQ_PASSWORD_RESET, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(TAG, "Couldn't start email selection for password reset: " + e.getLocalizedMessage());
                        showToast("Failed to show email selection");
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.d(TAG, "Email selection for password reset failed: " + e.getLocalizedMessage());
                    showToast("Email selection failed: " + e.getLocalizedMessage());
                });
    }

    @SuppressLint("CheckResult")
    private void checkEmailForPasswordReset(String email) {
        AlertDialog loadingDialog = createLoadingDialog("Checking Email", "Please wait...");
        loadingDialog.show();
        
        try {
            initializeDatabaseIfNeeded();
            
            if (database == null) {
                loadingDialog.dismiss();
                checkEmailWithBackendForReset(email);
                return;
            }
            
            database.userDao().getUserByEmail(email)
                .subscribe(
                    user -> {
                        loadingDialog.dismiss();
                        if (user != null) {
                            Log.d(TAG, "User exists locally, showing reset password dialog");
                            showToast("Email found! Please set your new password.");
                            showResetPasswordDialog(email);
                        } else {
                            Log.d(TAG, "User not found locally for password reset, checking with backend");
                            checkEmailWithBackendForReset(email);
                        }
                    },
                    error -> {
                        loadingDialog.dismiss();
                        Log.e(TAG, "Database error during email check for reset", error);
                        checkEmailWithBackendForReset(email);
                    },
                    () -> {
                        loadingDialog.dismiss();
                        Log.d(TAG, "User not found locally for password reset (onComplete), checking with backend");
                        checkEmailWithBackendForReset(email);
                    }
                );
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in checkEmailForPasswordReset", e);
            checkEmailWithBackendForReset(email);
        }
    }

    private void checkEmailWithBackendForReset(String email) {
        AuthRequest checkRequest = createAuthRequest(email, "", "");
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        
        checkCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists()) {
                        Log.d(TAG, "User exists in backend for password reset, showing reset dialog");
                        showToast("Email verified! Please set your new password.");
                        showResetPasswordDialog(email);
                    } else {
                        Log.d(TAG, "User doesn't exist in backend for password reset");
                        showErrorDialog("Email Not Found", 
                            "The email " + email + " is not registered in the system. Please contact an administrator.");
                    }
                } else {
                    String errorMessage = getServerErrorMessage(response.code());
                    showErrorDialog("Server Error", errorMessage);
                    Log.e(TAG, "Server error during password reset: " + response.code() + " " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e(TAG, "Network error during password reset", t);
                String errorMessage = getNetworkErrorMessage(t);
                showErrorDialog("Connection Error", errorMessage);
            }
        });
    }

    private String getNetworkErrorMessage(Throwable t) {
        String baseMessage = "Cannot connect to the authentication server. ";
        
        if (t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException) {
            return baseMessage + "Server is offline or unreachable. Please check your network connection or contact the administrator.";
        } else if (t instanceof java.net.UnknownHostException) {
            return baseMessage + "Server address not found. Please check your network connection.";
        } else {
            return baseMessage + t.getMessage();
        }
    }

    private void showResetPasswordDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.reset_password_dialog, null);
        builder.setView(dialogView);
        
        TextInputEditText newPasswordEditText = dialogView.findViewById(R.id.new_password_edit_text);
        TextInputEditText confirmPasswordEditText = dialogView.findViewById(R.id.confirm_password_edit_text);
        Button resetButton = dialogView.findViewById(R.id.reset_button);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        resetButton.setOnClickListener(v -> {
            String newPassword = newPasswordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();
            
            if (validateResetPasswordInputs(newPassword, confirmPassword, 
                    newPasswordEditText, confirmPasswordEditText)) {
                String hashedPassword = PasswordHasherUtility.hashPassword(newPassword);
                saveResetPasswordToLocalDatabase(email, hashedPassword, dialog);
            }
        });
        
        dialog.show();
    }

    private boolean validateResetPasswordInputs(String newPassword, String confirmPassword,
                                              TextInputEditText newPasswordEditText,
                                              TextInputEditText confirmPasswordEditText) {
        if (TextUtils.isEmpty(newPassword)) {
            newPasswordEditText.setError("New password is required");
            return false;
        }
        
        if (newPassword.length() < 6) {
            newPasswordEditText.setError("Password must be at least 6 characters");
            return false;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            return false;
        }
        
        return true;
    }

    @SuppressLint("CheckResult")
    private void saveResetPasswordToLocalDatabase(String email, String hashedPassword, AlertDialog dialog) {
        try {
            initializeDatabaseIfNeeded();
            
            if (database == null) {
                dialog.dismiss();
                showToast("Database error. Please try again.");
                return;
            }
            
            database.userDao().getUserByEmail(email)
                .subscribe(
                    user -> {
                        if (user != null) {
                            updateExistingUserPassword(email, hashedPassword, dialog);
                        } else {
                            fetchUserDetailsAndSavePasswordReset(email, hashedPassword, dialog);
                        }
                    },
                    error -> {
                        Log.e(TAG, "Database error checking user for password reset", error);
                        fetchUserDetailsAndSavePasswordReset(email, hashedPassword, dialog);
                    },
                    () -> fetchUserDetailsAndSavePasswordReset(email, hashedPassword, dialog)
                );
        } catch (Exception e) {
            dialog.dismiss();
            Log.e(TAG, "Error in saveResetPasswordToLocalDatabase", e);
            showToast("Database error. Please try again.");
        }
    }

    @SuppressLint("CheckResult")
    private void updateExistingUserPassword(String email, String hashedPassword, AlertDialog dialog) {
        database.userDao().updatePassword(email, hashedPassword)
            .subscribe(
                () -> {
                    dialog.dismiss();
                    showToast("Password reset successfully!");
                    Log.d(TAG, "Password reset successfully for local user: " + email);
                },
                error -> {
                    dialog.dismiss();
                    showToast("Error updating password: " + error.getMessage());
                    Log.e(TAG, "Database error updating password", error);
                }
            );
    }

    private void fetchUserDetailsAndSavePasswordReset(String email, String hashedPassword, AlertDialog dialog) {
        AuthRequest checkRequest = createAuthRequest(email, "", "");
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        
        checkCall.enqueue(new Callback<AuthResponse>() {
            @SuppressLint("CheckResult")
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists() 
                            && authResponse.getUser() != null) {
                        saveUserFromBackendResponse(authResponse.getUser(), email, hashedPassword, dialog);
                    } else {
                        dialog.dismiss();
                        showToast("Failed to get user details from server");
                    }
                } else {
                    dialog.dismiss();
                    showToast("Server error retrieving user data");
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                dialog.dismiss();
                showToast("Network error. Please check your connection.");
            }
        });
    }

    // ============ UTILITY METHODS ============
    
    private AlertDialog createLoadingDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        return builder.create();
    }
    
    private void showErrorDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private void showToast(String message) {
        Toast.makeText(Login.this, message, Toast.LENGTH_SHORT).show();
    }
    
    private String createTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
    }
    
    private String getServerErrorMessage(int responseCode) {
        switch (responseCode) {
            case 500:
                return "Internal server error. Please contact administrator.";
            case 404:
                return "Server endpoint not found. Please check server configuration.";
            default:
                return "Server error: " + responseCode;
        }
    }
    
    private AuthRequest createAuthRequest(String email, String firstName, String lastName) {
        return new AuthRequest(email, firstName, lastName, createTimestamp());
    }
}