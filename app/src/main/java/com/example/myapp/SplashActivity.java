package com.example.myapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME      = "AppPrefs";
    private static final String KEY_ACCEPTED_TC = "accepted_terms";
    private static final String KEY_DEVICE_ID   = "device_id";
    private static final String KEY_USERNAME    = "username";

    private static final int REQUEST_FINE_LOCATION       = 1001;
    private static final int REQUEST_BACKGROUND_LOCATION = 1002;

    // UI panels
    private ConstraintLayout panelTc;
    private ConstraintLayout panelPermission;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Generate and save device ID on first launch
        if (!prefs.contains(KEY_DEVICE_ID)) {
            String deviceId = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ANDROID_ID);
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }

        panelTc         = findViewById(R.id.panel_tc);
        panelPermission = findViewById(R.id.panel_permission);

        boolean tcAccepted        = prefs.getBoolean(KEY_ACCEPTED_TC, false);
        boolean usernameSet       = prefs.contains(KEY_USERNAME);
        boolean fineGranted       = isFineLocationGranted();
        boolean backgroundGranted = isBackgroundLocationGranted();

        // All done — launch straight away
        if (tcAccepted && usernameSet && fineGranted && backgroundGranted) {
            launchApp();
            return;
        }

        // T&C already accepted but username or permissions still missing
        if (tcAccepted && usernameSet) {
            showPermissionRationalePanel();
            return;
        }

        // First launch — show T&C panel
        showTcPanel();
    }

    // ─── Panel helpers ────────────────────────────────────────────────────────

    private void showTcPanel() {
        panelTc.setVisibility(View.VISIBLE);
        panelPermission.setVisibility(View.GONE);

        TextView tvTerms   = findViewById(R.id.tv_terms);
        Button   btnAccept = findViewById(R.id.btn_accept);
        final android.widget.EditText etUsername = findViewById(R.id.et_username);

        tvTerms.setText(R.string.terms_and_conditions_text);

        btnAccept.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            if (username.isEmpty()) {
                etUsername.setError("Username is required");
                return;
            }

            prefs.edit()
                .putBoolean(KEY_ACCEPTED_TC, true)
                .putString(KEY_USERNAME, username)
                .apply();

            // Move to visual rationale panel before triggering system dialog
            showPermissionRationalePanel();
        });
    }

    /**
     * Shows the beautiful dark "one-tap" rationale screen.
     * The user reads it and taps the button — then we trigger the OS dialog.
     */
    private void showPermissionRationalePanel() {
        panelTc.setVisibility(View.GONE);
        panelPermission.setVisibility(View.VISIBLE);

        Button btnGrant = findViewById(R.id.btn_grant);
        btnGrant.setOnClickListener(v -> requestFineLocation());
    }

    // ─── Permission Requests ──────────────────────────────────────────────────

    private void requestFineLocation() {
        if (isFineLocationGranted()) {
            requestBackgroundLocation();
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQUEST_FINE_LOCATION
        );
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            launchApp();
            return;
        }

        if (isBackgroundLocationGranted()) {
            launchApp();
            return;
        }

        // On Android 11+ the background permission must be requested separately.
        // Show a quick explanation before sending user to settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: must go to app settings to pick "Allow all the time"
            showGoToSettingsDialog();
        } else {
            // Android 10: requestPermissions still works for background
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION
            );
        }
    }

    // ─── Permission Result ────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_FINE_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                requestBackgroundLocation();
            } else {
                showDeniedDialog(false);
            }

        } else if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                launchApp();
            } else {
                showDeniedDialog(true);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When the user returns from Settings, re-check if background was granted
        if (prefs.getBoolean(KEY_ACCEPTED_TC, false)
                && isFineLocationGranted()
                && isBackgroundLocationGranted()) {
            launchApp();
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    /**
     * Shown on Android 11+ where background permission requires going to Settings.
     * Explains exactly what the user needs to do.
     */
    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable 'Allow all the time'")
                .setMessage("This app REQUIRES background location to function. \n\nWhen the system asks:\n- Select 'ALLOW ALL THE TIME'\n- Keep 'Use precise location' ON\n\nIf you select any other option, the app will not work.")
                .setCancelable(false)
                .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                .setNegativeButton("Exit App", (d, w) -> finishAffinity())
                .show();
    }

    /** Shown when the user denies a permission in the system dialog. */
    private void showDeniedDialog(boolean isBackground) {
        String msg = isBackground
                ? "You selected an incorrect option. This app REQUIRES 'Allow all the time' to function correctly.\n\nPlease go to Settings -> Location and change it to 'Allow all the time'."
                : "Location access is MANDATORY for this app. Without it, we cannot proceed.\n\nPlease grant location permission in Settings.";

        new AlertDialog.Builder(this)
                .setTitle("Permission Mandatory")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                .setNegativeButton("Exit App", (d, w) -> finishAffinity())
                .show();
    }

    /** Opens this app's system permission settings page. */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isFineLocationGranted() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isBackgroundLocationGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ─── Launch ───────────────────────────────────────────────────────────────

    private void launchApp() {
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        Intent mainIntent = new Intent(this, MainActivity.class);
        startActivity(mainIntent);
        finish();
    }
}
