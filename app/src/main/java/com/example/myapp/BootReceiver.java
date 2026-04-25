package com.example.myapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device rebooted. Starting LocationTrackingService...");
            
            Intent serviceIntent = new Intent(context, LocationTrackingService.class);
            // ContextCompat.startForegroundService is required for Android 8.0+
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
