package com.example.autoclicker;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;

public class MainActivity extends AppCompatActivity {
    private Button startButton, stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AutoClickService.class);
            intent.setAction("START");
            startService(intent);
            Toast.makeText(this, "AutoClick started", Toast.LENGTH_SHORT).show();
        });

        stopButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AutoClickService.class);
            intent.setAction("STOP");
            startService(intent);
            Toast.makeText(this, "AutoClick stopped", Toast.LENGTH_SHORT).show();
        });

        // Prompt user to enable Accessibility Service
        Toast.makeText(this, "Please enable the Accessibility Service", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));

        // Request storage permission for logging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }
}