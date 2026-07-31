package com.example.rhena;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import java.util.concurrent.Executors;

public class CreateActivity extends AppCompatActivity {

    private EditText etTitle, etDescription, etVolume, etCaffeine;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etVolume = findViewById(R.id.etVolume);
        etCaffeine = findViewById(R.id.etCaffeine);
        btnSubmit = findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(v -> {
            saveDrink();
        });

        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.navigation_create);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if(id == R.id.navigation_home){
                startActivity(new Intent(CreateActivity.this, MainActivity.class));
                return true;
            }
            else if(id == R.id.navigation_create){
                return true;
            }
            else if(id == R.id.navigation_account){
                startActivity(new Intent(CreateActivity.this, AccountActivity.class));
                return true;
            }
            return false;
        });
    }

    private void saveDrink() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String volumeStr = etVolume.getText().toString().trim();
        String caffeineStr = etCaffeine.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        int volume = 0;
        try {
            if (!volumeStr.isEmpty()) volume = Integer.parseInt(volumeStr);
        } catch (NumberFormatException ignored) {}

        int caffeine = 0;
        try {
            if (!caffeineStr.isEmpty()) caffeine = Integer.parseInt(caffeineStr);
        } catch (NumberFormatException ignored) {}

        long timestamp = System.currentTimeMillis();

        Drink drink = new Drink(title, description, volume, caffeine, timestamp);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getDatabase(getApplicationContext()).drinkDao().insert(drink);
            runOnUiThread(() -> {
                Toast.makeText(CreateActivity.this, "Drink saved!", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}