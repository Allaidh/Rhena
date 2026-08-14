package com.example.rhena;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.Executors;

public class CreateActivity extends AppCompatActivity {

    private EditText etTitle, etDescription, etVolume, etCaffeine;
    private Button btnSubmit, btnCamera;
//    private byte[] selectedImageBlob = null;
//    private Uri cameraImageUri = null;

//    private final ActivityResultLauncher<Intent> pickGallery =
//            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
//                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
//                    Uri uri = result.getData().getData();
//                    if (uri != null) {
//                        processImage(uri);
//                    }
//                }
//            });
//
//    private final ActivityResultLauncher<Uri> takePicture =
//            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
//                if (success && cameraImageUri != null) {
//                    processImage(cameraImageUri);
//                }
//            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etVolume = findViewById(R.id.etVolume);
        etCaffeine = findViewById(R.id.etCaffeine);
        btnSubmit = findViewById(R.id.btnSubmit);
//        btnCamera = findViewById(R.id.btnCamera);

        btnSubmit.setOnClickListener(v -> {
            saveDrink();
        });

//        btnCamera.setOnClickListener(v -> {
//            showImagePickerOptions();
//        });

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

//    private void showImagePickerOptions() {
//        String[] options = {"Take Photo", "Choose from Gallery"};
//        new AlertDialog.Builder(this)
//                .setTitle("Add Photo")
//                .setItems(options, (dialog, which) -> {
//                    if (which == 0) {
//                        cameraImageUri = getCameraUri();
//                        if (cameraImageUri != null) {
//                            takePicture.launch(cameraImageUri);
//                        }
//                    } else {
//                        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
//                        pickGallery.launch(intent);
//                    }
//                })
//                .show();
//    }

//    private Uri getCameraUri() {
//        try {
//            File tempFile = File.createTempFile("camera_image", ".jpg", getCacheDir());
//            return FileProvider.getUriForFile(this, "com.example.rhena.fileprovider", tempFile);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

//    private void processImage(Uri uri) {
//        try (InputStream inputStream = getContentResolver().openInputStream(uri);
//             ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {
//            int bufferSize = 1024;
//            byte[] buffer = new byte[bufferSize];
//            int len;
//            while (inputStream != null && (len = inputStream.read(buffer)) != -1) {
//                byteBuffer.write(buffer, 0, len);
//            }
//            selectedImageBlob = byteBuffer.toByteArray();
//            btnCamera.setText("✅");
//            Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
//        }
//    }

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