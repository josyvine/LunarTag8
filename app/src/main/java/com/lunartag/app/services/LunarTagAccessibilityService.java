package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * The Automation Brain.
 * UPDATED: Fixed Scrolling, Two-Step Clone Detection, and Full-Auto Logic.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilityService";

    // --- Shared Memory Constants ---
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    
    // Keys
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_AUTO_MODE = "automation_mode"; // "semi" or "full"
    private static final String KEY_TARGET_APP_LABEL = "target_app_label"; // e.g., "WhatsApp" or "WhatsApp (Clone)"

    private boolean isScrolling = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);

        if (!isJobPending) {
            return;
        }

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String targetGroupName = prefs.getString(KEY_TARGET_GROUP, "");
        String targetAppLabel = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");
        
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        CharSequence packageNameSeq = event.getPackageName();
        String packageName = (packageNameSeq != null) ? packageNameSeq.toString().toLowerCase() : "";

        // ---------------------------------------------------------
        // STEP 1: HANDLE NOTIFICATION (Full Automatic Only)
        // ---------------------------------------------------------
        if (mode.equals("full") && event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (packageName.equals(getPackageName())) {
                Parcelable data = event.getParcelableData();
                if (data instanceof Notification) {
                    showLiveLog("Auto: Clicking Notification...");
                    try {
                        Notification notification = (Notification) data;
                        if (notification.contentIntent != null) {
                            notification.contentIntent.send();
                            return; 
                        }
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // STEP 2: HANDLE APP SELECTION (System Share Sheet)
        // ---------------------------------------------------------
        // If we are NOT in WhatsApp yet, we try to find the app.
        // We look for "WhatsApp (Clone)" first, then "WhatsApp" (Parent).
        if (mode.equals("full") && !packageName.contains("whatsapp")) {
            
            // 1. Try to find the exact Target App Label (e.g. "WhatsApp (Clone)")
            if (clickNodeByText(rootNode, targetAppLabel)) {
                showLiveLog("Auto: Selected '" + targetAppLabel + "'");
                return;
            }

            // 2. CLONE LOGIC: If exact match not found, check if it's hidden inside "WhatsApp" parent
            // Only do this if the target contains "Clone" or "Dual" but we only see "WhatsApp"
            if (targetAppLabel.toLowerCase().contains("clone") || targetAppLabel.toLowerCase().contains("dual")) {
                 if (clickNodeByText(rootNode, "WhatsApp")) {
                     showLiveLog("Auto: Clicking Parent 'WhatsApp' to find Clone...");
                     return;
                 }
            }
            
            // 3. If neither found, try scrolling the Share Sheet
            // (System share sheets are often scrollable)
            performScroll(rootNode);
        }

        // ---------------------------------------------------------
        // STEP 3: HANDLE WHATSAPP (Chat List & Send Screen)
        // ---------------------------------------------------------
        if (packageName.contains("whatsapp")) {
            
            // A. Priority: Always look for "Send" button (Paper Plane) FIRST.
            // This handles the final preview screen.
            if (clickNodeByContentDescription(rootNode, "Send") || clickNodeByText(rootNode, "Send")) {
                showLiveLog("Auto: 'Send' Clicked. Job Done.");
                prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                return; 
            }

            // B. Secondary: Find the Group Name in the list
            if (!targetGroupName.isEmpty()) {
                // 1. Try finding the text directly
                if (clickNodeByText(rootNode, targetGroupName)) {
                    showLiveLog("Auto: Found Group '" + targetGroupName + "'");
                    return;
                }

                // 2. SCROLL LOGIC: If text not found, we must scroll down
                performScroll(rootNode);
            }
        }
        
        // Clean up resource
        // rootNode.recycle(); // Note: Some Android versions recycle auto, but explicit is safer
    }

    /**
     * Helper: Finds a node by text and clicks it (or its parent).
     */
    private boolean clickNodeByText(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (performClick(node)) return true;
            }
        }
        return false;
    }

    /**
     * Helper: Finds a node by Content Description (essential for Image Buttons like "Send").
     */
    private boolean clickNodeByContentDescription(AccessibilityNodeInfo root, String desc) {
        return recursiveSearchAndClick(root, desc);
    }

    private boolean recursiveSearchAndClick(AccessibilityNodeInfo node, String desc) {
        if (node == null) return false;
        
        if (node.getContentDescription() != null && 
            node.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return performClick(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveSearchAndClick(node.getChild(i), desc)) return true;
        }
        return false;
    }

    /**
     * Helper: Checks if a node is clickable, if not checks parent. Then clicks.
     */
    private boolean performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        while (target != null) {
            if (target.isClickable()) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            target = target.getParent();
        }
        return false;
    }

    /**
     * Helper: Finds a scrollable container and scrolls forward.
     * Uses a boolean flag/timestamp to prevent spamming scrolls too fast.
     */
    private void performScroll(AccessibilityNodeInfo root) {
        if (isScrolling) return; // Debounce

        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable != null) {
            isScrolling = true;
            showLiveLog("Auto: Scrolling...");
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            
            // Reset flag after a delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 1000);
        }
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findScrollableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    private void showLiveLog(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service Interrupted");
    }
}