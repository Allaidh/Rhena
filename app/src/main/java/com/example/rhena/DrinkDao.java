package com.example.rhena;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DrinkDao {
    @Query("SELECT * FROM Drink ORDER BY timestamp DESC")
    List<Drink> getAll();

    @Insert
    void insert(Drink drink);

    @Update
    void update(Drink drink);

    @Query("SELECT * FROM Drink WHERE id = :id")
    Drink getById(int id);

    @Delete
    void delete(Drink drink);
}
