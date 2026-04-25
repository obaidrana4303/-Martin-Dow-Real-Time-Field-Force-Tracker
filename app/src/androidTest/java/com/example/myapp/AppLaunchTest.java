package com.example.myapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppLaunchTest {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_TERMS_ACCEPTED = "terms_accepted";
    private static final String KEY_DEVICE_ID = "device_id";

    @Before
    public void setup() {
        // Clear SharedPreferences before each test to simulate first launch
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void testFirstLaunchShowsTC() {
        // Start SplashActivity
        ActivityScenario.launch(SplashActivity.class);

        // Verify T&C text and button are visible
        onView(withText("Get Started")).check(matches(isDisplayed()));
    }

    @Test
    public void testAcceptanceSavesToPrefs() {
        // Start SplashActivity
        ActivityScenario.launch(SplashActivity.class);

        // Click Accept
        onView(withText("Get Started")).perform(click());

        // Verify SharedPreferences
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue("Terms should be marked as accepted", prefs.getBoolean(KEY_TERMS_ACCEPTED, false));
        
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        assertNotNull("DeviceId should be generated", deviceId);
    }

    @Test
    public void testSecondLaunchSkipsTC() {
        // 1. Pre-set terms_accepted to true
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, true).putString(KEY_DEVICE_ID, "test-id-123").commit();

        // 2. Launch SplashActivity
        ActivityScenario.launch(SplashActivity.class);

        // 3. Since it skips to MainActivity, T&C button should NOT be present
        // (Espresso would fail if we tried to find a non-existent view without a specific check)
        // More reliably, we check that we reached MainActivity by looking for a view unique to it
        onView(withId(R.id.btn_submit_objective)).check(matches(isDisplayed()));
    }

    @Test
    public void testDeviceIdPersistence() {
        // 1. Launch first time, generate ID
        ActivityScenario.launch(SplashActivity.class);
        onView(withText("Get Started")).perform(click());

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String firstId = prefs.getString(KEY_DEVICE_ID, null);
        assertNotNull(firstId);

        // 2. Re-launch (without clearing prefs this time, though @Before clears them, 
        // for this specific test case we override logic)
        ActivityScenario.launch(SplashActivity.class);
        String secondId = prefs.getString(KEY_DEVICE_ID, null);
        
        assertEquals("Device ID must persist across restarts", firstId, secondId);
    }
}
