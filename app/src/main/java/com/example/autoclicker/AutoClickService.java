package com.example.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AutoClickService extends AccessibilityService {
    private static final String FLEETLERY_PACKAGE = "com.fleetlery.driver"; // Replace with actual package name
    private boolean isRunning = false;
    private Handler handler;
    private Runnable clickRunnable;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Logger.log(this, "AutoClickService connected");
        handler = new Handler(Looper.getMainLooper());
        clickRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    performContinuousAutoClick();
                    handler.postDelayed(this, 10); // Every 10ms for millisecond precision
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "START".equals(intent.getAction())) {
            isRunning = true;
            handler.post(clickRunnable);
            Logger.log(this, "AutoClickService started");
        } else if (intent != null && "STOP".equals(intent.getAction())) {
            isRunning = false;
            handler.removeCallbacks(clickRunnable);
            Logger.log(this, "AutoClickService stopped");
        }
        return START_STICKY; // Ensures service restarts if killed
    }

    private void performContinuousAutoClick() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && FLEETLERY_PACKAGE.equals(root.getPackageName())) {
            Logger.log(this, "Fleetlery app detected in focus");
            AccessibilityNodeInfo startTourButton = findClickableNodeWithText(root, "Start Tour");
            if (startTourButton != null) {
                Logger.log(this, "Found 'Start Tour' button (case-insensitive) - clicking");
                startTourButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            } else {
                Logger.log(this, "No 'Start Tour' button found - sending click attempt anyway");
                // Simulate click at approximate button location if known (optional)
            }

        }
    }

    // Helper method to find a clickable node with case-insensitive text
    private AccessibilityNodeInfo findClickableNodeWithText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence nodeText = node.getText();
        if (nodeText != null && text.equalsIgnoreCase(nodeText.toString()) && node.isClickable()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findClickableNodeWithText(child, text);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No event handling needed; we rely on continuous polling
    }

    @Override
    public void onInterrupt() {
        Logger.log(this, "AutoClickService interrupted");
        isRunning = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacks(clickRunnable);
        Logger.log(this, "AutoClickService destroyed");
    }
}