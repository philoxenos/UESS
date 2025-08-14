package com.psatraining.uess;


import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


import com.psatraining.uess.database.AppDatabase;

import com.psatraining.uess.Utility.QRUtility;



public class Login extends AppCompatActivity {
    private static final String TAG = "Login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        // Initialize views
        EditText emailInput = findViewById(R.id.email_edit_text);
        EditText passwordInput = findViewById(R.id.password_edit_text);
        Button signInButton = findViewById(R.id.submit_button);
        Button googleSignInButton = findViewById(R.id.button);  // Google sign up button
        TextView forgotPasswordLink = findViewById(R.id.textView5);  // Forgot Password link

        // Initialize database
        AppDatabase database = AppDatabase.getInstance(getApplicationContext());

        // Set up click listeners
        signInButton.setOnClickListener(v -> {


        });
        googleSignInButton.setOnClickListener(v -> {


        });
        forgotPasswordLink.setOnClickListener(v -> {

        });
        
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
}