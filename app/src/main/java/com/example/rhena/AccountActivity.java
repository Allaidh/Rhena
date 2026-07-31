package com.example.rhena;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.navigation_account);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if(id == R.id.navigation_home){
                startActivity(new Intent(AccountActivity.this, MainActivity.class));
                return true;
            }
            else if(id == R.id.navigation_create){
                startActivity(new Intent(AccountActivity.this, CreateActivity.class));
                return true;
            }
            else if(id == R.id.navigation_account){
                return true;
            }
            return false;
        });
    }
}