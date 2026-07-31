package com.example.rhena;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseTest {
    private DrinkDao drinkDao;
    private AppDatabase db;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        drinkDao = db.drinkDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void writeDrinkAndReadInList() throws Exception {
        Drink drink = new Drink("Coffee", "Yummy", 250, 100, System.currentTimeMillis());
        drinkDao.insert(drink);
        List<Drink> allDrinks = drinkDao.getAll();
        assertEquals("Coffee", allDrinks.get(0).title);
    }
}
