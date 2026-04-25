package com.example.myapp.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DayObjective {
    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("username")
    public String username;

    @SerializedName("date")
    public String date;

    @SerializedName("trip_type")
    public String tripType;

    @SerializedName("trip_description")
    public String tripDescription;

    @SerializedName("doctors")
    public List<DoctorEntry> doctors;

    @SerializedName("timestamp")
    public String timestamp;

    public DayObjective(String deviceId, String username, String date, String tripType, String tripDescription, List<DoctorEntry> doctors, String timestamp) {
        this.deviceId = deviceId;
        this.username = username;
        this.date = date;
        this.tripType = tripType;
        this.tripDescription = tripDescription;
        this.doctors = doctors;
        this.timestamp = timestamp;
    }
}
