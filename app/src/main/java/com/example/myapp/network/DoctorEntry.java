package com.example.myapp.network;

import com.google.gson.annotations.SerializedName;

public class DoctorEntry {
    @SerializedName("name")
    public String name;

    @SerializedName("location_name")
    public String locationName;

    @SerializedName("lat")
    public double lat;

    @SerializedName("lon")
    public double lon;

    public DoctorEntry(String name, String locationName, double lat, double lon) {
        this.name = name;
        this.locationName = locationName;
        this.lat = lat;
        this.lon = lon;
    }
}
