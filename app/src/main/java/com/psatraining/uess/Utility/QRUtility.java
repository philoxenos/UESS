package com.psatraining.uess.Utility;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.widget.ImageView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.Hashtable;

public class QRUtility {

    public static void showDeviceQRCode(Context context, ImageView qrImageView) {
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
            Bitmap qrBitmap = generateTransparentQRCode(qrContent, size);
            qrImageView.setImageBitmap(qrBitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    public static Bitmap generateTransparentQRCode(String content, int size) throws WriterException {
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.MARGIN, 1); // minimal margin

        BitMatrix bitMatrix = new MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size, size, hints
        );

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        // Fill with transparent (optional, as default is transparent)
        bitmap.eraseColor(Color.TRANSPARENT);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.TRANSPARENT);
            }
        }
        return bitmap;
    }
}
