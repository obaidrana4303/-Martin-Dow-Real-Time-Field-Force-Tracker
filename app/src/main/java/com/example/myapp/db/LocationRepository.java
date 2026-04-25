package com.example.myapp.db;

import android.content.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository that wraps AppDatabase and exposes a safe, background-thread helper.
 * Use this class from Activities / Services — never call Room on the main thread.
 */
public class LocationRepository {

    private static final int MAX_ENTRIES = 50;

    private final LocationDao dao;
    private final ExecutorService executor;

    public LocationRepository(Context context) {
        dao      = AppDatabase.getInstance(context).locationDao();
        executor = Executors.newSingleThreadExecutor();
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Saves a location entry on a background thread.
     * If the table already holds MAX_ENTRIES rows, the oldest one is removed first.
     *
     * @param lat  WGS-84 latitude
     * @param lon  WGS-84 longitude
     * @param mode "gps" or "network"
     */
    public void saveLocationLocally(double lat, double lon, String mode) {
        executor.execute(() -> {
            // 1. Enforce cap: drop the oldest row when at limit
            if (dao.getCount() >= MAX_ENTRIES) {
                dao.deleteOldest();
            }

            // 2. Build ISO 8601 timestamp (requires API 26+)
            String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());

            // 3. Insert the new entry
            LocationEntry entry = new LocationEntry(lat, lon, mode, timestamp);
            dao.insert(entry);
        });
    }

    /** Returns all unsynced entries. Call from a background thread. */
    public List<LocationEntry> getAllUnsynced() {
        return dao.getAllUnsynced();
    }

    /** Marks the entry with the given id as synced. Call from a background thread. */
    public void markAsSynced(long id) {
        executor.execute(() -> dao.markAsSynced(id));
    }

    /** Shuts down the executor gracefully (e.g. in onDestroy). */
    public void shutdown() {
        executor.shutdown();
    }
}
