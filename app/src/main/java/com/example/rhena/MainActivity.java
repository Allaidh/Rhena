package com.example.rhena;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.woheller69.freeDroidWarn.FreeDroidWarn;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;
    private boolean isInternalNavigation = false;
    private Button btnLibrary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);

        viewPager = findViewById(R.id.viewPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        btnLibrary = findViewById(R.id.btnLibrary);

        MainViewPagerAdapter adapter = new MainViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                isInternalNavigation = true;
                switch (position) {
                    case 0:
                        bottomNavigation.setSelectedItemId(R.id.navigation_home);
                        break;
                    case 1:
                        bottomNavigation.setSelectedItemId(R.id.navigation_create);
                        break;
                    case 2:
                        bottomNavigation.setSelectedItemId(R.id.navigation_account);
                        break;
                }
                isInternalNavigation = false;
            }
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_home) {
                viewPager.setCurrentItem(0);
                btnLibrary.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.navigation_create) {
                // Only clear selection if user actually tapped the tab directly (not triggered by swipe or programmatic navigate)
                if (!isInternalNavigation) {
                    DrinkViewModel viewModel = new ViewModelProvider(this).get(DrinkViewModel.class);
                    // Check if we are currently in an "Edit" mode. If not, clear it.
                    Integer currentId = viewModel.getSelectedDrinkId().getValue();
                    if (currentId == null || currentId == -1) {
                        viewModel.clearSelection();
                    }
                }
                viewPager.setCurrentItem(1);
                btnLibrary.setVisibility(View.VISIBLE);
                return true;
            } else if (id == R.id.navigation_account) {
                viewPager.setCurrentItem(2);
                btnLibrary.setVisibility(View.GONE);
                return true;
            }
            return false;
        });

        btnLibrary.setOnClickListener(view -> {
            Intent intent = new Intent(this.getApplicationContext(), LibraryActivity.class);
            startActivityForResult(intent, 15);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == 15 && resultCode == RESULT_OK && data != null) {
            String name = data.getStringExtra("name");
            int vol = data.getIntExtra("volume", 0);
            int caf = data.getIntExtra("caffeine", 0);

            ((EditText)findViewById(R.id.etTitle)).setText(name);
            ((EditText)findViewById(R.id.etVolume)).setText(String.valueOf(vol));
            ((EditText)findViewById(R.id.etCaffeine)).setText(String.valueOf(caf));
        }
    }

    public void navigateToCreate(int drinkId) {
        isInternalNavigation = true;
        DrinkViewModel viewModel = new ViewModelProvider(this).get(DrinkViewModel.class);
        viewModel.selectDrink(drinkId);
        viewPager.setCurrentItem(1);
        isInternalNavigation = false;
    }
}