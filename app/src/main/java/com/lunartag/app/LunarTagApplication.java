package com.lunartag.app;

import android.app.Application;

import com.lunartag.app.firebase.FirebaseManager;

/**
 * The custom Application class for Lunar Tag.
 * This is the entry point of the application process.
 * Its main responsibility is to initialize components that are needed globally,
 * such as our dynamic Firebase configuration.
 */
public class LunarTagApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize our custom FirebaseManager.
        // This manager will attempt to load a user-provided google-services.json first,
        // and if it doesn't find one, it will fall back to the one bundled with the app.
        // This single line of code enables the dynamic Firebase backend feature.
        FirebaseManager.initialize(this);
    }
}