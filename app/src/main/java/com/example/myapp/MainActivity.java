package com.example.myapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapp.db.AppDatabase;
import com.example.myapp.db.DayObjectiveEntity;
import com.example.myapp.network.ApiService;
import com.example.myapp.network.DoctorEntry;
import com.example.myapp.network.DayObjective;
import com.example.myapp.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.graphics.Color;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_USERNAME = "username";

    private String deviceId;
    private String username;
    private String tripType = null; // null, "Out Station", or "Local"

    private MaterialButton btnOutStation, btnLocal, btnSubmit;
    private LinearLayout containerDoctors;
    private TextInputEditText etTripDescription;
    private List<View> doctorRows = new ArrayList<>();
    private TextView activeLocationLabel;

    private final ActivityResultLauncher<Intent> mapPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && activeLocationLabel != null) {
                    double lat = result.getData().getDoubleExtra("lat", 0);
                    double lon = result.getData().getDoubleExtra("lon", 0);
                    String address = result.getData().getStringExtra("address");
                    
                    activeLocationLabel.setText(address);
                    activeLocationLabel.setVisibility(View.VISIBLE);
                    activeLocationLabel.setTag(new double[]{lat, lon});
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // -- Initialization --
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        deviceId = prefs.getString(KEY_DEVICE_ID, null);
        username = prefs.getString(KEY_USERNAME, "Unknown User");

        if (deviceId == null) {
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }

        // 1. Header Date
        TextView tvDate = findViewById(R.id.tv_current_date);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
        tvDate.setText(LocalDate.now().format(dtf));

        // 2. Middle Section: Trip Type Buttons
        btnOutStation = findViewById(R.id.btn_out_station);
        btnLocal = findViewById(R.id.btn_local);
        etTripDescription = findViewById(R.id.et_trip_description);

        btnOutStation.setOnClickListener(v -> selectTripType("Out Station"));
        btnLocal.setOnClickListener(v -> selectTripType("Local"));

        // 3. Bottom Section: Dynamic List
        containerDoctors = findViewById(R.id.container_doctors);
        addDoctorRow(); // Start with one empty row

        // 4. Submit Button
        btnSubmit = findViewById(R.id.btn_submit_objective);
        btnSubmit.setOnClickListener(v -> validateAndSubmit());

        // Background tracking (existing logic)
        startLocationServiceIfNeeded();
    }

    private void selectTripType(String type) {
        tripType = type;
        boolean isOut = "Out Station".equals(type);
        if (isOut) {
            btnOutStation.setBackgroundColor(Color.parseColor("#2563eb"));
            btnOutStation.setTextColor(Color.WHITE);
            btnLocal.setBackgroundColor(Color.TRANSPARENT);
            btnLocal.setTextColor(Color.parseColor("#2563eb"));
        } else {
            btnLocal.setBackgroundColor(Color.parseColor("#2563eb"));
            btnLocal.setTextColor(Color.WHITE);
            btnOutStation.setBackgroundColor(Color.TRANSPARENT);
            btnOutStation.setTextColor(Color.parseColor("#2563eb"));
        }
    }

    private void addDoctorRow() {
        if (doctorRows.size() >= 20) return;

        View rowView = LayoutInflater.from(this).inflate(R.layout.item_doctor_row, containerDoctors, false);
        TextInputEditText etName = rowView.findViewById(R.id.et_doctor_name);
        MaterialButton btnMap = rowView.findViewById(R.id.btn_pick_location);
        TextView tvLocationLabel = rowView.findViewById(R.id.tv_location_result);

        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (doctorRows.get(doctorRows.size() - 1) == rowView && s.length() > 0) {
                    addDoctorRow();
                }
            }
        });

        btnMap.setOnClickListener(v -> {
            activeLocationLabel = tvLocationLabel;
            Intent intent = new Intent(this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        containerDoctors.addView(rowView);
        doctorRows.add(rowView);
    }

    private void validateAndSubmit() {
        if (tripType == null) {
            Toast.makeText(this, "Please select a Trip Type", Toast.LENGTH_LONG).show();
            return;
        }

        String description = etTripDescription.getText().toString().trim();
        List<DoctorEntry> doctors = new ArrayList<>();
        for (View row : doctorRows) {
            TextInputEditText etName = row.findViewById(R.id.et_doctor_name);
            TextInputEditText etLocManual = row.findViewById(R.id.et_location_manual);
            TextView tvLoc = row.findViewById(R.id.tv_location_result);
            String name = etName.getText().toString().trim();
            
            if (!name.isEmpty()) {
                double lat = 0, lon = 0;
                String locPinned = tvLoc.getText().toString();
                String locManual = etLocManual != null && etLocManual.getText() != null ? etLocManual.getText().toString().trim() : "";
                
                String finalLocName = "";
                if (!locManual.isEmpty() && tvLoc.getVisibility() == View.VISIBLE) {
                    finalLocName = locManual + " (" + locPinned + ")";
                } else if (!locManual.isEmpty()) {
                    finalLocName = locManual;
                } else if (tvLoc.getVisibility() == View.VISIBLE) {
                    finalLocName = locPinned;
                } else {
                    finalLocName = "Not Provided";
                }

                if (tvLoc.getTag() instanceof double[]) {
                    double[] coords = (double[]) tvLoc.getTag();
                    lat = coords[0];
                    lon = coords[1];
                }
                doctors.add(new DoctorEntry(name, finalLocName, lat, lon));
            }
        }

        if (doctors.isEmpty()) {
            Toast.makeText(this, "Please enter at least one doctor name", Toast.LENGTH_LONG).show();
            return;
        }

        String dateStr = LocalDate.now().toString(); 
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        DayObjective payload = new DayObjective(deviceId, username, dateStr, tripType, description, doctors, timestamp);

        submitToBackend(payload);
    }

    private void submitToBackend(DayObjective payload) {
        ApiService apiService = RetrofitClient.getApiService();
        apiService.submitObjective(payload).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Submitted Successfully!", Toast.LENGTH_SHORT).show();
                    resetDoctorRows();
                } else {
                    saveObjectiveOffline(payload);
                    Toast.makeText(MainActivity.this, "Server Error: Saved locally", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                saveObjectiveOffline(payload);
                Toast.makeText(MainActivity.this, "Network Failure: Saved locally", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetDoctorRows() {
        containerDoctors.removeAllViews();
        doctorRows.clear();
        addDoctorRow();
    }

    private void saveObjectiveOffline(DayObjective obj) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            DayObjectiveEntity entity = new DayObjectiveEntity(
                    obj.deviceId, obj.username, obj.date,
                    obj.tripType, obj.tripDescription, obj.timestamp, obj.doctors
            );
            db.objectiveDao().insert(entity);
        }).start();
    }

    private void syncPendingObjectives() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<DayObjectiveEntity> failedList = db.objectiveDao().getAllFailedObjectives();
            if (failedList != null && !failedList.isEmpty()) {
                ApiService apiService = RetrofitClient.getApiService();
                for (DayObjectiveEntity entity : failedList) {
                        DayObjective payload = new DayObjective(
                                entity.device_id, entity.username, entity.date,
                                entity.trip_type, entity.trip_description, entity.doctors, entity.timestamp
                        );
                    
                    try {
                        Response<ResponseBody> response = apiService.submitObjective(payload).execute();
                        if (response.isSuccessful()) {
                            db.objectiveDao().delete(entity);
                            Log.d("Sync", "Successfully synced offline objective ID: " + entity.id);
                        }
                    } catch (Exception e) {
                        Log.e("Sync", "Failed to sync offline objective: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocationServiceIfNeeded();
        syncPendingObjectives();
    }

    private void startLocationServiceIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d("LocationTracker", "Permissions OK. Attempting to start service...");
            Intent serviceIntent = new Intent(this, LocationTrackingService.class);
            try {
                ContextCompat.startForegroundService(this, serviceIntent);
            } catch (Exception e) {
                Log.e("LocationTracker", "Failed to start service: " + e.getMessage());
            }
        }
    }
}
