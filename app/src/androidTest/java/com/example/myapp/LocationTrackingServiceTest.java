package com.example.myapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.example.myapp.db.AppDatabase;
import com.example.myapp.db.LocationDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LocationTrackingServiceTest {

    private Context context;
    private UiDevice uiDevice;
    private LocationDao dao;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        dao = AppDatabase.getInstance(context).locationDao();
        
        // Clear DB for fresh interval count
        dao.deleteOldest(); // Simple clear helper if needed, or use a cleanup method
    }

    @After
    public void teardown() {
        context.stopService(new Intent(context, LocationTrackingService.class));
    }

    @Test
    public void testServiceStartsForeground() {
        Intent intent = new Intent(context, LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        // Give it a moment to initialize
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        assertTrue("Service should be running", isServiceRunning(LocationTrackingService.class));
    }

    @Test
    public void testNotificationUI() {
        testServiceStartsForeground();

        // Open notification shade
        uiDevice.openNotification();
        uiDevice.wait(Until.hasObject(By.text("Tracking Active")), 5000);

        UiObject2 notification = uiDevice.findObject(By.text("Tracking Active"));
        assertTrue("Notification title 'Tracking Active' should be visible", notification != null);

        // Verify it is not swipable (UI Automator cannot directly check 'ongoing' flag, 
        // but we can attempt a swipe and see if it remains)
        notification.swipe(androidx.test.uiautomator.Direction.RIGHT, 1.0f);
        assertTrue("Notification should still be present after swipe attempt", 
                uiDevice.hasObject(By.text("Tracking Active")));
        
        uiDevice.pressBack(); // Close notification shade
    }

    @Test
    public void testUpdateInterval() throws InterruptedException {
        testServiceStartsForeground();
        
        // Clear existing data
        // dao.clearAll(); 

        // Wait for 60 seconds to count updates
        Thread.sleep(60000);

        int count = dao.getCount();
        // Expected interval is 10s. With variation, 4-12 is a safe range for 1 minute.
        assertTrue("Receive between 4 and 12 updates in 60s. Actual: " + count, 
                count >= 4 && count <= 12);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
