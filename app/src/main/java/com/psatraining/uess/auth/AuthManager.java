package com.psatraining.uess.auth;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.psatraining.uess.R;

import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.psatraining.uess.database.AppDatabase;
import com.psatraining.uess.model.User;
import com.psatraining.uess.Utility.CryptoHelper;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AuthManager {
    private static final String TAG = "AuthManager";
    private final Context context;
    private final AppDatabase database;
    private final CompositeDisposable disposable = new CompositeDisposable();
    private GoogleSignInClient googleSignInClient;
    
    // Interface for authentication callbacks
    public interface AuthCallback {
        void onSuccess(User user);
        void onError(String errorMessage);
    }
    
    // Interface for email check callbacks
    public interface EmailCheckCallback {
        void onExistingUser();
        void onNewUser(String email);
    }
    
    public AuthManager(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        
        // Configure Google Sign-In with Web client ID
        String clientId = context.getString(R.string.google_client_id);
        Log.d(TAG, "Using client ID: " + clientId);
        
        try {
            // Use minimal Google Sign-In configuration for testing
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail() // Only request email for now
                    .build();
                    
            googleSignInClient = GoogleSignIn.getClient(context, gso);
            Log.d(TAG, "Google Sign-In client created successfully");
            
            // Check for existing silent sign-in
            GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(context);
            if (lastAccount != null) {
                Log.d(TAG, "Found previously signed-in Google account: " + lastAccount.getEmail());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Google Sign-In", e);
        }
    }
    
    /**
     * Check if email exists in local database
     * @param email Email to check
     * @param callback Callback with result
     */
    public void checkEmailExists(String email, EmailCheckCallback callback) {
        disposable.add(
            database.userDao().checkEmailExists(email)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    count -> {
                        if (count > 0) {
                            callback.onExistingUser();
                        } else {
                            callback.onNewUser(email);
                        }
                    },
                    throwable -> {
                        Log.e(TAG, "Error checking email: ", throwable);
                        // Default to new user on error
                        callback.onNewUser(email);
                    }
                )
        );
    }
    
    /**
     * Register a new user with email and password
     * @param name User's name
     * @param surname User's surname
     * @param email User's email
     * @param password Raw password (will be hashed)
     * @param callback Callback with result
     */
    public void registerUser(String name, String surname, String email, String password, AuthCallback callback) {
        // Hash the password
        String hashedPassword = CryptoHelper.hashPassword(password);
        if (hashedPassword == null) {
            callback.onError("Password hashing failed");
            return;
        }
        
        // Create user object
        User newUser = new User(
                CryptoHelper.generateUserId(),
                name,
                surname,
                email,
                hashedPassword,
                System.currentTimeMillis()
        );
        
        // Store in database
        disposable.add(
            database.userDao().insert(newUser)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> callback.onSuccess(newUser),
                    throwable -> {
                        Log.e(TAG, "Error registering user: ", throwable);
                        callback.onError("Registration failed: " + throwable.getMessage());
                    }
                )
        );
    }
    
    /**
     * Login with email and password
     * @param email User's email
     * @param password Raw password (will be hashed for comparison)
     * @param callback Callback with result
     */
    public void loginWithEmailAndPassword(String email, String password, AuthCallback callback) {
        // Hash the password for comparison
        String hashedPassword = CryptoHelper.hashPassword(password);
        if (hashedPassword == null) {
            callback.onError("Password hashing failed");
            return;
        }
        
        // Find user by email and check password
        final String finalHashedPassword = hashedPassword;
        disposable.add(
            database.userDao().getUserByEmail(email)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    user -> {
                        if (finalHashedPassword.equals(user.getHashedPassword())) {
                            callback.onSuccess(user);
                        } else {
                            callback.onError("Invalid password");
                        }
                    },
                    throwable -> {
                        Log.e(TAG, "Error during login: ", throwable);
                        callback.onError("Login failed: " + throwable.getMessage());
                    },
                    () -> callback.onError("User not found")
                )
        );
    }
    
    /**
     * Reset password for a user
     * @param email User's email
     * @param newPassword New raw password (will be hashed)
     * @param callback Callback with result
     */
    public void resetPassword(String email, String newPassword, AuthCallback callback) {
        // Hash the new password
        String hashedPassword = CryptoHelper.hashPassword(newPassword);
        if (hashedPassword == null) {
            callback.onError("Password hashing failed");
            return;
        }
        
        // Update password in database
        disposable.add(
            database.userDao().updatePassword(email, hashedPassword)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        // Get updated user for callback
                        disposable.add(
                            database.userDao().getUserByEmail(email)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                    callback::onSuccess,
                                    throwable -> {
                                        Log.e(TAG, "Error getting updated user: ", throwable);
                                        callback.onError("Password reset but user fetch failed");
                                    },
                                    () -> callback.onError("Password reset but user not found")
                                )
                        );
                    },
                    throwable -> {
                        Log.e(TAG, "Error resetting password: ", throwable);
                        callback.onError("Password reset failed: " + throwable.getMessage());
                    }
                )
        );
    }
    
    /**
     * Handle the result from Google Sign-In
     */
    public void handleGoogleSignInResult(Intent data, AuthCallback callback) {
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            if (task.isSuccessful()) {
                GoogleSignInAccount account = task.getResult(ApiException.class);

                if (account == null) {
                    Log.e(TAG, "Google Sign-In successful but account is null");
                    callback.onError("Failed to get account information");
                    return;
                }

                String email = account.getEmail();
                if (email == null || email.isEmpty()) {
                    Log.e(TAG, "Google account doesn't have an email");
                    callback.onError("Google account doesn't have an associated email");
                    return;
                }

                Log.d(TAG, "Google Sign-In successful for email: " + email);

                // Check if email exists in database
                checkEmailExists(email, new EmailCheckCallback() {
                    @Override
                    public void onExistingUser() {
                        Log.d(TAG, "Existing user found with email: " + email);
                        // User already exists, try to login with their Google account info
                        getUserFromDatabase(email)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                callback::onSuccess,
                                throwable -> {
                                    Log.e(TAG, "Error getting user from database", throwable);
                                    callback.onError("Error getting user: " + throwable.getMessage());
                                }
                            );
                    }

                    @Override
                    public void onNewUser(String email) {
                        Log.d(TAG, "New user with email: " + email + ", creating account");
                        // Create new user from Google account info
                        String name = account.getGivenName() != null ? account.getGivenName() : "User";
                        String surname = account.getFamilyName() != null ? account.getFamilyName() : "";

                        // Generate a random password - user won't need to know this as they'll use Google login
                        String randomPassword = CryptoHelper.generateUserId(); // Using UUID as random password
                        registerUser(name, surname, email, randomPassword, callback);
                    }
                });
            } else {
                Log.e(TAG, "Google Sign-In task was not successful");
                if (task.getException() != null) {
                    Log.e(TAG, "Exception in Google Sign-In", task.getException());
                    callback.onError("Google Sign-In failed: " + task.getException().getMessage());
                } else {
                    callback.onError("Google Sign-In failed with unknown error");
                }
            }
        } catch (ApiException e) {
            Log.e(TAG, "Google sign in failed with ApiException", e);
            String errorMessage;
            switch (e.getStatusCode()) {
                case 12500: // SIGN_IN_FAILED
                    errorMessage = "Sign-In failed - please update Google Play Services";
                    break;
                case 12501: // SIGN_IN_CANCELLED
                    errorMessage = "Sign-In was cancelled";
                    break;
                case 12502: // SIGN_IN_CURRENTLY_IN_PROGRESS
                    errorMessage = "Sign-In is already in progress";
                    break;
                default:
                    errorMessage = "Google sign in failed: " + e.getStatusCode();
            }
            callback.onError(errorMessage);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception during Google Sign-In", e);
            callback.onError("Unexpected error during sign-in: " + e.getMessage());
        }
    }
    
    /**
     * Get a user object from database by email
     */
    private Single<User> getUserFromDatabase(String email) {
        return database.userDao().getUserByEmail(email)
                .toSingle();
    }
    
    /**
     * Start Google Sign-In flow
     */
    public void signInWithGoogle(FragmentActivity activity, ActivityResultLauncher<Intent> launcher) {
        try {
            // For debugging purposes, check if we have a last signed-in account
            GoogleSignInAccount lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity);
            if (lastSignedInAccount != null) {
                Log.d(TAG, "Last signed-in account: " + lastSignedInAccount.getEmail());
            } else {
                Log.d(TAG, "No last signed-in Google account found");
            }
            
            // Skip sign-out for now to simplify the flow
            Log.d(TAG, "Starting Google Sign-In flow directly");
            Intent signInIntent = googleSignInClient.getSignInIntent();
            launcher.launch(signInIntent);
            
        } catch (Exception e) {
            Log.e(TAG, "Error launching Google Sign-In", e);
        }
    }
    
    /**
     * Clear resources when no longer needed
     */
    public void dispose() {
        if (!disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}
