package com.lunartag.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.lunartag.app.R;

import java.io.File;

public class SendService extends Service {

    private static final String TAG = "SendService";
    private static final String CHANNEL_ID = "SendServiceChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String EXTRA_FILE_PATH = "com.lunartag.app.EXTRA_FILE_PATH";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);

        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "File path was null or empty. Stopping service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        File imageFile = new File(filePath);
        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist at path: " + filePath);
            stopSelf();
            return START_NOT_STICKY;
        }

        // --- FIX: Create a "Clickable" Notification instead of auto-launching ---
        // Android 10+ blocks starting activities from background. 
        // We must use a High-Priority notification that the user taps to launch WhatsApp.

        // 1. Prepare the URI
        Uri imageUri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".fileprovider",
                imageFile
        );

        // 2. Create the Intent that opens WhatsApp (SAME as before)
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
        shareIntent.setPackage("com.whatsapp"); // Target WhatsApp specifically
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 3. Wrap it in a PendingIntent (This makes it "wait" for the click)
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                shareIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 4. Build the High-Priority Notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Scheduled Photo Ready")
                .setContentText("Tap here to send to WhatsApp Group")
                .setSmallIcon(R.drawable.ic_camera) // Your app icon
                .setContentIntent(pendingIntent) // Connect the click to the intent
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Pop up on screen
                .setCategory(NotificationCompat.CATEGORY_ALARM) // Ensure it rings through DND
                .setAutoCancel(true) // Dismiss when clicked
                .build();

        // 5. Show it immediately
        startForeground(NOTIFICATION_ID, notification);
        
        Log.d(TAG, "Notification posted. Waiting for user tap.");

        // We do NOT stopSelf() immediately. We leave the notification active.
        // The OS will eventually clean it up, or the user will click it.
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_HIGH is critical for the "Heads Up" banner to appear over other apps
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Scheduled Send Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setDescription("Alerts when a photo is ready to be sent via WhatsApp");
            serviceChannel.enableVibration(true);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // This is a started service, not a bound service.
        return null;
    }
}