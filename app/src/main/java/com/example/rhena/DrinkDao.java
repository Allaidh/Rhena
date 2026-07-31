package com.example.rhena;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DrinkDao {
    @Query("SELECT * FROM Drink ORDER BY timestamp DESC")
    List<Drink> getAll();

    @Insert
    void insert(Drink drink);

    @Delete
    void delete(Drink drink);
}
