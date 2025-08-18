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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.BeginSignInResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
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
                        .setServerClientId("904774983485-set6ptkk5s0gq40a9uec8uuc31d0as2c.apps.googleusercontent.com") // TODO
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
                .addOnSuccessListener(this, new OnSuccessListener<BeginSignInResult>() {
            @Override
            public void onSuccess(BeginSignInResult result) {
                try {
                    startIntentSenderForResult(
                            result.getPendingIntent().getIntentSender(), REQ_ONE_TAP,
                            null, 0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    Log.e(TAG, "Couldn't start One Tap UI: " + e.getLocalizedMessage());
                }
            }
        })
                .addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                String errorMessage = e.getLocalizedMessage();
                Log.d(TAG, "Google Sign-In failed: " + errorMessage);
                Toast.makeText(Login.this, "Sign-In Error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
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
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        Call<AuthResponse> call = authService.authenticateUser(authRequest);
        
        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus())) {
                        if (authResponse.isExists()) {
                            // Check if user already has a password
                            if (authResponse.getUser() != null && authResponse.getUser().getPassword() != null 
                                    && !authResponse.getUser().getPassword().isEmpty()) {
                                // User already has a password, don't show password creation dialog
                                Log.d(TAG, "User exists and already has a password");
                                AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                                builder.setTitle("Account Exists")
                                       .setMessage("This email is already registered. Please sign in with your password.")
                                       .setPositiveButton("OK", null)
                                       .show();
                            } else {
                                // User exists but no password yet
                                Log.d(TAG, "User exists in backend but needs password");
                                Toast.makeText(Login.this, "Email verified. Please set your password.", Toast.LENGTH_SHORT).show();
                                showPasswordCreationDialog(authRequest.getEmail());
                            }
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
    
    private void showPasswordCreationDialog(String email) {
        // First verify if the user exists in the database
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        
        // Create authentication request to check if user exists
        AuthRequest checkRequest = new AuthRequest(email, "", "", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        
        // Show loading indicator
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        loadingBuilder.setTitle("Verifying Email");
        loadingBuilder.setMessage("Please wait...");
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        loadingDialog.show();
        
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        checkCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                loadingDialog.dismiss();
                
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists()) {
                        // Check if user already has a password
                        if (authResponse.getUser() != null && authResponse.getUser().getPassword() != null 
                                && !authResponse.getUser().getPassword().isEmpty()) {
                            // User already has a password, show message
                            AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                            builder.setTitle("Account Already Exists")
                                   .setMessage("This email already has an account. Please sign in with your password.")
                                   .setPositiveButton("OK", null)
                                   .show();
                        } else {
                            // User exists but no password, show password creation dialog
                            displayPasswordDialog(email);
                        }
                    } else {
                        // User doesn't exist, show error message
                        AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                        builder.setTitle("Authentication Error")
                               .setMessage("The email " + email + " is not authorized to access this application. Please contact an administrator.")
                               .setPositiveButton("OK", null)
                               .show();
                    }
                } else {
                    // Handle server error
                    Toast.makeText(Login.this, "Server error. Please try again later.", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                loadingDialog.dismiss();
                Toast.makeText(Login.this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void displayPasswordDialog(String email) {
        // First check if the user already has a password in the backend
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        AuthRequest checkRequest = new AuthRequest(email, "", "", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        
        // Show loading indicator
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        loadingBuilder.setTitle("Checking Email");
        loadingBuilder.setMessage("Please wait...");
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        loadingDialog.show();
        
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        checkCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                loadingDialog.dismiss();
                
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists()) {
                        // Check if user already has a password
                        if (authResponse.getUser() != null && !TextUtils.isEmpty(authResponse.getUser().getPassword())) {
                            // User already has a password, show message
                            AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                            builder.setTitle("Account Already Exists")
                                   .setMessage("This email already has an account. Please sign in with your password.")
                                   .setPositiveButton("OK", null)
                                   .show();
                            return;
                        }
                        
                        // User exists but no password, show password creation dialog
                        showPasswordRegistrationDialog(email);
                    } else {
                        // User doesn't exist, show error message
                        AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                        builder.setTitle("Authentication Error")
                               .setMessage("The email " + email + " is not authorized to access this application. Please contact an administrator.")
                               .setPositiveButton("OK", null)
                               .show();
                    }
                } else {
                    // Handle server error
                    Toast.makeText(Login.this, "Server error. Please try again later.", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                loadingDialog.dismiss();
                Toast.makeText(Login.this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void showPasswordRegistrationDialog(String email) {
        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.register_dialog, null);
        builder.setView(dialogView);
        
        // Find the views in the dialog
        TextView emailTextView = dialogView.findViewById(R.id.email_text);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.password_edit_text);
        TextInputEditText confirmPasswordEditText = dialogView.findViewById(R.id.confirm_password_edit_text);
        Button registerButton = dialogView.findViewById(R.id.register_button);
        
        // Set the email text
        emailTextView.setText("Email: " + email);
        
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
            
            // Update the user in the backend and save to local DB
            proceedWithPasswordUpdate(email, hashedPassword, dialog);
        });
        
        dialog.show();
    }
    
    private void updateUserPassword(String email, String password, AlertDialog dialog) {
        // First verify if the user exists in the database
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        
        // Create authentication request to check if user exists
        AuthRequest checkRequest = new AuthRequest(email, "", "", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        checkCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    if ("success".equals(authResponse.getStatus()) && authResponse.isExists()) {
                        // User exists, proceed with password update
                        proceedWithPasswordUpdate(email, password, dialog);
                    } else {
                        // User doesn't exist, show error message
                        Toast.makeText(Login.this, "Cannot set password. Email not authorized.", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        
                        AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                        builder.setTitle("Authentication Error")
                               .setMessage("The email " + email + " is not authorized to access this application. Please contact an administrator.")
                               .setPositiveButton("OK", null)
                               .show();
                    }
                } else {
                    // Handle server error
                    Toast.makeText(Login.this, "Server error. Please try again later.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Toast.makeText(Login.this, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
    }
    
    /**
     * Validates user credentials against the local database
     * @param email User email
     * @param hashedPassword Hashed password to check
     */
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
                Toast.makeText(this, "Database initialization failed. Checking with server...", Toast.LENGTH_SHORT).show();
                checkUserWithBackend(email, hashedPassword);
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
                            } else {
                                // Password doesn't match
                                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                            }
                        },
                        error -> {
                            loadingDialog.dismiss();
                            Toast.makeText(this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Database error", error);
                            
                            // Try authenticating with the backend as fallback
                            checkUserWithBackend(email, hashedPassword);
                        },
                        () -> {
                            // No user found with this email
                            loadingDialog.dismiss();
                            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                            
                            // Try authenticating with the backend as fallback
                            checkUserWithBackend(email, hashedPassword);
                        }
                    );
            } catch (Exception e) {
                loadingDialog.dismiss();
                Log.e(TAG, "Error querying database", e);
                Toast.makeText(this, "Database query error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                
                // Try authenticating with the backend as fallback
                checkUserWithBackend(email, hashedPassword);
            }
        } catch (Exception e) {
            loadingDialog.dismiss();
            Log.e(TAG, "Error in validateUserCredentials", e);
            Toast.makeText(this, "Authentication error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // Try authenticating with the backend as fallback
            checkUserWithBackend(email, hashedPassword);
        }
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
                        displayPasswordDialog(email);
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

    private void proceedWithPasswordUpdate(String email, String password, AlertDialog dialog) {
        // First check if the user already has a password in the backend
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        AuthRequest checkRequest = new AuthRequest(email, "", "", 
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
                
        Call<AuthResponse> checkCall = authService.authenticateUser(checkRequest);
        checkCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authResponse = response.body();
                    
                    // Check if user already has a password
                    if (authResponse.isExists() && authResponse.getUser() != null && 
                            !TextUtils.isEmpty(authResponse.getUser().getPassword())) {
                        dialog.dismiss();
                        // User already has password, show warning
                        AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                        builder.setTitle("Account Already Exists")
                               .setMessage("This email already has an account. Please sign in with your password.")
                               .setPositiveButton("OK", null)
                               .show();
                        return;
                    }
                    
                    // No existing password, proceed with update
                    performPasswordUpdate(email, password, dialog);
                } else {
                    // Server error, try update anyway
                    performPasswordUpdate(email, password, dialog);
                }
            }
            
            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                // Network error, try update anyway
                performPasswordUpdate(email, password, dialog);
            }
        });
    }
    
    private void performPasswordUpdate(String email, String password, AlertDialog dialog) {
        AuthService authService = RetrofitClient.getClient().create(AuthService.class);
        UpdateUserRequest updateRequest = new UpdateUserRequest(email, password);
        Call<UpdateUserResponse> call = authService.updateUser(updateRequest);
        
        call.enqueue(new Callback<UpdateUserResponse>() {
            @Override
            public void onResponse(Call<UpdateUserResponse> call, Response<UpdateUserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateUserResponse updateResponse = response.body();
                    
                    if ("success".equals(updateResponse.getStatus())) {
                        Toast.makeText(Login.this, "Password created successfully!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        
                        // First get user details from backend
                        fetchUserDetailsAndSaveToLocalDB(email, password);
                    } else if ("error".equals(updateResponse.getStatus())) {
                        // Handle specific error message from server
                        String errorMsg = updateResponse.getMessage() != null ? 
                                          updateResponse.getMessage() : 
                                          "Failed to update password. Please try again.";
                                          
                        AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                        builder.setTitle("Update Failed")
                               .setMessage(errorMsg)
                               .setPositiveButton("OK", null)
                               .show();
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
                        builder.setTitle("Update Failed")
                               .setMessage("Failed to update password. Please try again.")
                               .setPositiveButton("OK", null)
                               .show();
                    }
                } else {
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
            public void onFailure(Call<UpdateUserResponse> call, Throwable t) {
                Log.e(TAG, "Network error", t);
                
                String errorMessage = "Cannot connect to the server. ";
                
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
}