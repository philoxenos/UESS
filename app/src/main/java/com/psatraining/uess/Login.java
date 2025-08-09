package com.psatraining.uess;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ImageView;
import com.psatraining.uess.Utility.QRUtility;



public class Login extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView qrImageView = findViewById(R.id.qrcode);
        QRUtility.showDeviceQRCode(this, qrImageView);
    }
}