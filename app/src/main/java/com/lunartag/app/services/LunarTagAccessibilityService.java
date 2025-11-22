package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * FINAL FIX: "PASSIVE-FIRST" ARCHITECTURE
 * The robot is disabled by default. It only wakes up when specific triggers happen.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_AUTO_MODE = "automation_mode"; // "semi" or "full"
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_TARGET_APP_LABEL = "target_app_label";

    // TIMERS
    private long lastNotificationTime = 0;
    private static final long SHARE_SHEET_TIMEOUT = 5000; // Robot sleeps again after 5 seconds

    private boolean isScrolling = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                          AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. SAFETY: If no job is pending, do absolutely nothing.
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_JOB_PENDING, false)) return;

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String currentPkg = (event.getPackageName() != null) ? event.getPackageName().toString().toLowerCase() : "";

        // ============================================================
        // TRIGGER 1: NOTIFICATION (The "Start" Button)
        // ============================================================
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (mode.equals("full") && currentPkg.contains(getPackageName())) {
                Parcelable data = event.getParcelableData();
                if (data instanceof Notification) {
                    try {
                        // Wake up the robot logic for the next 5 seconds
                        lastNotificationTime = System.currentTimeMillis();
                        showDebugToast("🤖 Notif Clicked. Waiting for Share Sheet...");
                        
                        ((Notification) data).contentIntent.send();
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            return; // Don't do anything else during a notification event
        }

        // ============================================================
        // LOGIC: DETERMINE IF WE SHOULD ACT
        // ============================================================
        
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // SCENARIO A: WE ARE INSIDE WHATSAPP
        if (currentPkg.contains("whatsapp")) {
            handleWhatsAppLogic(root, prefs);
            return;
        }

        // SCENARIO B: WE ARE LOOKING FOR "WHATSAPP" IN SHARE LIST (Full Auto Only)
        // STRICT RULE: We only look for this if a notification was clicked < 5 seconds ago.
        // This prevents clicking the Home Screen icon.
        if (mode.equals("full")) {
            long timeSinceNotif = System.currentTimeMillis() - lastNotificationTime;
            
            if (timeSinceNotif < SHARE_SHEET_TIMEOUT) {
                // We are in the "Safe Zone" (5 seconds after notification).
                // It is safe to assume we are in the Share Sheet.
                handleShareSheetLogic(root, prefs);
            } else {
                // TIMEOUT EXPIRED.
                // We are likely on the Home Screen or IDLE.
                // DO NOTHING. DO NOT SCAN. DO NOT CLICK.
            }
        }
    }

    // -------------------------------------------------------------------------
    // LOGIC HANDLERS
    // -------------------------------------------------------------------------

    private void handleShareSheetLogic(AccessibilityNodeInfo root, SharedPreferences prefs) {
        String targetApp = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");

        // 1. Click App Name
        if (scanAndClick(root, targetApp)) return;

        // 2. Clone Support
        if (targetApp.toLowerCase().contains("clone") && scanAndClick(root, "WhatsApp")) return;

        // 3. Scroll (Only scroll if we are in the active 5-second window)
        performScroll(root);
    }

    private void handleWhatsAppLogic(AccessibilityNodeInfo root, SharedPreferences prefs) {
        String targetGroup = prefs.getString(KEY_TARGET_GROUP, "");

        // 1. Try to Click SEND (Paper Plane)
        if (scanAndClickContentDesc(root, "Send")) {
            showDebugToast("🤖 Done.");
            prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply(); // STOP JOB
            return;
        }

        // 2. Search for Group
        if (!targetGroup.isEmpty()) {
            if (scanAndClick(root, targetGroup)) return;
            if (scanListItemsManually(root, targetGroup)) return;
            performScroll(root);
        }
    }

    // -------------------------------------------------------------------------
    // STANDARD HELPERS (No Changes Needed Here)
    // -------------------------------------------------------------------------

    private boolean scanAndClick(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (performClick(node)) return true;
            }
        }
        return false;
    }

    private boolean scanAndClickContentDesc(AccessibilityNodeInfo root, String desc) {
        if (root == null) return false;
        if (root.getContentDescription() != null && 
            root.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return performClick(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanAndClickContentDesc(root.getChild(i), desc)) return true;
        }
        return false;
    }

    private boolean scanListItemsManually(AccessibilityNodeInfo root, String targetText) {
        if (root == null) return false;
        if (root.getClassName() != null && 
           (root.getClassName().toString().contains("RecyclerView") || 
            root.getClassName().toString().contains("ListView") ||
            root.getClassName().toString().contains("ViewGroup"))) {
            for (int i = 0; i < root.getChildCount(); i++) {
                if (recursiveCheck(root.getChild(i), targetText)) return true;
            }
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanListItemsManually(root.getChild(i), targetText)) return true;
        }
        return false;
    }

    private boolean recursiveCheck(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;
        if ((node.getText() != null && node.getText().toString().toLowerCase().contains(target.toLowerCase())) ||
            (node.getContentDescription() != null && node.getContentDescription().toString().toLowerCase().contains(target.toLowerCase()))) {
            return performClick(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveCheck(node.getChild(i), target)) return true;
        }
        return false;
    }

    private boolean performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        int attempts = 0;
        while (target != null && attempts < 6) {
            if (target.isClickable()) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            target = target.getParent();
            attempts++;
        }
        return false;
    }

    private void performScroll(AccessibilityNodeInfo root) {
        if (isScrolling) return;
        AccessibilityNodeInfo scrollable = findScrollable(root);
        if (scrollable != null) {
            isScrolling = true;
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 1500);
        }
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo res = findScrollable(node.getChild(i));
            if (res != null) return res;
        }
        return null;
    }

    private void showDebugToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    @Override public void onInterrupt() {}
}