package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * The Automation Engine.
 * UPDATED: Prioritizes "Send" button to prevent infinite looping.
 * Includes Live Log (Toasts) to visualize every step of the automation.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilityService";

    // --- Shared Memory Constants (Must match AlarmReceiver) ---
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. Expanded Safety Check: Allow Standard, Business, and Clones
        CharSequence packageName = event.getPackageName();
        if (packageName == null || !packageName.toString().toLowerCase().contains("whatsapp")) {
            // If the active app is not some version of WhatsApp, ignore it.
            return;
        }

        // 2. Memory Check: Do we have an order to execute?
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);
        String targetGroupName = prefs.getString(KEY_TARGET_GROUP, null);

        if (!isJobPending) {
            // Silent exit (No job active), to avoid spamming toast messages.
            return;
        }

        if (targetGroupName == null || targetGroupName.isEmpty()) {
            showLiveLog("Error: Auto-Send active but No Group Name found!");
            // Cancel the bad job to prevent looping error
            prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            return;
        }

        // --- PHASE 1 (CRITICAL FIX): Check for "Send" Button FIRST ---
        // We look for the send button (Paper Airplane) immediately.
        // If found, we are already inside the chat, so we send and finish.
        List<AccessibilityNodeInfo> sendButtonNodes = rootNode.findAccessibilityNodeInfosByText("Send");

        if (sendButtonNodes != null && !sendButtonNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : sendButtonNodes) {
                if (node.isClickable()) {
                    showLiveLog("Auto: Found 'Send' Button. Clicking...");
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                    // --- JOB COMPLETE: Update Memory ---
                    // This is the specific fix for the loop. We turn the job OFF immediately.
                    prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                    showLiveLog("Auto-Send Complete! Job Cleared.");

                    rootNode.recycle();
                    return; // STOP HERE. Do not look for group name.
                }
            }
        }

        // --- PHASE 2: Find the Target Group and Click It ---
        // Only run this if we DIDN'T find the Send button (meaning we are still on the home screen).
        List<AccessibilityNodeInfo> groupNodes = rootNode.findAccessibilityNodeInfosByText(targetGroupName);
        if (groupNodes != null && !groupNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : groupNodes) {
                AccessibilityNodeInfo parent = node.getParent();
                while (parent != null) {
                    if (parent.isClickable()) {
                        showLiveLog("Auto: Found Group '" + targetGroupName + "'. Clicking...");
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                        // Clean up and wait for the screen to change (next event will trigger Phase 1)
                        rootNode.recycle();
                        return;
                    }
                    parent = parent.getParent();
                }
            }
        }

        // Clean up to prevent memory leaks
        rootNode.recycle();
    }

    /**
     * Live Log Helper: Shows visual confirmation of background actions on screen.
     */
    private void showLiveLog(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted.");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Visual confirmation that user successfully enabled the service
        showLiveLog("LunarTag Automation Ready (Service Connected)");
        Log.d(TAG, "LunarTag Accessibility Service Connected.");
    }
}