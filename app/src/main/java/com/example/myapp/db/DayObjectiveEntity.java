package com.example.myapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.example.myapp.network.DoctorEntry;
import java.util.List;

@Entity(tableName = "failed_objectives")
public class DayObjectiveEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String device_id;
    public String username;
    public String date;
    public String trip_type;
    public String trip_description;
    public String timestamp;
    public List<DoctorEntry> doctors;

    public DayObjectiveEntity(String device_id, String username, String date, String trip_type, String trip_description, String timestamp, List<DoctorEntry> doctors) {
        this.device_id = device_id;
        this.username = username;
        this.date = date;
        this.trip_type = trip_type;
        this.trip_description = trip_description;
        this.timestamp = timestamp;
        this.doctors = doctors;
    }
}
