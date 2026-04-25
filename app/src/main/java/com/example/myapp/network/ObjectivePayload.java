package com.example.myapp.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ObjectivePayload {
    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("username")
    public String username;

    @SerializedName("date")
    public String date;

    @SerializedName("trip_type")
    public String tripType;

    @SerializedName("timestamp")
    public String timestamp;

    @SerializedName("doctors")
    public List<DoctorPayload> doctors;

    public ObjectivePayload(String deviceId, String username, String date, String tripType, String timestamp, List<DoctorPayload> doctors) {
        this.deviceId = deviceId;
        this.username = username;
        this.date = date;
        this.tripType = tripType;
        this.timestamp = timestamp;
        this.doctors = doctors;
    }
}
