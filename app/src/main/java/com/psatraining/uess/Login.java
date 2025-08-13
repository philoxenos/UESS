package com.psatraining.uess;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.psatraining.uess.database.AppDatabase;
import com.psatraining.uess.model.User;
import com.psatraining.uess.Utility.CryptoHelper;
import com.psatraining.uess.Utility.QRUtility;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class Login extends AppCompatActivity {
    private static final String TAG = "Login";
    
    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;
    private Button googleSignInButton;
    private TextView forgotPasswordLink;
    private CompositeDisposable disposables = new CompositeDisposable();
    private GoogleSignInClient googleSignInClient;
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        // Initialize views
        emailInput = findViewById(R.id.email_edit_text);
        passwordInput = findViewById(R.id.password_edit_text);
        signInButton = findViewById(R.id.submit_button);
        googleSignInButton = findViewById(R.id.button);  // Google sign up button
        forgotPasswordLink = findViewById(R.id.textView5);  // Forgot Password link
        
        // Initialize database
        database = AppDatabase.getInstance(getApplicationContext());
        
        // Set up Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Set up click listeners
        signInButton.setOnClickListener(v -> handleManualSignIn());
        googleSignInButton.setOnClickListener(v -> handleGoogleSignIn());
        forgotPasswordLink.setOnClickListener(v -> showForgotPasswordDialog());
        
        // Set up the QR code icon click listener
        ImageView qrCodeIcon = findViewById(R.id.qr_code_icon);
        qrCodeIcon.setOnClickListener(v -> showQrCodeDialog());
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
    
    // Handle manual sign in with email and password
    private void handleManualSignIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if email exists
        disposables.add(database.userDao().getUserByEmail(email)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                user -> {
                    // User found, check password
                    String hashedPassword = CryptoHelper.hashPassword(password);
                    if (hashedPassword != null && hashedPassword.equals(user.getHashedPassword())) {
                        // Password matched, proceed to main app
                        Toast.makeText(this, "Welcome back, " + user.getName(), Toast.LENGTH_SHORT).show();
                        redirectToMainApp();
                    } else {
                        // Password incorrect
                        Toast.makeText(this, "Incorrect password. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                },
                throwable -> {
                    Log.e(TAG, "Error checking user credentials", throwable);
                    Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                },
                () -> {
                    // User not found
                    Toast.makeText(this, "Email not found. Please sign up first.", Toast.LENGTH_SHORT).show();
                }
            )
        );
    }
    
    // Handle Google Sign In
    private void handleGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }
    
    // Activity result launcher for Google Sign In
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                handleGoogleSignInResult(account);
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google Sign In failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
        }
    );
    
    // Process Google Sign In result
    private void handleGoogleSignInResult(GoogleSignInAccount account) {
        if (account == null) return;
        
        String email = account.getEmail();
        if (email == null) {
            Toast.makeText(this, "Could not get email from Google account", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if user exists in database
        disposables.add(database.userDao().checkEmailExists(email)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                count -> {
                    if (count > 0) {
                        // User exists, show welcome back message
                        disposables.add(database.userDao().getUserByEmail(email)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                user -> {
                                    Toast.makeText(this, "Welcome back, " + user.getName(), Toast.LENGTH_SHORT).show();
                                    redirectToMainApp();
                                },
                                throwable -> Log.e(TAG, "Error getting user details", throwable)
                            )
                        );
                    } else {
                        // New user, create account
                        createNewAccountFromGoogle(account);
                    }
                },
                throwable -> Log.e(TAG, "Error checking if email exists", throwable)
            )
        );
    }
    
    // Create new account from Google Sign In
    private void createNewAccountFromGoogle(GoogleSignInAccount account) {
        String email = account.getEmail();
        String name = account.getGivenName();
        String surname = account.getFamilyName();
        
        if (email == null || name == null) {
            Toast.makeText(this, "Could not get required information from Google account", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Generate random password (user won't need to enter it)
        String randomPassword = CryptoHelper.generateUserId();
        String hashedPassword = CryptoHelper.hashPassword(randomPassword);
        
        // Create new user
        User newUser = new User(
            CryptoHelper.generateUserId(),
            name,
            surname != null ? surname : "",
            email,
            hashedPassword,
            System.currentTimeMillis()
        );
        
        // Save to database
        disposables.add(database.userDao().insert(newUser)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                () -> {
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    redirectToMainApp();
                },
                throwable -> {
                    Log.e(TAG, "Error creating account", throwable);
                    Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                }
            )
        );
    }
    
    // Show dialog for forgot password flow
    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.forgot_password_dialog, null);
        builder.setView(dialogView);
        
        EditText emailField = dialogView.findViewById(R.id.email_edit_text);
        Button resetButton = dialogView.findViewById(R.id.next_button);
        
        AlertDialog dialog = builder.create();
        
        // Set rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        resetButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Check if email exists in database
            disposables.add(database.userDao().checkEmailExists(email)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    count -> {
                        if (count > 0) {
                            // Email found, show TOTP input dialog
                            dialog.dismiss();
                            showTOTPDialog(email);
                        } else {
                            // Email not found
                            Toast.makeText(this, "Email not found", Toast.LENGTH_SHORT).show();
                        }
                    },
                    throwable -> Log.e(TAG, "Error checking email", throwable)
                )
            );
        });
        
        dialog.show();
    }
    
    // Show dialog for TOTP verification
    private void showTOTPDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.totp_dialog, null);
        builder.setView(dialogView);
        
        EditText totpField = dialogView.findViewById(R.id.totp_edit_text);
        Button verifyButton = dialogView.findViewById(R.id.verify_button);
        
        AlertDialog dialog = builder.create();
        
        // Set rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        verifyButton.setOnClickListener(v -> {
            String totp = totpField.getText().toString().trim();
            if (totp.isEmpty()) {
                Toast.makeText(this, "Please enter the verification code", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // For demo purposes, assume valid TOTP if 6 digits
            // In a real app, validate with Google Authenticator or similar
            if (totp.length() == 6 && totp.matches("\\d+")) {
                dialog.dismiss();
                showNewPasswordDialog(email);
            } else {
                Toast.makeText(this, "Invalid code. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }
    
    // Show dialog for setting new password
    private void showNewPasswordDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.new_password_dialog, null);
        builder.setView(dialogView);
        
        EditText newPasswordField = dialogView.findViewById(R.id.new_password_input);
        EditText confirmPasswordField = dialogView.findViewById(R.id.confirm_password_input);
        Button saveButton = dialogView.findViewById(R.id.save_password_button);
        
        AlertDialog dialog = builder.create();
        
        // Set rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        saveButton.setOnClickListener(v -> {
            String newPassword = newPasswordField.getText().toString();
            String confirmPassword = confirmPasswordField.getText().toString();
            
            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please enter and confirm your new password", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Update password in database
            String hashedPassword = CryptoHelper.hashPassword(newPassword);
            disposables.add(database.userDao().updatePassword(email, hashedPassword)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        dialog.dismiss();
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                    },
                    throwable -> {
                        Log.e(TAG, "Error updating password", throwable);
                        Toast.makeText(this, "Error updating password: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                )
            );
        });
        
        dialog.show();
    }
    
    // Redirect to main app after successful authentication
    private void redirectToMainApp() {
        // In a real app, you would start your main activity here
        // For now, just show a toast
        Toast.makeText(this, "Redirecting to main app...", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}