package com.lunartag.app.receivers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.lunartag.app.R;

import java.io.File;

/**
 * The New "Doorbell" Receiver.
 * Replaces SendService to bypass Android 12+ Background Restrictions.
 * Allows user to choose between WhatsApp / Business / Clones.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    
    // Key to retrieve file path (Must match Scheduler)
    public static final String EXTRA_FILE_PATH = "com.lunartag.app.EXTRA_FILE_PATH";

    // Settings Prefs (To read "Love" group name)
    private static final String PREFS_SETTINGS = "LunarTagSettings";
    private static final String KEY_WHATSAPP_GROUP = "whatsapp_group";

    // Bridge Prefs (To write commands for the Robot)
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";

    private static final String CHANNEL_ID = "SendServiceChannel"; 
    private static final int NOTIFICATION_ID = 999;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm Received! Waking up...");
        // Live Log: Prove the alarm fired
        Toast.makeText(context, "LunarTag: Scheduled Time Reached!", Toast.LENGTH_LONG).show();

        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);

        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "No file path provided in Alarm Intent.");
            return;
        }

        // 1. Validate File & Get URI (Handles both SD Card & Internal)
        Uri imageUri = null;
        try {
            if (filePath.startsWith("content://")) {
                // Custom Folder (SD Card)
                imageUri = Uri.parse(filePath);
            } else {
                // Internal Storage
                File file = new File(filePath);
                if (!file.exists()) {
                    Toast.makeText(context, "Error: Photo file missing!", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "File missing at: " + filePath);
                    return;
                }
                // Secure File Provider URI
                imageUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        file
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "URI Parse Error: " + e.getMessage());
            return;
        }

        // 2. Arm the Accessibility Bridge (So the robot knows what to do)
        armAccessibilityService(context);

        // 3. Create the Notification (The "Doorbell")
        showNotification(context, imageUri);
    }

    /**
     * Writes the Target Group Name to persistent memory so the
     * Accessibility Service can read it whenever WhatsApp finally opens.
     */
    private void armAccessibilityService(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String groupName = settings.getString(KEY_WHATSAPP_GROUP, "");

        if (groupName != null && !groupName.isEmpty()) {
            SharedPreferences accessPrefs = context.getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
            accessPrefs.edit()
                    .putString(KEY_TARGET_GROUP, groupName)
                    .putBoolean(KEY_JOB_PENDING, true)
                    .apply();
            Log.d(TAG, "Bridge Armed for Group: " + groupName);
        } else {
            Toast.makeText(context, "Warning: Set WhatsApp Group Name in Settings!", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Posts the high-priority notification.
     * Uses Intent.createChooser() to allow selecting Clone Apps.
     */
    private void showNotification(Context context, Uri imageUri) {
        createNotificationChannel(context);

        // A. The Share Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // NOTE: No 'setPackage' here. This enables the Multi-App Selector.

        // B. The Chooser Intent (Forces the "Select App" menu)
        Intent chooserIntent = Intent.createChooser(shareIntent, "Select WhatsApp to Send...");
        
        // C. The PendingIntent (Waiting for user tap)
        // Use System.currentTimeMillis() as ID to ensure unique pending intents
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(), 
                chooserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // D. The Notification
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_camera) // Make sure you have an icon
                .setContentTitle("Photo Ready to Send")
                .setContentText("Tap to choose WhatsApp & Auto-Send")
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads up!
                .setCategory(NotificationCompat.CATEGORY_ALARM) // Bypass DND if possible
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Remove when clicked
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
            Log.d(TAG, "Notification Posted. Waiting for user selection.");
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Scheduled Sends",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for scheduled photo uploads");
            channel.enableVibration(true);
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
                          }
