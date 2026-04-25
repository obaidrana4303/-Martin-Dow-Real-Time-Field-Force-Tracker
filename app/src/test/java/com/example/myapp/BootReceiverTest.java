package com.example.myapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BootReceiverTest {

    private Context context;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testBootReceiverRegisteredInManifest() {
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryBroadcastReceivers(intent, 0);

        boolean found = false;
        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo.name.equals(BootReceiver.class.getName())) {
                found = true;
                break;
            }
        }
        assertTrue("BootReceiver should be registered for BOOT_COMPLETED in Manifest", found);
    }

    @Test
    public void testBootReceiverStartsService() {
        BootReceiver receiver = new BootReceiver();
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);

        receiver.onReceive(context, intent);

        // Verify that the service was started
        Intent nextIntent = shadowOf((android.app.Application) context).getNextStartedService();
        assertNotNull("Service should have been started", nextIntent);
        assertEquals(LocationTrackingService.class.getName(), nextIntent.getComponent().getClassName());
    }

    @Test
    public void testWrongActionDoesNotStartService() {
        BootReceiver receiver = new BootReceiver();
        Intent intent = new Intent(Intent.ACTION_ANSWER); // Random different action

        receiver.onReceive(context, intent);

        Intent nextIntent = shadowOf((android.app.Application) context).getNextStartedService();
        assertTrue("Service should NOT be started for unknown actions", nextIntent == null);
    }

    // Helper for manifest check since queryBroadcastReceivers might need explicit search
    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
