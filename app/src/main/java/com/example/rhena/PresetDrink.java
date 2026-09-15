package com.example.rhena;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class PresetDrink {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public int volume;
    public int caffeine;

    public int getVolume() {
        return volume;
    }

    public PresetDrink(String name, int volume, int caffeine) {
        this.name = name;
        this.volume = volume;
        this.caffeine = caffeine;
    }

    public int getCaffeine() {
        return caffeine;
    }

    public String getName() {
        return name;
    }
}
