package com.example.autoclicker;

import android.os.Environment;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    private static final String TAG = "AutoClicker";
    private static final String LOG_FILE = "autoclick_log.txt";
    private static final SimpleDateFormat TIMESTAMP_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public static void log(String message) {
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        String logEntry = String.format("%s: %s", timestamp, message);
        Log.d(TAG, logEntry);

        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!dir.exists()) dir.mkdirs();
            File logFile = new File(dir, LOG_FILE);
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(logEntry);
            writer.newLine();
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log: " + e.getMessage());
        }
    }
}