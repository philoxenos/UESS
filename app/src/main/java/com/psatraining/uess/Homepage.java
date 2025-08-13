package com.psatraining.uess;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.psatraining.uess.Authentication.AuthManager;
import com.psatraining.uess.Authentication.GoogleAuthProvider;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class Homepage extends AppCompatActivity {

    private TextView welcomeText;
    private TextView roleText;

    private AuthManager authManager;
    private GoogleAuthProvider googleAuthProvider;
    private CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.homepage_activity);

        // Initialize views
        welcomeText = findViewById(R.id.welcome_text);
        roleText = findViewById(R.id.role_text);

        // Initialize auth components
        authManager = new AuthManager(getApplicationContext());
        googleAuthProvider = new GoogleAuthProvider(this);

        // Load user data
        loadUserProfile();

        // Handle deep links if needed
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            // Handle deep links if needed
            String scheme = intent.getData().getScheme();
            String host = intent.getData().getHost();

            if ("uess".equals(scheme) && "auth-callback".equals(host)) {
                boolean success = intent.getData().getBooleanQueryParameter("success", false);
                if (success) {
                    // Registration/auth was successful, refresh user data
                    loadUserProfile();
                }
            }
        }
    }

    private void loadUserProfile() {
        disposables.add(authManager.getCurrentUser()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        user -> {
                            welcomeText.setText("Welcome, " + user.getName());
                            roleText.setText("Roles: " + String.join(", ", user.getRoles()));
                        },
                        error -> {
                            Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                            logout();
                        }
                ));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        disposables.add(authManager.logout()
                .andThen(io.reactivex.rxjava3.core.Completable.fromAction(googleAuthProvider::signOut))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::navigateToLogin,
                        error -> navigateToLogin()
                ));
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}