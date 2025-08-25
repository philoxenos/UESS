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
import com.google.android.material.textfield.TextInputLayout;
import com.psatraining.uess.Utility.PasswordHasherUtility;
import com.psatraining.uess.api.AuthService;
import com.psatraining.uess.api.RetrofitClient;
import com.psatraining.uess.database.AppDatabase;
import com.psatraining.uess.model.AuthRequest;
import com.psatraining.uess.model.AuthResponse;
import com.psatraining.uess.model.UpdateUserRequest;
import com.psatraining.uess.model.UpdateUserResponse;
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
            // Simply redirect to login page for now
            Toast.makeText(Login.this, "Please use Google Sign-In to verify your email", Toast.LENGTH_LONG).show();
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
                String displayName = credential.getDisplayName();
                String givenName = credential.getGivenName();
                String familyName = credential.getFamilyName();

                if (idToken != null) {
                    // Got an ID token from Google. Use it to authenticate with our backend.
                    Log.d(TAG, "Got ID token and email: " + email);

                    // Create timestamp for the authentication request
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                            .format(new Date());

                    // Create the authentication request object
                    AuthRequest authRequest = new AuthRequest(email,
                            givenName != null ? givenName : "",
                            familyName != null ? familyName : "",
                            timestamp);

                    // Make the API call to authenticate the user
                    authenticateUser(authRequest);
                }
            } catch (ApiException e) {
                Log.e(TAG, "Error getting sign-in credentials: " + e.getMessage(), e);
                Toast.makeText(this, "Authentication failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void authenticateUser(AuthRequest authRequest) {
        // First check if user exists in local database before making backend call
        checkUserLocallyFirst(authRequest);
    }
    
    @SuppressLint("CheckResult")
    private void checkUserLocallyFirst(AuthRequest authRequest) {
        // Show loading dialog
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        loadingBuilder.setTitle("Checking User");
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
                authenticateWithBackend(authRequest);
                return;
            }
            
            try {
                // Query the database for the user
                database.userDao().getUserByEmail(authRequest.getEmail())
                    .subscribe(
                        user -> {
                            loadingDialog.dismiss();
                            
                            if (user != null) {
                                // User exists locally, show password login dialog
                                Log.d(TAG, "User exists locally, showing password login dialog");
                                Toast.makeText(Login.this, "Welcome back! Please enter your password.", Toast.LENGTH_SHORT).show();
                                showPasswordLoginDialog(authRequest.getEmail());
                            } else {
                                // User doesn't exist locally, check with backend
                                Log.d(TAG, "User not found locally, checking with backend");
                                authenticateWithBackend(authRequest);
                            }
                        },
                        error -> {
                            loadingDialog.dismiss();
                            Log.e(TAG, "Database error during local check", error);
                            // On database error, fallback to backend check
                            authenticateWithBackend(authRequest);
                        },
                        () -> {
                            // No user found locally, check with backend
                            loadingDialog.dismiss();
                            Log.d(TAG, "User not found locally (onComplete), checking with backend");
                            authenticateWithBackend(authRequest);
                        }
                    );
            } catch (Exception e) {
                loadingDialog.dismiss();
                Log.e(TAG, "Error querying local database", e);
                // On error, fallback to backend check
                authenticateWithBackend(authRequest);
            }
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in checkUserLocallyFirst", e);
            // On error, fallback to backend check
            authenticateWithBackend(authRequest);
        }
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
                            // User exists in the backend, show password setup dialog for new users
                            Log.d(TAG, "User exists in backend, showing password setup dialog");
                            Toast.makeText(Login.this, "Email verified. Please set up your password.", Toast.LENGTH_SHORT).show();
                            showPasswordRegistrationDialog(authRequest.getEmail());
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
                    String errorMessage = "Server error: ";
                    if (response.code() == 500) {
                        errorMessage += "Internal server error. Please contact administrator.";
                    } else if (response.code() == 404) {
                        errorMessage += "Server endpoint not found. Please check server configuration.";
                    } else {
                        errorMessage += response.code() + " " + response.message();
                    }
                    
                    AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                    builder.setTitle("Server Error")
                           .setMessage(errorMessage)
                           .setPositiveButton("OK", null)
                           .show();
                    
                    Log.e(TAG, "Server error: " + response.code() + " " + response.message());
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                // Handle network failure with more detailed error message
                Log.e(TAG, "Network error", t);
                
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
    
    private void displayPasswordDialog(String email) {
        // This method is now simplified since we don't store passwords in backend
        // Just show the password login dialog directly
        showPasswordLoginDialog(email);
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
        AuthRequest checkRequest = new AuthRequest(email, "", "", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        
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
               .setMessage("Account does not exist. Please connect to the internet and sign up with Google to authenticate.")
               .setPositiveButton("OK", null)
               .show();
    }
    
    /**
     * Check user credentials with the backend server
     * @param email User email
     * @param hashedPassword Hashed password to check
     */
    private void checkUserWithBackend(String email, String hashedPassword) {
        // Create authentication request
        AuthRequest checkRequest = new AuthRequest(email, "", "", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        
        checkCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists()) {
                        // User exists in backend but not in local DB, prompt to set password
                        Toast.makeText(Login.this, "Please set up your password", Toast.LENGTH_SHORT).show();
                        showPasswordRegistrationDialog(email);
                    } else {
                        // User doesn't exist in backend either
                        Toast.makeText(Login.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Server error
                    Toast.makeText(Login.this, "Server error. Please try again later.", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Toast.makeText(Login.this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Fetch user details from backend and save to local database
     * @param email User email
     * @param hashedPassword Hashed password
     */
    private void fetchUserDetailsAndSaveToLocalDB(String email, String hashedPassword) {
        // Create authentication request to get user details
        AuthRequest checkRequest = new AuthRequest(email, "", "", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        
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
                                    // Navigate to main activity after successful login
                                    Intent intent = new Intent(Login.this, Homepage.class);
                                    startActivity(intent);
                                    finish();
                                },
                                error -> {
                                    Toast.makeText(Login.this, "Error saving to local database: " + error.getMessage(), 
                                                  Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Database error", error);
                                }
                            );
                    } else {
                        Toast.makeText(Login.this, "Failed to get user details from server", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(Login.this, "Server error retrieving user data", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Toast.makeText(Login.this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

}