package com.psatraining.uess.Utility;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.ImageView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.HashMap;
import java.util.Map;

public class QRUtility {
    private static final String TAG = "QRUtility";

    public static void showDeviceQRCode(Context context, ImageView qrImageView) {
        if (context == null || qrImageView == null) {
            Log.e(TAG, "Context or ImageView is null");
            return;
        }

        // Get device info
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceModel = Build.MODEL;
        String deviceBrand = Build.BRAND;
        String deviceOS = "Android " + Build.VERSION.RELEASE;

        String qrContent =
                "Device ID: " + deviceId + "\n" +
                        "Model: " + deviceModel + "\n" +
                        "OS: " + deviceOS + "\n" +
                        "Brand: " + deviceBrand;

        int size = 400; // width and height of QR
        try {
            // Try the more reliable BarcodeEncoder method first
            try {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Map<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.MARGIN, 1);

                BitMatrix matrix = new MultiFormatWriter().encode(
                        qrContent,
                        BarcodeFormat.QR_CODE,
                        size,
                        size,
                        hints
                );

                Bitmap qrBitmap = barcodeEncoder.createBitmap(matrix);
                qrImageView.setImageBitmap(qrBitmap);
                Log.d(TAG, "QR code generated successfully with BarcodeEncoder");
            } catch (Exception e) {
                // Fall back to manual method if the first one fails
                Log.w(TAG, "BarcodeEncoder failed, trying manual method: " + e.getMessage());
                Bitmap qrBitmap = generateTransparentQRCode(context, qrContent, size);
                qrImageView.setImageBitmap(qrBitmap);
                Log.d(TAG, "QR code generated successfully with manual method");
            }
        } catch (WriterException e) {
            Log.e(TAG, "Failed to generate QR code: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Bitmap generateTransparentQRCode(Context context, String content, int size) throws WriterException {
        // Check if dark mode is enabled
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1); // minimal margin

        BitMatrix bitMatrix = new MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size, size, hints
        );

        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ?
                        (isDarkMode ? Color.WHITE : Color.BLACK) :
                        Color.TRANSPARENT);
            }
        }
        return bitmap;
    }
}