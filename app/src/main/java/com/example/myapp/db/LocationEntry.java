package com.example.myapp.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_entries")
public class LocationEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    /** "gps" or "network" */
    @ColumnInfo(name = "mode")
    public String mode;

    /** ISO 8601 timestamp, e.g. "2026-03-27T09:38:56+05:00" */
    @ColumnInfo(name = "timestamp")
    public String timestamp;

    /** false until successfully synced to the server */
    @ColumnInfo(name = "is_synced")
    public boolean isSynced = false;

    // ── Constructor ──────────────────────────────────────────────────────────

    public LocationEntry(double latitude, double longitude, String mode, String timestamp) {
        this.latitude  = latitude;
        this.longitude = longitude;
        this.mode      = mode;
        this.timestamp = timestamp;
        this.isSynced  = false;
    }
}
