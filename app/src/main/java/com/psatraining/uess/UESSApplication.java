package com.psatraining.uess;

import android.app.Application;

import com.psatraining.uess.Api.ApiClient;

public class UESSApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the ApiClient with application context
        ApiClient.init(this);
    }
}