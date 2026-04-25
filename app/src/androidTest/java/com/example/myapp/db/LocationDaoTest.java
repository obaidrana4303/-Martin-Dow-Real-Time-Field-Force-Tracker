package com.example.myapp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LocationDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase db;
    private LocationDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        // Use in-memory database for testing as it is destroyed after each test
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.locationDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void testInsertion() {
        LocationEntry entry = new LocationEntry(24.86, 67.00, "gps", "2024-03-27T10:00:00Z");
        dao.insert(entry);

        List<LocationEntry> all = dao.getAllUnsynced(); // Since isSynced defaults to false
        assertEquals(1, all.size());
        
        LocationEntry result = all.get(0);
        assertEquals(24.86, result.latitude, 0.0001);
        assertEquals(67.00, result.longitude, 0.0001);
        assertEquals("gps", result.mode);
        assertEquals("2024-03-27T10:00:00Z", result.timestamp);
        assertFalse(result.isSynced);
    }

    @Test
    public void test50EntryLimit() {
        // 1. Insert 50 entries
        for (int i = 1; i <= 50; i++) {
            dao.insert(new LocationEntry(i, i, "gps", "time_" + i));
        }
        assertEquals(50, dao.getCount());

        // 2. Capture the ID of the first (oldest) entry
        List<LocationEntry> initialEntries = dao.getAllUnsynced();
        long oldestId = initialEntries.get(0).id;

        // 3. Insert 51st entry
        // In the real app, the Repository handles the "delete if count >= 50" logic
        // But the requirement asks to verify the deletion of the oldest.
        // We will simulate the Repository's logic here:
        if (dao.getCount() >= 50) {
            dao.deleteOldest();
        }
        dao.insert(new LocationEntry(51, 51, "gps", "time_51"));

        // 4. Verify count remains 50
        assertEquals(50, dao.getCount());

        // 5. Verify oldest ID is gone
        List<LocationEntry> currentEntries = dao.getAllUnsynced();
        for (LocationEntry e : currentEntries) {
            assertTrue("Oldest entry should have been deleted", e.id != oldestId);
        }
        
        // 6. Verify newest entry exists
        assertEquals("time_51", currentEntries.get(currentEntries.size() - 1).timestamp);
    }

    @Test
    public void testUnsyncedQuery() {
        // Insert 5 entries
        for (int i = 0; i < 5; i++) {
            dao.insert(new LocationEntry(0, 0, "test", "time_" + i));
        }

        // Mark 2 as synced
        List<LocationEntry> all = dao.getAllUnsynced();
        dao.markAsSynced(all.get(0).id);
        dao.markAsSynced(all.get(1).id);

        // Verify only 3 are returned by getAllUnsynced()
        List<LocationEntry> unsynced = dao.getAllUnsynced();
        assertEquals(3, unsynced.size());
    }

    @Test
    public void testMarkAsSynced() {
        LocationEntry entry = new LocationEntry(0, 0, "gps", "now");
        dao.insert(entry);
        
        List<LocationEntry> results = dao.getAllUnsynced();
        long id = results.get(0).id;
        assertFalse(results.get(0).isSynced);

        dao.markAsSynced(id);
        
        // getAllUnsynced should now be empty
        assertTrue(dao.getAllUnsynced().isEmpty());
    }

    @Test
    public void testDeleteOldest() {
        // Insert 3 entries with distinct timestamps/ids
        dao.insert(new LocationEntry(1, 1, "gps", "oldest"));
        dao.insert(new LocationEntry(2, 2, "gps", "middle"));
        dao.insert(new LocationEntry(3, 3, "gps", "newest"));

        List<LocationEntry> before = dao.getAllUnsynced();
        long oldestId = before.get(0).id;

        dao.deleteOldest();

        List<LocationEntry> after = dao.getAllUnsynced();
        assertEquals(2, after.size());
        for (LocationEntry e : after) {
            assertTrue("Oldest entry should be removed", e.id != oldestId);
        }
    }
}
