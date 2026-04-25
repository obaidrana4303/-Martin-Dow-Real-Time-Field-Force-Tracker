package com.example.myapp;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private MaterialButton btnConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        btnConfirm = findViewById(R.id.btn_confirm_location);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnConfirm.setOnClickListener(v -> {
            if (mMap != null) {
                LatLng center = mMap.getCameraPosition().target;
                returnResult(center);
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        
        // 1. Check for location permissions
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED && 
            androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        enableMyLocation();
    }

    private void enableMyLocation() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            
            // Focus on Karolli area or current location if available
            LatLng defaultLoc = new LatLng(24.8607, 67.0011);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 12));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }
    }

    private void returnResult(LatLng location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        String addressName = "Custom Location (" + location.latitude + ", " + location.longitude + ")";
        
        try {
            List<Address> addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                addressName = addr.getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra("lat", location.latitude);
        resultIntent.putExtra("lon", location.longitude);
        resultIntent.putExtra("address", addressName);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
