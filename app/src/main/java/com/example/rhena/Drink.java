package com.example.rhena;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Drink {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public String description;
    public int volume;
    public int caffeine;
    public long timestamp;
//    public byte[] imageBlob;

    public int getCaffeine() {
        return caffeine;
    }

    public int getVolume() {
        return volume;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Drink(String title, String description, int volume, int caffeine, long timestamp) {
        this.title = title;
        this.description = description;
        this.volume = volume;
        this.caffeine = caffeine;
        this.timestamp = timestamp;
    }
}
