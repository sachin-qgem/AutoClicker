package com.example.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AutoClickService extends AccessibilityService {
    private static final String TAG = "AutoClickService";
    private static final String FLEETLERY_PACKAGE = "com.fleetlery.driver"; // Ensure this matches the app's package name

    // Handler for running the scanning loop on the main thread
    private Handler handler = new Handler(Looper.getMainLooper());

    // Runnable that performs scanning every 100ms
    private Runnable scanningRunnable = new Runnable() {
        @Override
        public void run() {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && FLEETLERY_PACKAGE.equals(root.getPackageName())) {
                logToFileAndLogcat("Scanning Fleetlery app for 'Start Tour' button");
                scanScreenAndClickSignUp(root);
            } else {
                logToFileAndLogcat("Not scanning: Fleetlery app not active");
            }
            handler.postDelayed(this, 100); // Re-run every 100 milliseconds
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.packageNames = new String[]{FLEETLERY_PACKAGE};
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
        logToFileAndLogcat("AutoClickService connected");
        handler.post(scanningRunnable); // Start the continuous scanning
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        logToFileAndLogcat("Event received: " + AccessibilityEvent.eventTypeToString(event.getEventType()));
        // Scanning is handled by the timer, not here
    }

    @Override
    public void onInterrupt() {
        logToFileAndLogcat("AutoClickService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scanningRunnable); // Stop the scanning
        logToFileAndLogcat("AutoClickService destroyed");
    }

    // Scans the screen and clicks the "Start Tour" button
    private void scanScreenAndClickSignUp(AccessibilityNodeInfo root) {
        List<ButtonInfo> buttons = findAllButtons(root);
        StringBuilder logBuilder = new StringBuilder("Screen Scan Results: \n");
        if (buttons.isEmpty()) {
            logBuilder.append("No buttons found on screen. \n");
        } else {
            logBuilder.append("Found ").append(buttons.size()).append(" buttons: \n");
            for (ButtonInfo button : buttons) {
                logBuilder.append(button.toString()).append(" \n");
            }
        }
        logToFileAndLogcat(logBuilder.toString());

        for (ButtonInfo button : buttons) {
            if ("Start Tour".equalsIgnoreCase(button.text) || "Start Tour".equalsIgnoreCase(button.contentDescription)) {
                boolean success = button.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                logToFileAndLogcat("Click result: " + (success ? "Success" : "Failed"));
                return; // Click only once per scan
            }
        }

        List<AccessibilityNodeInfo> signUpNodes = root.findAccessibilityNodeInfosByText("Start Tour");
        if (!signUpNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : signUpNodes) {
                if (node.isClickable()) {
                    logToFileAndLogcat("Found 'Start Tour' button via findAccessibilityNodeInfosByText - clicking");
                    boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    logToFileAndLogcat("Click result: " + (success ? "Success" : "Failed"));
                    return;
                }
            }
        }
        logToFileAndLogcat("No 'Start Tour' button found or it's not clickable");
    }

    // Finds all clickable buttons
    private List<ButtonInfo> findAllButtons(AccessibilityNodeInfo node) {
        List<ButtonInfo> buttons = new ArrayList<>();
        collectButtons(node, buttons);
        return buttons;
    }

    // Recursively collects clickable buttons
    private void collectButtons(AccessibilityNodeInfo node, List<ButtonInfo> buttons) {
        if (node == null || !node.isVisibleToUser()) return;
        if (node.isClickable()) {
            String className = node.getClassName() != null ? node.getClassName().toString() : "unknown";
            String text = getNodeText(node);
            String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            Rect boundsRect = new Rect();
            node.getBoundsInScreen(boundsRect);
            String bounds = boundsRect.toString();
            logToFileAndLogcat("Clickable node: Text='" + text + "', Class=" + className + ", Bounds=" + bounds);
            if (!className.contains("EditText")) {
                buttons.add(new ButtonInfo(node, text, contentDesc, node.isClickable(), className, bounds));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectButtons(node.getChild(i), buttons);
        }
    }

    // Gets text from a node
    private String getNodeText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        String text = node.getText() != null ? node.getText().toString().trim() : "";
        if (!text.isEmpty() && !text.contains("ComposableTag")) {
            return text;
        }
        String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
        if (!contentDesc.isEmpty() && !contentDesc.contains("ComposableTag")) {
            return contentDesc;
        }
        String tooltip = node.getTooltipText() != null ? node.getTooltipText().toString().trim() : "";
        if (!tooltip.isEmpty() && !tooltip.contains("ComposableTag")) {
            return tooltip;
        }
        String childText = getTextFromChildren(node);
        if (!childText.isEmpty() && !childText.contains("ComposableTag")) {
            return childText;
        }
        return text.isEmpty() ? contentDesc : text;
    }

    // Gets text from children nodes
    private String getTextFromChildren(AccessibilityNodeInfo node) {
        if (node == null) return "";
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String childText = child.getText() != null ? child.getText().toString().trim() : "";
                if (!childText.isEmpty() && !childText.contains("ComposableTag")) {
                    return childText;
                }
                String childDesc = child.getContentDescription() != null ? child.getContentDescription().toString().trim() : "";
                if (!childDesc.isEmpty() && !childDesc.contains("ComposableTag")) {
                    return childDesc;
                }
                String childTooltip = child.getTooltipText() != null ? child.getTooltipText().toString().trim() : "";
                if (!childTooltip.isEmpty() && !childTooltip.contains("ComposableTag")) {
                    return childTooltip;
                }
                String grandChildText = getTextFromChildren(child);
                if (!grandChildText.isEmpty() && !grandChildText.contains("ComposableTag")) {
                    return grandChildText;
                }
            }
        }
        return "";
    }

    // Logs to both file and Logcat
    private void logToFileAndLogcat(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String logEntry = timestamp + ": " + message;
        Log.d(TAG, logEntry);
        File logFile = new File(getExternalFilesDir(null), "autoclicker_log.txt");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(logEntry).append("\n");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file: " + e.getMessage());
        }
    }

    // Inner class to store button information
    private static class ButtonInfo {
        AccessibilityNodeInfo node;
        String text;
        String contentDescription;
        boolean isClickable;
        String className;
        String bounds;

        ButtonInfo(AccessibilityNodeInfo node, String text, String contentDescription, boolean isClickable, String className, String bounds) {
            this.node = node;
            this.text = text.isEmpty() ? contentDescription : text;
            this.contentDescription = contentDescription;
            this.isClickable = isClickable;
            this.className = className;
            this.bounds = bounds;
        }

        @Override
        public String toString() {
            return "Button: Text='" + text + "', ContentDesc='" + contentDescription + "', Clickable=" + isClickable + ", Class=" + className + ", Bounds=" + bounds;
        }
    }
}