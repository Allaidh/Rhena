package com.example.rhena;

import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.woheller69.freeDroidWarn.FreeDroidWarn;

public class MainActivity extends AppCompatActivity {
    private List<Drink> drinks;
    private ZoneId zone = ZoneId.systemDefault();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDrinks();
    }

    private void loadDrinks() {
        Executors.newSingleThreadExecutor().execute(() -> {
            drinks = AppDatabase.getDatabase(getApplicationContext()).drinkDao().getAll();

            LocalDate startOfWeek = LocalDate.now(zone)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Instant startInstant = startOfWeek.atStartOfDay(zone).toInstant();
            long startMillis = startInstant.toEpochMilli();

            LocalDate startOfNextWeek = startOfWeek.plusWeeks(1);
            Instant endInstant = startOfNextWeek.atStartOfDay(zone).toInstant();
            long endMillis = endInstant.toEpochMilli();

            List<Drink> drinksThisWeek = drinks.stream()
                    .filter(drink -> drink.timestamp >= startMillis && drink.timestamp < endMillis)
                    .collect(Collectors.toList());

            runOnUiThread(() -> {
                LinearLayout containerTW = findViewById(R.id.weekConsumptionContainer);
                containerTW.removeAllViews();
                LinearLayout layoutAmount = new LinearLayout(MainActivity.this);
                layoutAmount.setOrientation(VERTICAL);
                TextView amountTW = new TextView(MainActivity.this);
                amountTW.setText(String.valueOf(drinksThisWeek.size()));
                layoutAmount.addView(amountTW);
                TextView amountText = new TextView(MainActivity.this);
                amountText.setText("Drinks");
                layoutAmount.addView(amountText);
                containerTW.addView(layoutAmount);
                LinearLayout layoutCaffeine = new LinearLayout(MainActivity.this);
                layoutCaffeine.setOrientation(VERTICAL);
                TextView caffeineTW = new TextView(MainActivity.this);
                caffeineTW.setText(String.valueOf(drinksThisWeek.stream().mapToInt(Drink::getCaffeine).sum()) + "mg");
                layoutCaffeine.addView(caffeineTW);
                TextView caffeineText = new TextView(MainActivity.this);
                caffeineText.setText("Caffeine");
                layoutCaffeine.addView(caffeineText);
                containerTW.addView(layoutCaffeine);
                LinearLayout layoutVolume = new LinearLayout(MainActivity.this);
                layoutVolume.setOrientation(VERTICAL);
                TextView volumeTW = new TextView(MainActivity.this);
                volumeTW.setText(String.valueOf(drinksThisWeek.stream().mapToInt(Drink::getVolume).sum()) + "ml");
                layoutVolume.addView(volumeTW);
                TextView volumeText = new TextView(MainActivity.this);
                volumeText.setText("Volume");
                layoutVolume.addView(volumeText);
                containerTW.addView(layoutVolume);



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

                    TextView menuButton = new TextView(MainActivity.this);
                    menuButton.setText("⋮");
                    menuButton.setTextSize(20);
                    menuButton.setPadding(16, 0, 16, 0);
                    menuButton.setOnClickListener(v -> {
                        PopupMenu popup = new PopupMenu(MainActivity.this, v);
                        popup.getMenuInflater().inflate(R.menu.drink_item_menu, popup.getMenu());
                        popup.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_delete) {
                                deleteDrink(drink);
                                return true;
                            } else if (item.getItemId() == R.id.action_edit) {
                                Intent intent = new Intent(MainActivity.this, CreateActivity.class);
                                intent.putExtra("drink_id", drink.id);
                                startActivity(intent);
                                return true;
                            }
                            return false;
                        });
                        popup.show();
                    });
                    layoutTD.addView(menuButton);

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
                    layout.setPadding(10, 10, 10, 10);
                    container.addView(layout);
                    View divider = new View(MainActivity.this);
                    int dividerHeight = (int) (getResources().getDisplayMetrics().density * 5);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerHeight);
                    divider.setLayoutParams(params);
                    android.util.TypedValue typedValue = new android.util.TypedValue();
                    if (getTheme().resolveAttribute(android.R.attr.listDivider, typedValue, true)) {
                        divider.setBackgroundResource(typedValue.resourceId);
                    }
                    container.addView(divider);
                }
            });
        });
    }

    private void deleteDrink(Drink drink) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getDatabase(getApplicationContext()).drinkDao().delete(drink);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Drink deleted", Toast.LENGTH_SHORT).show();
                loadDrinks();
            });
        });
    }
}