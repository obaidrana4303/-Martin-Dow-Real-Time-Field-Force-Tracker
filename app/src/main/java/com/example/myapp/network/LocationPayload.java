package com.example.myapp.network;

import com.google.gson.annotations.SerializedName;

public class LocationPayload {

    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("lat")
    public double lat;

    @SerializedName("lon")
    public double lon;

    @SerializedName("mode")
    public String mode;

    @SerializedName("username")
    public String username;

    @SerializedName("timestamp")
    public String timestamp;

    public LocationPayload(String deviceId, String username, double lat, double lon, String mode, String timestamp) {
        this.deviceId = deviceId;
        this.username = username;
        this.lat = lat;
        this.lon = lon;
        this.mode = mode;
        this.timestamp = timestamp;
    }
}
