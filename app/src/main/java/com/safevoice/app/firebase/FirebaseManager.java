package com.safevoice.app.firebase;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manages the dynamic initialization of the Firebase backend.
 * This class allows the app to switch its Firebase project at runtime
 * by loading a user-provided google-services.json file.
 */
public class FirebaseManager {

    private static final String TAG = "FirebaseManager";
    private static final String USER_CONFIG_FILENAME = "user_google_services.json";

    /**
     * Initializes Firebase for the entire application.
     * It first checks for a user-provided configuration file in the app's private storage.
     * If found, it initializes Firebase using that configuration.
     * If not found, it falls back to the default google-services.json bundled with the APK.
     *
     * @param context The application context.
     */
    public static void initialize(Context context) {
        // Only initialize if no FirebaseApp has been initialized yet.
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseOptions options = null;
            File userConfigFile = new File(context.getFilesDir(), USER_CONFIG_FILENAME);

            if (userConfigFile.exists()) {
                Log.d(TAG, "User-provided Firebase config found. Initializing...");
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(userConfigFile);
                    options = FirebaseOptions.fromStream(fis);
                    FirebaseApp.initializeApp(context, options);
                    Log.d(TAG, "Firebase initialized successfully with USER config.");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read user-provided Firebase config. Falling back to default.", e);
                    // If parsing the user file fails, fall back to default.
                    initializeAppWithDefault(context);
                } finally {
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing file input stream.", e);
                        }
                    }
                }
            } else {
                Log.d(TAG, "No user-provided Firebase config found. Initializing with default.");
                initializeAppWithDefault(context);
            }
        } else {
            Log.d(TAG, "Firebase already initialized.");
        }
    }

    /**
     * Helper method to initialize Firebase using the default bundled configuration.
     * @param context The application context.
     */
    private static void initializeAppWithDefault(Context context) {
        try {
            // FirebaseOptions.fromResource() is the standard way to load the default config
            FirebaseApp.initializeApp(context);
            Log.d(TAG, "Firebase initialized successfully with DEFAULT config.");
        } catch (Exception e) {
            Log.e(TAG, "FATAL: Could not initialize Firebase with default config.", e);
        }
    }

    /**
     * Saves a new google-services.json file provided by the user to the app's private storage.
     *
     * @param context The application context.
     * @param inputStream The InputStream from the user-selected file.
     * @return true if the file was saved successfully, false otherwise.
     */
    public static boolean saveUserFirebaseConfig(Context context, InputStream inputStream) {
        File userConfigFile = new File(context.getFilesDir(), USER_CONFIG_FILENAME);
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(userConfigFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Log.d(TAG, "Successfully saved user Firebase config to: " + userConfigFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving user Firebase config.", e);
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams after saving config.", e);
            }
        }
    }
}
