package com.example.rhena;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.Executors;

public class LibraryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        recyclerView = findViewById(R.id.recyclerViewLibrary);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        loadPresets();
    }

    private void loadPresets() {
        Executors.newSingleThreadExecutor().execute(() -> {
            DrinkDao dao = AppDatabase.getDatabase(getApplicationContext()).drinkDao();
            List<PresetDrink> presetDrinks = dao.getAllPresetDrinks();
            runOnUiThread(() -> {
                PresetDrinkAdapter adapter = new PresetDrinkAdapter(presetDrinks);
                recyclerView.setAdapter(adapter);
            });
        });
    }
}
