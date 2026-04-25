package com.example.myapp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.myapp.db.LocationEntry;
import com.example.myapp.db.LocationRepository;
import com.example.myapp.network.ApiService;
import com.example.myapp.network.LocationPayload;
import com.example.myapp.network.RetrofitClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LocationTrackingService extends Service {

    private static final String TAG             = "LocationTrackingService";
    private static final String BASE_URL = "http://192.168.1.31:5000/";
    private static final String CHANNEL_ID      = "location_channel";
    private static final int    NOTIFICATION_ID = 12345;
    private static final String PREFS_NAME      = "AppPrefs";
    private static final String KEY_DEVICE_ID   = "device_id";
    private static final String KEY_USERNAME    = "username";

    /** If no Fused update arrives within this window, fall back to network provider. */
    private static final long FALLBACK_TIMEOUT_MS = 30_000L;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback            locationCallback;
    private LocationRepository          repository;
    private ApiService                  apiService;
    private String                      deviceId;
    private String                      username;
    private NotificationManager         notificationManager;

    /** Handler + Runnable used to trigger the 30-second network fallback. */
    private final Handler  fallbackHandler  = new Handler(Looper.getMainLooper());
    private final Runnable fallbackRunnable = this::triggerNetworkFallback;

    /** Tracks the last mode so we only update the notification when it changes. */
    private String lastMode = "";

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient  = LocationServices.getFusedLocationProviderClient(this);
        repository           = new LocationRepository(this);
        apiService           = RetrofitClient.getApiService();
        notificationManager  = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        deviceId = prefs.getString(KEY_DEVICE_ID, "unknown_device");
        username = prefs.getString(KEY_USERNAME, "unknown_user");

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // We received a Fused update — reset the fallback timer
                    resetFallbackTimer();
                    handleLocation(location);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(
                NOTIFICATION_ID,
                buildNotification("Tracking Active", "Locating…"),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0
        );

        startLocationUpdates();
        fetchLastKnownLocation();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fallbackHandler.removeCallbacks(fallbackRunnable);
        fusedLocationClient.removeLocationUpdates(locationCallback);
        repository.shutdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Fused Location ───────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .build();

        fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper());

        // Start the fallback countdown
        resetFallbackTimer();
    }

    /** Get the last cached location immediately on start so we send something right away. */
    @SuppressLint("MissingPermission")
    private void fetchLastKnownLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                Log.d(TAG, "Got last known location immediately on start");
                handleLocation(location);
            }
        });
    }

    // ─── 30-Second Network Fallback ───────────────────────────────────────────

    /** Restarts the 30-second countdown. Called every time Fused delivers a fix. */
    private void resetFallbackTimer() {
        fallbackHandler.removeCallbacks(fallbackRunnable);
        fallbackHandler.postDelayed(fallbackRunnable, FALLBACK_TIMEOUT_MS);
    }

    /**
     * Called when Fused gives no update for 30 seconds.
     * Falls back to the raw NETWORK_PROVIDER via LocationManager.
     */
    @SuppressLint("MissingPermission")
    private void triggerNetworkFallback() {
        Log.w(TAG, "No Fused update in 30 s — triggering network fallback");
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;

        // 1. Try the last known network location first (instant)
        Location last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (last != null) {
            Log.d(TAG, "Fallback: using last known network location");
            handleFallbackLocation(last);
        }

        // 2. Register for a single live update from NETWORK_PROVIDER
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestSingleUpdate(
                    LocationManager.NETWORK_PROVIDER,
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(@NonNull Location location) {
                            Log.d(TAG, "Fallback: received live network location");
                            handleFallbackLocation(location);
                            // Restart fallback countdown so we keep checking
                            resetFallbackTimer();
                        }

                        @Override public void onProviderEnabled(@NonNull String provider)  {}
                        @Override public void onProviderDisabled(@NonNull String provider) {}
                        @Override public void onStatusChanged(String p, int s, Bundle e)   {}
                    },
                    Looper.getMainLooper()
            );
        } else {
            Log.w(TAG, "NETWORK_PROVIDER is also disabled — no location available");
            // Keep trying; reset timer so we check again in 30 s
            resetFallbackTimer();
        }
    }

    private void handleFallbackLocation(Location location) {
        // Force mode to "network" for all fallback fixes
        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        String mode = "network";
        updateNotificationMode(mode);
        if (isInternetAvailable()) {
            sendToServer(location.getLatitude(), location.getLongitude(), mode, timestamp);
        } else {
            repository.saveLocationLocally(location.getLatitude(), location.getLongitude(), mode);
        }
    }

    // ─── Location Handling ────────────────────────────────────────────────────

    public void handleLocation(Location location) {
        double lat  = location.getLatitude();
        double lon  = location.getLongitude();

        String provider = location.getProvider();
        String mode;
        // Check for accuracy to detect GPS quality in the Fused Provider
        if ("gps".equals(provider) || (location.hasAccuracy() && location.getAccuracy() <= 25.0f)) {
            mode = "gps";
        } else {
            mode = "network";
        }

        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());

        updateNotificationMode(mode);
        
        // Always try to send to server. Retrofit's onFailure will handle the local saving if it fails.
        sendToServer(lat, lon, mode, timestamp);
    }

    /** Helper for sending a pre-formed database entry to the server. */
    public void sendLocationToServer(LocationEntry entry) {
        sendToServer(entry.latitude, entry.longitude, entry.mode, entry.timestamp);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void updateNotificationMode(String mode) {
        if (mode != null && mode.equals(lastMode)) return; // avoid redundant updates
        lastMode = mode;

        String modeLabel = "gps".equals(mode) ? "GPS Mode" : "Network Mode";
        updateNotification("Tracking Active - " + modeLabel, "Your location is being monitored");
    }

    private Notification buildNotification(String title, String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String title, String text) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text));
        }
    }

    // ─── Network / Server ─────────────────────────────────────────────────────

    public void sendToServer(double lat, double lon, String mode, String timestamp) {
        LocationPayload payload = new LocationPayload(deviceId, username, lat, lon, mode, timestamp);

        apiService.updateLocation(payload).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                   @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("LocationTracker", "Sync Success: " + mode);
                    syncUnsyncedLocations();
                } else {
                    Log.e(TAG, "Server error: " + response.code());
                    repository.saveLocationLocally(lat, lon, mode);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e(TAG, "Network failure: " + t.getMessage());
                repository.saveLocationLocally(lat, lon, mode);
            }
        });
    }

    private void syncUnsyncedLocations() {
        new Thread(() -> {
            List<LocationEntry> unsynced = repository.getAllUnsynced();
            if (unsynced.isEmpty()) return;
            syncOfflineLocations(unsynced);
        }).start();
    }

    /** Synchronizes a specific list of unsynced locations with the server. (internal implementation) */
    public void syncOfflineLocations(List<LocationEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        Log.d(TAG, "Syncing " + entries.size() + " unsynced locations…");
        for (LocationEntry entry : entries) {
            LocationPayload payload = new LocationPayload(
                    deviceId, username, entry.latitude, entry.longitude, entry.mode, entry.timestamp);
            try {
                Response<ResponseBody> response = apiService.updateLocation(payload).execute();
                if (response.isSuccessful()) {
                    repository.markAsSynced(entry.id);
                    Log.d(TAG, "Marked entry " + entry.id + " as synced");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync entry " + entry.id + ": " + e.getMessage());
                break;
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo active = cm.getActiveNetworkInfo();
            return active != null && active.isConnected();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
