package com.example.myapp.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ObjectiveDao {
    @Insert
    void insert(DayObjectiveEntity objective);

    @Query("SELECT * FROM failed_objectives")
    List<DayObjectiveEntity> getAllFailedObjectives();

    @Delete
    void delete(DayObjectiveEntity objective);

    @Query("DELETE FROM failed_objectives")
    void deleteAll();
}
