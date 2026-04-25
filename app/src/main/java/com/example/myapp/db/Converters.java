package com.example.myapp.db;

import androidx.room.TypeConverter;
import com.example.myapp.network.DoctorEntry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class Converters {
    @TypeConverter
    public static List<DoctorEntry> fromString(String value) {
        Type listType = new TypeToken<List<DoctorEntry>>() {}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromList(List<DoctorEntry> list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }
}
