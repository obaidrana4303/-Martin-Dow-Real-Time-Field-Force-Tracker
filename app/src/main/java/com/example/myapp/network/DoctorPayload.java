package com.example.myapp.network;

import com.google.gson.annotations.SerializedName;

public class DoctorPayload {
    @SerializedName("name")
    public String name;

    @SerializedName("location_name")
    public String locationName;

    @SerializedName("lat")
    public Double lat;

    @SerializedName("lon")
    public Double lon;

    public DoctorPayload(String name, String locationName, Double lat, Double lon) {
        this.name = name;
        this.locationName = locationName;
        this.lat = lat;
        this.lon = lon;
    }
}
