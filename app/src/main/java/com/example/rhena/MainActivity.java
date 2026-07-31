package com.example.rhena;

import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private List<Drink> drinks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.navigation_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if(id == R.id.navigation_home){
                return true;
            }
            else if(id == R.id.navigation_create){
                startActivity(new Intent(MainActivity.this, CreateActivity.class));
                return true;
            }
            else if(id == R.id.navigation_account){
                startActivity(new Intent(MainActivity.this, AccountActivity.class));
                return true;
            }
            return false;
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            drinks = AppDatabase.getDatabase(getApplicationContext()).drinkDao().getAll();
            runOnUiThread(() -> {
                LinearLayout container = findViewById(R.id.drinkHistoryContainer);
                container.removeAllViews();
                for(Drink drink : drinks) {
                    LinearLayout layout = new LinearLayout(MainActivity.this);
                    layout.setOrientation(VERTICAL);
                    layout.setPadding(0, 8, 0, 8);
                    LinearLayout layoutTD = new LinearLayout(MainActivity.this);
                    layoutTD.setOrientation(HORIZONTAL);
                    TextView title = new TextView(MainActivity.this);
                    title.setText(drink.title);
                    title.setTextSize(18);
                    title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                    layoutTD.addView(title);
                    TextView dateTv = new TextView(MainActivity.this);
                    Date drinkDate = new Date(drink.timestamp);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
                    String formattedDate = sdf.format(drinkDate);
                    dateTv.setText(formattedDate);
                    layoutTD.addView(dateTv);
                    layout.addView(layoutTD);
                    TextView desctiption = new TextView(MainActivity.this);
                    desctiption.setText(drink.description);
                    layout.addView(desctiption);
                    TextView volume = new TextView(MainActivity.this);
                    String volumeStr = "Volume (ml) " + drink.volume;
                    volume.setText(volumeStr);
                    layout.addView(volume);
                    TextView caffeine = new TextView(MainActivity.this);
                    String caffeineStr = "Caffeine (mg) " + drink.caffeine;
                    caffeine.setText(caffeineStr);
                    layout.addView(caffeine);
                    container.addView(layout);
                }
            });
        });



    }
}