package com.example.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

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
    private static final String FLEETLERY_PACKAGE = "com.fleetlery.driver";
    private static final long SCAN_INTERVAL_MS = 100; // Check every 100ms
    private Handler handler;
    private Runnable scanRunnable;
    private int scanCount = 0; // For debugging the number of scans

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.packageNames = new String[]{FLEETLERY_PACKAGE};
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
        logToFileAndLogcat("AutoClickService connected");

        // Initialize the Handler for periodic scanning
        handler = new Handler(Looper.getMainLooper());
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                scanCount++;
                logToFileAndLogcat("Periodic scan started (scan #" + scanCount + ")");
                // Log the foreground app using UsageStatsManager
                String foregroundApp = getForegroundApp();
                logToFileAndLogcat("Foreground app (via UsageStats): " + foregroundApp);

                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) {
                    logToFileAndLogcat("Root node is null");
                    // Log active windows for debugging
                    List<AccessibilityWindowInfo> windows = getWindows();
                    if (windows.isEmpty()) {
                        logToFileAndLogcat("No active windows available");
                    } else {
                        for (AccessibilityWindowInfo window : windows) {
                            logToFileAndLogcat("Active window: " + window.toString());
                        }
                    }
                    handler.postDelayed(this, SCAN_INTERVAL_MS); // Schedule next scan
                    return;
                }

                String packageName = root.getPackageName() != null ? root.getPackageName().toString() : "unknown";
                logToFileAndLogcat("Active package: " + packageName);
                if (FLEETLERY_PACKAGE.equals(packageName)) {
                    logToFileAndLogcat("Fleetlery app detected - performing scan for 'Start tour'");
                    scanScreenAndClickStartTour(root);
                } else {
                    logToFileAndLogcat("Fleetlery app not active, skipping scan");
                }

                // Schedule the next scan
                handler.postDelayed(this, SCAN_INTERVAL_MS);
            }
        };

        // Start the periodic scanning
        logToFileAndLogcat("Starting periodic scanning for 'Start tour'");
        handler.post(scanRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        logToFileAndLogcat("Event received: " + AccessibilityEvent.eventTypeToString(event.getEventType()));
        // Log the active package on window state changes
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String packageName = root != null && root.getPackageName() != null ? root.getPackageName().toString() : "unknown";
            logToFileAndLogcat("Window state changed, active package: " + packageName);
            // Log active windows for debugging
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows.isEmpty()) {
                logToFileAndLogcat("No active windows available during window state change");
            } else {
                for (AccessibilityWindowInfo window : windows) {
                    logToFileAndLogcat("Active window during window state change: " + window.toString());
                }
            }
        }
    }

    private String getForegroundApp() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long currentTime = System.currentTimeMillis();
        // Query usage stats for the last 5 seconds
        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - 5000, currentTime);
        if (stats != null) {
            for (UsageStats usageStats : stats) {
                if (usageStats.getLastTimeUsed() > currentTime - 5000) {
                    return usageStats.getPackageName();
                }
            }
        }
        return "unknown";
    }

    private void scanScreenAndClickStartTour(AccessibilityNodeInfo root) {
        List<ButtonInfo> buttons = findAllButtons(root);

        // Log button details
        StringBuilder logBuilder = new StringBuilder("Screen Scan Results:\n");
        if (buttons.isEmpty()) {
            logBuilder.append("No buttons found on screen.\n");
        } else {
            logBuilder.append("Found ").append(buttons.size()).append(" buttons:\n");
            for (ButtonInfo button : buttons) {
                logBuilder.append(button.toString()).append("\n");
            }
        }
        logToFileAndLogcat(logBuilder.toString());

        // Find and click "Start tour" button
        for (ButtonInfo button : buttons) {
            if ("Start tour".equalsIgnoreCase(button.text) ||
                    "Start tour".equalsIgnoreCase(button.contentDescription) ||
                    button.text.toLowerCase().contains("starttour")) {
                AccessibilityNodeInfo node = button.node;
                if (node != null && node.isClickable()) {
                    logToFileAndLogcat("Found 'Start tour' button - clicking");
                    boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    logToFileAndLogcat("Click result: " + (success ? "Success" : "Failed"));
                    return; // Found and clicked, but the periodic scan will continue
                }
            }
        }

        // Fallback: Use findAccessibilityNodeInfosByText
        List<AccessibilityNodeInfo> startTourNodes = root.findAccessibilityNodeInfosByText("Start tour");
        if (!startTourNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : startTourNodes) {
                if (node.isClickable()) {
                    logToFileAndLogcat("Found 'Start tour' button via findAccessibilityNodeInfosByText - clicking");
                    boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    logToFileAndLogcat("Click result: " + (success ? "Success" : "Failed"));
                    return;
                }
            }
        }

        logToFileAndLogcat("No 'Start tour' button found or it’s not clickable");
    }

    private List<ButtonInfo> findAllButtons(AccessibilityNodeInfo node) {
        List<ButtonInfo> buttons = new ArrayList<>();
        collectButtons(node, buttons);
        return buttons;
    }

    private void collectButtons(AccessibilityNodeInfo node, List<ButtonInfo> buttons) {
        if (node == null || !node.isVisibleToUser()) return;

        // Consider all clickable nodes, but filter later
        if (node.isClickable()) {
            String className = node.getClassName() != null ? node.getClassName().toString() : "unknown";
            String text = getNodeText(node);
            String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
            Rect boundsRect = new Rect();
            node.getBoundsInScreen(boundsRect);
            String bounds = boundsRect.toString();

            // Log all clickable nodes for debugging
            logToFileAndLogcat("Clickable node: Text='" + text + "', Class=" + className + ", Bounds=" + bounds);

            // Filter out non-button elements
            if (className.contains("EditText") ||
                    text.toLowerCase().contains("checkbox") ||
                    text.toLowerCase().contains("visualtransformationicon") ||
                    text.toLowerCase().contains("input") ||
                    text.toLowerCase().contains("navigation icon")) {
                return; // Skip non-buttons
            }

            buttons.add(new ButtonInfo(node, text, contentDesc, node.isClickable(), className, bounds));
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectButtons(child, buttons);
            }
        }
    }

    private String getNodeText(AccessibilityNodeInfo node) {
        // Check node's text
        String text = node.getText() != null ? node.getText().toString().trim() : "";
        if (!text.isEmpty() && !text.contains("ComposableTag")) {
            return text;
        }

        // Check content description
        String contentDesc = node.getContentDescription() != null ? node.getContentDescription().toString().trim() : "";
        if (!contentDesc.isEmpty() && !contentDesc.contains("ComposableTag")) {
            return contentDesc;
        }

        // Check tooltip text (API 26+)
        String tooltip = node.getTooltipText() != null ? node.getTooltipText().toString().trim() : "";
        if (!tooltip.isEmpty() && !tooltip.contains("ComposableTag")) {
            return tooltip;
        }

        // Check child nodes for text (deeper recursion)
        String childText = getTextFromChildren(node);
        if (!childText.isEmpty() && !childText.contains("ComposableTag")) {
            return childText;
        }

        // Fallback to original text if nothing better is found
        return text.isEmpty() ? contentDesc : text;
    }

    private String getTextFromChildren(AccessibilityNodeInfo node) {
        if (node == null) return "";

        // Traverse all children and their descendants
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                // Check child's text
                String childText = child.getText() != null ? child.getText().toString().trim() : "";
                if (!childText.isEmpty() && !childText.contains("ComposableTag")) {
                    return childText;
                }

                // Check child's content description
                String childDesc = child.getContentDescription() != null ? child.getContentDescription().toString().trim() : "";
                if (!childDesc.isEmpty() && !childDesc.contains("ComposableTag")) {
                    return childDesc;
                }

                // Check child's tooltip
                String childTooltip = child.getTooltipText() != null ? child.getTooltipText().toString().trim() : "";
                if (!childTooltip.isEmpty() && !childTooltip.contains("ComposableTag")) {
                    return childTooltip;
                }

                // Recursively check grandchildren
                String grandChildText = getTextFromChildren(child);
                if (!grandChildText.isEmpty() && !grandChildText.contains("ComposableTag")) {
                    return grandChildText;
                }
            }
        }
        return "";
    }

    private void logToFileAndLogcat(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String logEntry = timestamp + ": " + message;

        // Log to Logcat
        Log.d(TAG, logEntry);

        // Log to file
        File logFile = new File(getExternalFilesDir(null), "autoclicker_log.txt");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(logEntry).append("\n");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        logToFileAndLogcat("AutoClickService interrupted");
        // Stop the periodic scanning
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logToFileAndLogcat("AutoClickService destroyed");
        // Stop the periodic scanning
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }
    }

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
            return "Button: Text='" + text + "', ContentDesc='" + contentDescription + "', Clickable=" + isClickable +
                    ", Class=" + className + ", Bounds=" + bounds + ", Color=Unknown (not accessible)";
        }
    }
}