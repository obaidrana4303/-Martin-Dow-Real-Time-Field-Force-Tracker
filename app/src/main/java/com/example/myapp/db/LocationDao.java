package com.example.myapp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocationDao {

    /** Insert a new location entry. */
    @Insert
    void insert(LocationEntry entry);

    /** Return all entries that have not yet been synced. */
    @Query("SELECT * FROM location_entries WHERE is_synced = 0 ORDER BY id ASC")
    List<LocationEntry> getAllUnsynced();

    /** Mark a single entry as synced by its id. */
    @Query("UPDATE location_entries SET is_synced = 1 WHERE id = :id")
    void markAsSynced(long id);

    /** Return the total number of stored entries. */
    @Query("SELECT COUNT(*) FROM location_entries")
    int getCount();

    /** Delete the oldest entry (lowest id) to keep the table bounded. */
    @Query("DELETE FROM location_entries WHERE id = (SELECT MIN(id) FROM location_entries)")
    void deleteOldest();
}
