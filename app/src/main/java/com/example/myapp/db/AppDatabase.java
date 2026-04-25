package com.example.myapp.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import androidx.room.TypeConverters;

@Database(entities = {LocationEntry.class, DayObjectiveEntity.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract LocationDao locationDao();
    public abstract ObjectiveDao objectiveDao();

    // ── Singleton ────────────────────────────────────────────────────────────

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "app_database"
                            )
                            .fallbackToDestructiveMigration() // safe for dev; replace with Migrations in production
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
