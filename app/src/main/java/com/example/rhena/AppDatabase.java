package com.example.rhena;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.Executors;

@Database(entities = {Drink.class, PresetDrink.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {
    public abstract DrinkDao drinkDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "rhena_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void checkpoint(Context context) {
        AppDatabase db = getDatabase(context);
        if (db != null && db.isOpen()) {
            try (android.database.Cursor cursor = db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL);", null)) {
                if (cursor != null) {
                    cursor.moveToFirst();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void closeDatabase() {
        if (INSTANCE != null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE != null) {
                    if (INSTANCE.isOpen()) {
                        INSTANCE.close();
                    }
                    INSTANCE = null;
                }
            }
        }
    }

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            Executors.newSingleThreadExecutor().execute(() -> {
                DrinkDao dao = INSTANCE.drinkDao();
                dao.insertPresetDrink(new PresetDrink("Monster Energy Ultra Peachy Keen", 500, 150));
                dao.insertPresetDrink(new PresetDrink("Monster Energy Full Throttle Zero Sugar", 500, 160));
            });
        }
    };
}
