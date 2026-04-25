package com.example.myapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.location.Location;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.myapp.db.AppDatabase;
import com.example.myapp.db.LocationDao;
import com.example.myapp.db.LocationEntry;
import com.example.myapp.db.LocationRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LocationSyncTest {

    private Context context;
    private ConnectivityManager connectivityManager;
    private LocationRepository repository;
    private LocationDao dao;
    private LocationTrackingService service;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        connectivityManager = mock(ConnectivityManager.class);
        
        // Use a real DAO but mock the repository's network dependency if possible
        // Or better, spy on the service to verify method calls
        service = spy(new LocationTrackingService());
        
        // Mocking isInternetAvailable logic
        // Since isInternetAvailable is private/internal, we mock the system service return
        // but for this test we'll assume the service uses the provided connectivityManager
    }

    @Test
    public void testOnlineMode() {
        // 1. Mock Internet ON
        when(service.isInternetAvailable()).thenReturn(true);
        
        // 2. Trigger location update
        Location location = mock(Location.class);
        when(location.getLatitude()).thenReturn(1.0);
        when(location.getLongitude()).thenReturn(1.0);
        when(location.getProvider()).thenReturn("gps");
        
        service.handleLocation(location);

        // 3. Verify sendToServer was called
        verify(service, times(1)).sendToServer(any(Double.class), any(Double.class), any(String.class), any(String.class));
    }

    @Test
    public void testOfflineMode() {
        // 1. Mock Internet OFF
        when(service.isInternetAvailable()).thenReturn(false);

        // 2. Trigger 5 updates
        for (int i = 0; i < 5; i++) {
            Location location = mock(Location.class);
            when(location.getLatitude()).thenReturn((double)i);
            when(location.getLongitude()).thenReturn((double)i);
            service.handleLocation(location);
        }

        // 3. Verify sendToServer was NEVER called
        verify(service, never()).sendToServer(any(Double.class), any(Double.class), any(String.class), any(String.class));
    }

    @Test
    public void testSyncOnRestore() {
        // 1. Start with 5 unsynced entries in a real/mock list
        List<LocationEntry> unsynced = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            unsynced.add(new LocationEntry(i, i, "gps", "old_" + i));
        }
        
        // 2. Mock internet restore
        when(service.isInternetAvailable()).thenReturn(true);
        
        // 3. Trigger sync (usually happens in handleLocation or a timer)
        service.syncOfflineLocations(unsynced);

        // 4. Verify all 5 were sent
        verify(service, times(5)).sendToServer(any(Double.class), any(Double.class), any(String.class), any(String.class));
    }

    @Test
    public void testMixedScenario() {
        // 1. 3 locations online
        when(service.isInternetAvailable()).thenReturn(true);
        for (int i = 0; i < 3; i++) {
            service.sendLocationToServer(new LocationEntry(0, 0, "online", "t" + i));
        }
        verify(service, times(3)).sendLocationToServer(any());

        // 2. 3 locations offline
        when(service.isInternetAvailable()).thenReturn(false);
        for (int i = 0; i < 3; i++) {
            service.sendLocationToServer(new LocationEntry(0, 0, "offline", "t" + (i+3)));
        }
        
        // 3. Restore and sync
        when(service.isInternetAvailable()).thenReturn(true);
        // Simulate the next location update triggering a sync
        List<LocationEntry> buffer = new ArrayList<>(); // In real app, this comes from DAO
        buffer.add(new LocationEntry(0,0,"offline","t3"));
        buffer.add(new LocationEntry(0,0,"offline","t4"));
        buffer.add(new LocationEntry(0,0,"offline","t5"));
        service.syncOfflineLocations(buffer);

        // 4. Total verification: 3 direct + 3 synced = 6 calls
        verify(service, times(6)).sendLocationToServer(any());
    }
}
