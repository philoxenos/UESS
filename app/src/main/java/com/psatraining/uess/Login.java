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
    private SignInClient signInClient;
    private BeginSignInRequest beginSignInRequest;
    private static final int REQ_ONE_TAP = 100;
    private static final int REQ_PASSWORD_RESET = 101;
    
    // Database reference
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        // Set up the QR code icon click listener
        ImageView qrCodeIcon = findViewById(R.id.qr_code_icon);
        qrCodeIcon.setOnClickListener(v -> showQrCodeDialog());

        // Initialize views
        EditText emailInput = findViewById(R.id.email_edit_text);
        EditText passwordInput = findViewById(R.id.password_edit_text);
        Button signInButton = findViewById(R.id.submit_button);
        Button googleSignInButton = findViewById(R.id.button);  // Google sign up button
        TextView forgotPasswordLink = findViewById(R.id.textView5);  // Forgot Password link

        signInClient = Identity.getSignInClient(this);
        beginSignInRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                        .setSupported(true)
                        .build())
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        // Your server's client ID, not your Android client ID.
                        .setServerClientId(getString(R.string.google_client_id))
                        // Only show accounts previously used to sign in.
                        .setFilterByAuthorizedAccounts(false)
                        .build())
                // Automatically sign in when exactly one credential is retrieved.
                .setAutoSelectEnabled(true)
                .build();

        // Initialize database
        database = AppDatabase.getInstance(getApplicationContext());

        // Set up click listeners
        signInButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            
            // Validate inputs
            if (TextUtils.isEmpty(email)) {
                emailInput.setError("Email is required");
                return;
            }
            
            if (TextUtils.isEmpty(password)) {
                passwordInput.setError("Password is required");
                return;
            }
            
            // Hash the password for comparison with stored hash
            String hashedPassword = PasswordHasherUtility.hashPassword(password);
            
            // Check against local database
            validateUserCredentials(email, hashedPassword);
        });
        googleSignInButton.setOnClickListener(this::buttonGoogleSignIn);
        forgotPasswordLink.setOnClickListener(v -> {
            // Start password reset flow
            initiatePasswordReset();
        });


        

    }

    private void showQrCodeDialog() {
        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.qr_dialog, null);
        builder.setView(dialogView);

        // Find the QR code ImageView in the dialog layout
        ImageView qrImageView = dialogView.findViewById(R.id.dialog_qrcode);

        // Generate the QR code using your utility
        QRUtility.showDeviceQRCode(this, qrImageView);

        // Create and show the dialog
        AlertDialog dialog = builder.create();

        // Make the dialog rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }

        dialog.show();
    }

    private void buttonGoogleSignIn(View view){
        signInClient.beginSignIn(beginSignInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        startIntentSenderForResult(
                                result.getPendingIntent().getIntentSender(), REQ_ONE_TAP,
                                null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(TAG, "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                    }
                })
                .addOnFailureListener(this, e -> {
                    String errorMessage = e.getLocalizedMessage();
                    Log.d(TAG, "Google Sign-In failed: " + errorMessage);
                    Toast.makeText(Login.this, "Sign-In Error: " + errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ONE_TAP) {
            try {
                SignInCredential credential = signInClient.getSignInCredentialFromIntent(data);
                String idToken = credential.getGoogleIdToken();
                String email = credential.getId();
                String givenName = credential.getGivenName();
                String familyName = credential.getFamilyName();

                if (idToken != null) {
                    // Got an ID token from Google. Use it to authenticate with our backend.
                    Log.d(TAG, "Got ID token and email: " + email);

                    // Create the authentication request object
                    AuthRequest authRequest = createAuthRequest(email,
                            givenName != null ? givenName : "",
                            familyName != null ? familyName : "");

                    // Make the API call to authenticate the user
                    authenticateUser(authRequest);
                }
            } catch (ApiException e) {
                Log.e(TAG, "Error getting sign-in credentials: " + e.getMessage(), e);
                Toast.makeText(this, "Authentication failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_PASSWORD_RESET) {
            try {
                SignInCredential credential = signInClient.getSignInCredentialFromIntent(data);
                String email = credential.getId();

                if (email != null) {
                    // Got email for password reset. Check in local database first
                    Log.d(TAG, "Got email for password reset: " + email);
                    checkEmailForPasswordReset(email);
                }
            } catch (ApiException e) {
                Log.e(TAG, "Error getting email for password reset: " + e.getMessage(), e);
                Toast.makeText(this, "Failed to get email: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void authenticateUser(AuthRequest authRequest) {
        // Directly authenticate with backend instead of checking local database first
        authenticateWithBackend(authRequest);
    }
    
    
    private void authenticateWithBackend(AuthRequest authRequest) {
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        Call<AuthResponse> call = authService.authenticateUser(authRequest);
        
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus())) {
                        if (authResponse.isExists()) {
                            // User exists in the backend, now check if they exist locally
                            Log.d(TAG, "User exists in backend, checking local database");
                            checkUserLocallyAfterBackendAuth(authRequest.getEmail());
                        } else {
                            // User doesn't exist, show error message
                            Log.d(TAG, "User doesn't exist in backend");
                            AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                            builder.setTitle("Authentication Error")
                                   .setMessage("The email " + authRequest.getEmail() + " is not authorized to access this application. Please contact an administrator.")
                                   .setPositiveButton("OK", null)
                                   .show();
                        }
                    } else {
                        // Handle error in response
                        Toast.makeText(Login.this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Handle unsuccessful response
                    String errorMessage = getServerErrorMessage(response.code());
                    showErrorDialog("Server Error", errorMessage);
                    Log.e(TAG, "Server error: " + response.code() + " " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                // Handle network failure - show specific message for Google sign-in requiring internet
                Log.e(TAG, "Network error", t);
                showErrorDialog("Internet Connection Required", 
                    "You need an internet connection to sign in with Google. Please check your connection and try again.");
            }
        });
    }
    
    @SuppressLint("CheckResult")
    private void checkUserLocallyAfterBackendAuth(String email) {
        // Show loading dialog
        AlertDialog loadingDialog = createLoadingDialog("Checking Account", "Please wait...");
        loadingDialog.show();
        
        try {
            // Initialize database if not already initialized
            if (database == null) {
                database = AppDatabase.getInstance(getApplicationContext());
            }
            
            if (database == null) {
                // Database initialization failed, show password setup dialog
                loadingDialog.dismiss();
                Log.d(TAG, "Database initialization failed, showing password setup dialog");
                Toast.makeText(Login.this, "Email verified. Please set up your password.", Toast.LENGTH_SHORT).show();
                showPasswordRegistrationDialog(email);
                return;
            }
            
            try {
                // Query the database for the user
                database.userDao().getUserByEmail(email)
                    .subscribe(
                        user -> {
                            loadingDialog.dismiss();
                            
                            if (user != null) {
                                // User exists locally with password, proceed to dashboard
                                Log.d(TAG, "User exists locally, proceeding to dashboard");
                                Toast.makeText(Login.this, "Welcome back!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(Login.this, Homepage.class);
                                startActivity(intent);
                                finish();
                            } else {
                                // User doesn't exist locally, show password setup dialog
                                Log.d(TAG, "User not found locally, showing password setup dialog");
                                Toast.makeText(Login.this, "Email verified. Please set up your password.", Toast.LENGTH_SHORT).show();
                                showPasswordRegistrationDialog(email);
                            }
                        },
                        error -> {
                            loadingDialog.dismiss();
                            Log.e(TAG, "Database error during local check after backend auth", error);
                            // On database error, fallback to password setup
                            Toast.makeText(Login.this, "Email verified. Please set up your password.", Toast.LENGTH_SHORT).show();
                            showPasswordRegistrationDialog(email);
                        },
                        () -> {
                            // No user found locally, show password setup dialog
                            loadingDialog.dismiss();
                            Log.d(TAG, "User not found locally (onComplete), showing password setup dialog");
                            Toast.makeText(Login.this, "Email verified. Please set up your password.", Toast.LENGTH_SHORT).show();
                            showPasswordRegistrationDialog(email);
                        }
                    );
            } catch (Exception e) {
                loadingDialog.dismiss();
                Log.e(TAG, "Error querying local database after backend auth", e);
                // On error, fallback to password setup
                Toast.makeText(Login.this, "Email verified. Please set up your password.", Toast.LENGTH_SHORT).show();
                showPasswordRegistrationDialog(email);
            }
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in checkUserLocallyAfterBackendAuth", e);
            // On error, fallback to password setup
            Toast.makeText(Login.this, "Email verified. Please set up your password.", Toast.LENGTH_SHORT).show();
            showPasswordRegistrationDialog(email);
        }
    }
    
    private void showPasswordLoginDialog(String email) {
        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.register_dialog, null);
        builder.setView(dialogView);
        
        // Find the views in the dialog using the correct IDs from register_dialog.xml
        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView emailTextView = dialogView.findViewById(R.id.email_text);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.password_edit_text);
        com.google.android.material.textfield.TextInputLayout confirmPasswordLayout = dialogView.findViewById(R.id.confirm_password_input_layout);
        Button registerButton = dialogView.findViewById(R.id.register_button);
        
        // Set the dialog title and email text
        titleTextView.setText("Login");
        emailTextView.setText("Email: " + email);
        
        // Hide confirm password field for login
        confirmPasswordLayout.setVisibility(View.GONE);
        
        // Change button text to "Login"
        registerButton.setText("Login");
        
        // Create the dialog
        AlertDialog dialog = builder.create();
        
        // Make the dialog rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        // Set up the login button click listener
        registerButton.setOnClickListener(v -> {
            String password = passwordEditText.getText().toString().trim();
            
            // Validate password
            if (TextUtils.isEmpty(password)) {
                passwordEditText.setError("Password is required");
                return;
            }
            
            // Hash the password for local verification
            String hashedPassword = PasswordHasherUtility.hashPassword(password);
            
            // Validate credentials locally and proceed with login
            dialog.dismiss();
            validateUserCredentials(email, hashedPassword);
        });
        
        dialog.show();
    }
    
    private void showPasswordRegistrationDialog(String email) {
        // Create the dialog for initial password setup
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.register_dialog, null);
        builder.setView(dialogView);
        
        // Find the views in the dialog using the correct IDs from register_dialog.xml
        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView emailTextView = dialogView.findViewById(R.id.email_text);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.password_edit_text);
        TextInputEditText confirmPasswordEditText = dialogView.findViewById(R.id.confirm_password_edit_text);
        Button registerButton = dialogView.findViewById(R.id.register_button);
        
        // Set the dialog title and email text
        titleTextView.setText("Set Up Password");
        emailTextView.setText("Email: " + email);
        
        // Change button text to "Set Password"
        registerButton.setText("Set Password");
        
        // Create the dialog
        AlertDialog dialog = builder.create();
        
        // Make the dialog rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        // Set up the register button click listener
        registerButton.setOnClickListener(v -> {
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();
            
            // Validate passwords
            if (TextUtils.isEmpty(password)) {
                passwordEditText.setError("Password is required");
                return;
            }
            
            if (!password.equals(confirmPassword)) {
                confirmPasswordEditText.setError("Passwords do not match");
                return;
            }
            
            // Hash the password
            String hashedPassword = PasswordHasherUtility.hashPassword(password);
            
            // Save to local database and proceed with login
            saveUserToLocalDatabase(email, hashedPassword, dialog);
        });
        
        dialog.show();
    }
    
    private void saveUserToLocalDatabase(String email, String hashedPassword, AlertDialog dialog) {
        // Fetch user details from backend and save to local database
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
                        // Get user details from response
                        AuthResponse.User backendUser = authResponse.getUser();
                        
                        // Initialize database if not already initialized
                        if (database == null) {
                            database = AppDatabase.getInstance(getApplicationContext());
                        }
                        
                        // Create User object for local database
                        User localUser = new User(
                                UUID.randomUUID().toString(), // Generate a unique ID
                                backendUser.getName() != null ? backendUser.getName() : "",
                                backendUser.getSurname() != null ? backendUser.getSurname() : "",
                                email,
                                backendUser.getRole() != null ? backendUser.getRole() : "user",
                                hashedPassword, // Use the hashed password
                                System.currentTimeMillis() // Current timestamp
                        );
                        
                        // Save to local database
                        database.userDao().insert(localUser)
                            .subscribe(
                                () -> {
                                    dialog.dismiss();
                                    Toast.makeText(Login.this, "Password set successfully!", Toast.LENGTH_SHORT).show();
                                    // Navigate to main activity after successful login
                                    Intent intent = new Intent(Login.this, Homepage.class);
                                    startActivity(intent);
                                    finish();
                                },
                                error -> {
                                    dialog.dismiss();
                                    Toast.makeText(Login.this, "Error saving to local database: " + error.getMessage(), 
                                                  Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Database error", error);
                                }
                            );
                    } else {
                        dialog.dismiss();
                        Toast.makeText(Login.this, "Failed to get user details from server", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    dialog.dismiss();
                    Toast.makeText(Login.this, "Server error retrieving user data", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                dialog.dismiss();
                Toast.makeText(Login.this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Validates user credentials against the local database
     * @param email User email
     * @param hashedPassword Hashed password to check
     */
    @SuppressLint("CheckResult")
    private void validateUserCredentials(String email, String hashedPassword) {
        // Show loading dialog
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        loadingBuilder.setTitle("Authenticating");
        loadingBuilder.setMessage("Please wait...");
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        loadingDialog.show();
        
        try {
            // Initialize database if not already initialized
            if (database == null) {
                database = AppDatabase.getInstance(getApplicationContext());
            }
            
            if (database == null) {
                // Database initialization failed
                loadingDialog.dismiss();
                showAccountNotExistDialog();
                return;
            }
            
            try {
                // Query the database for the user
                database.userDao().getUserByEmail(email)
                    .subscribe(
                        user -> {
                            loadingDialog.dismiss();
                            
                            // User found, check password
                            if (user != null && hashedPassword.equals(user.getHashedPassword())) {
                                // Password matches, proceed to homepage
                                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(Login.this, Homepage.class);
                                startActivity(intent);
                                finish();
                            } else if (user != null) {
                                // User exists but password doesn't match
                                Toast.makeText(this, "Invalid password", Toast.LENGTH_SHORT).show();
                            } else {
                                // User doesn't exist in local database
                                showAccountNotExistDialog();
                            }
                        },
                        error -> {
                            loadingDialog.dismiss();
                            Log.e(TAG, "Database error", error);
                            // On database error, show account not exist dialog
                            showAccountNotExistDialog();
                        },
                        () -> {
                            // No user found with this email
                            loadingDialog.dismiss();
                            showAccountNotExistDialog();
                        }
                    );
            } catch (Exception e) {
                loadingDialog.dismiss();
                Log.e(TAG, "Error querying database", e);
                showAccountNotExistDialog();
            }
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in validateUserCredentials", e);
            showAccountNotExistDialog();
        }
    }
    
    /**
     * Shows dialog when account doesn't exist locally
     */
    private void showAccountNotExistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Login Error")
               .setMessage("Account does not exist. Please connect to the internet and sign in with Google to authenticate.")
               .setPositiveButton("OK", null)
               .show();
    }
    
    /**
     * Initiates the password reset flow by showing Google email selection
     */
    private void initiatePasswordReset() {
        signInClient.beginSignIn(beginSignInRequest)
                .addOnSuccessListener(this, result -> {
                    try {
                        startIntentSenderForResult(
                                result.getPendingIntent().getIntentSender(), REQ_PASSWORD_RESET,
                                null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(TAG, "Couldn't start email selection for password reset: " + e.getLocalizedMessage());
                        Toast.makeText(Login.this, "Failed to show email selection", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(this, e -> {
                    String errorMessage = e.getLocalizedMessage();
                    Log.d(TAG, "Email selection for password reset failed: " + errorMessage);
                    Toast.makeText(Login.this, "Email selection failed: " + errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Checks if the selected email exists for password reset
     * @param email The email selected for password reset
     */
    @SuppressLint("CheckResult")
    private void checkEmailForPasswordReset(String email) {
        // Show loading dialog
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        loadingBuilder.setTitle("Checking Email");
        loadingBuilder.setMessage("Please wait...");
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        loadingDialog.show();
        
        try {
            // Initialize database if not already initialized
            if (database == null) {
                database = AppDatabase.getInstance(getApplicationContext());
            }
            
            if (database == null) {
                // Database initialization failed, proceed with backend check
                loadingDialog.dismiss();
                checkEmailWithBackendForReset(email);
                return;
            }
            
            try {
                // Query the database for the user
                database.userDao().getUserByEmail(email)
                    .subscribe(
                        user -> {
                            loadingDialog.dismiss();
                            
                            if (user != null) {
                                // User exists locally, show reset password dialog
                                Log.d(TAG, "User exists locally, showing reset password dialog");
                                Toast.makeText(Login.this, "Email found! Please set your new password.", Toast.LENGTH_SHORT).show();
                                showResetPasswordDialog(email);
                            } else {
                                // User doesn't exist locally, check with backend
                                Log.d(TAG, "User not found locally for password reset, checking with backend");
                                checkEmailWithBackendForReset(email);
                            }
                        },
                        error -> {
                            loadingDialog.dismiss();
                            Log.e(TAG, "Database error during email check for reset", error);
                            // On database error, fallback to backend check
                            checkEmailWithBackendForReset(email);
                        },
                        () -> {
                            // No user found locally, check with backend
                            loadingDialog.dismiss();
                            Log.d(TAG, "User not found locally for password reset (onComplete), checking with backend");
                            checkEmailWithBackendForReset(email);
                        }
                    );
            } catch (Exception e) {
                loadingDialog.dismiss();
                Log.e(TAG, "Error querying local database for password reset", e);
                // On error, fallback to backend check
                checkEmailWithBackendForReset(email);
            }
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in checkEmailForPasswordReset", e);
            // On error, fallback to backend check
            checkEmailWithBackendForReset(email);
        }
    }

    /**
     * Checks if the email exists in the backend for password reset
     * @param email The email to check
     */
    private void checkEmailWithBackendForReset(String email) {
        // Create authentication request to check if email exists
        AuthRequest checkRequest = createAuthRequest(email, "", "");
        
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        
        checkCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists()) {
                        // User exists in backend, show reset password dialog
                        Log.d(TAG, "User exists in backend for password reset, showing reset dialog");
                        Toast.makeText(Login.this, "Email verified! Please set your new password.", Toast.LENGTH_SHORT).show();
                        showResetPasswordDialog(email);
                    } else {
                        // User doesn't exist in backend
                        Log.d(TAG, "User doesn't exist in backend for password reset");
                        showErrorDialog("Email Not Found", 
                            "The email " + email + " is not registered in the system. Please contact an administrator.");
                    }
                } else {
                    // Handle unsuccessful response
                    String errorMessage = getServerErrorMessage(response.code());
                    showErrorDialog("Server Error", errorMessage);
                    Log.e(TAG, "Server error during password reset: " + response.code() + " " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                // Handle network failure with more detailed error message
                Log.e(TAG, "Network error during password reset", t);
                
                String errorMessage = "Cannot connect to the authentication server. ";
                
                // Provide more specific error message based on the exception
                if (t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException) {
                    errorMessage += "Server is offline or unreachable. Please check your network connection or contact the administrator.";
                } else if (t instanceof java.net.UnknownHostException) {
                    errorMessage += "Server address not found. Please check your network connection.";
                } else {
                    errorMessage += t.getMessage();
                }
                
                AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                builder.setTitle("Connection Error")
                       .setMessage(errorMessage)
                       .setPositiveButton("OK", null)
                       .show();
            }
        });
    }

    /**
     * Shows the reset password dialog
     * @param email The email for which to reset password
     */
    private void showResetPasswordDialog(String email) {
        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.reset_password_dialog, null);
        builder.setView(dialogView);
        
        // Find the views in the dialog
        TextInputEditText newPasswordEditText = dialogView.findViewById(R.id.new_password_edit_text);
        TextInputEditText confirmPasswordEditText = dialogView.findViewById(R.id.confirm_password_edit_text);
        Button resetButton = dialogView.findViewById(R.id.reset_button);
        
        // Create the dialog
        AlertDialog dialog = builder.create();
        
        // Make the dialog rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        // Set up the reset button click listener
        resetButton.setOnClickListener(v -> {
            String newPassword = newPasswordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();
            
            // Validate passwords
            if (TextUtils.isEmpty(newPassword)) {
                newPasswordEditText.setError("New password is required");
                return;
            }
            
            if (newPassword.length() < 6) {
                newPasswordEditText.setError("Password must be at least 6 characters");
                return;
            }
            
            if (!newPassword.equals(confirmPassword)) {
                confirmPasswordEditText.setError("Passwords do not match");
                return;
            }
            
            // Hash the new password
            String hashedPassword = PasswordHasherUtility.hashPassword(newPassword);
            
            // Save the new password to local database
            saveResetPasswordToLocalDatabase(email, hashedPassword, dialog);
        });
        
        dialog.show();
    }

    /**
     * Saves the reset password to local database
     * @param email The user's email
     * @param hashedPassword The new hashed password
     * @param dialog The dialog to dismiss
     */
    @SuppressLint("CheckResult")
    private void saveResetPasswordToLocalDatabase(String email, String hashedPassword, AlertDialog dialog) {
        try {
            // Initialize database if not already initialized
            if (database == null) {
                database = AppDatabase.getInstance(getApplicationContext());
            }
            
            if (database == null) {
                dialog.dismiss();
                Toast.makeText(Login.this, "Database error. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                // First check if user exists locally
                database.userDao().getUserByEmail(email)
                    .subscribe(
                        user -> {
                            if (user != null) {
                                // User exists locally, update password
                                database.userDao().updatePassword(email, hashedPassword)
                                    .subscribe(
                                        () -> {
                                            dialog.dismiss();
                                            Toast.makeText(Login.this, "Password reset successfully!", Toast.LENGTH_SHORT).show();
                                            Log.d(TAG, "Password reset successfully for local user: " + email);
                                        },
                                        error -> {
                                            dialog.dismiss();
                                            Toast.makeText(Login.this, "Error updating password: " + error.getMessage(), 
                                                          Toast.LENGTH_SHORT).show();
                                            Log.e(TAG, "Database error updating password", error);
                                        }
                                    );
                            } else {
                                // User doesn't exist locally, need to fetch from backend first
                                fetchUserDetailsAndSavePasswordReset(email, hashedPassword, dialog);
                            }
                        },
                        error -> {
                            Log.e(TAG, "Database error checking user for password reset", error);
                            // Try to fetch from backend
                            fetchUserDetailsAndSavePasswordReset(email, hashedPassword, dialog);
                        },
                        () -> {
                            // No user found locally, fetch from backend
                            fetchUserDetailsAndSavePasswordReset(email, hashedPassword, dialog);
                        }
                    );
            } catch (Exception e) {
                dialog.dismiss();
                Log.e(TAG, "Error in saveResetPasswordToLocalDatabase", e);
                Toast.makeText(Login.this, "Database error. Please try again.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            dialog.dismiss();
            Log.e(TAG, "Error initializing database for password reset", e);
            Toast.makeText(Login.this, "Database error. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Fetches user details from backend and saves with new password to local database
     * @param email The user's email
     * @param hashedPassword The new hashed password
     * @param dialog The dialog to dismiss
     */
    private void fetchUserDetailsAndSavePasswordReset(String email, String hashedPassword, AlertDialog dialog) {
        // Create authentication request to get user details
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
                        // Get user details from response
                        AuthResponse.User backendUser = authResponse.getUser();
                        
                        // Initialize database if not already initialized
                        if (database == null) {
                            database = AppDatabase.getInstance(getApplicationContext());
                        }
                        
                        // Create User object for local database with new password
                        User localUser = new User(
                                UUID.randomUUID().toString(), // Generate a unique ID
                                backendUser.getName() != null ? backendUser.getName() : "",
                                backendUser.getSurname() != null ? backendUser.getSurname() : "",
                                email,
                                backendUser.getRole() != null ? backendUser.getRole() : "user",
                                hashedPassword, // Use the new hashed password
                                System.currentTimeMillis() // Current timestamp
                        );
                        
                        // Save to local database
                        database.userDao().insert(localUser)
                            .subscribe(
                                () -> {
                                    dialog.dismiss();
                                    Toast.makeText(Login.this, "Password reset successfully!", Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "Password reset successfully for new local user: " + email);
                                },
                                error -> {
                                    dialog.dismiss();
                                    Toast.makeText(Login.this, "Error saving password reset to local database: " + error.getMessage(), 
                                                  Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Database error during password reset save", error);
                                }
                            );
                    } else {
                        dialog.dismiss();
                        Toast.makeText(Login.this, "Failed to get user details from server", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    dialog.dismiss();
                    Toast.makeText(Login.this, "Server error retrieving user data", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                dialog.dismiss();
                Toast.makeText(Login.this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // ============ UTILITY METHODS ============
    
    /**
     * Creates a standard loading dialog
     */
    private AlertDialog createLoadingDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        return builder.create();
    }
    
    /**
     * Shows a standard error dialog
     */
    private void showErrorDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    /**
     * Creates timestamp for API requests
     */
    private String createTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
    }
    
    /**
     * Handles server error responses
     */
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
    
    /**
     * Creates AuthRequest with current timestamp
     */
    private AuthRequest createAuthRequest(String email, String firstName, String lastName) {
        return new AuthRequest(email, firstName, lastName, createTimestamp());
    }

}