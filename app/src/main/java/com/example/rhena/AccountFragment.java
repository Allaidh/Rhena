package com.example.rhena;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public class AccountFragment extends Fragment {

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),
            uri -> {
                if (uri != null) {
                    exportDatabase(uri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importDatabase(uri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btnExport).setOnClickListener(v -> exportLauncher.launch("rhena_database.db"));
        view.findViewById(R.id.btnImport).setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));
    }

    private void exportDatabase(Uri uri) {
        Context context = getContext();
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean success = false;
            try {
                AppDatabase.checkpoint(appContext);
                File dbFile = appContext.getDatabasePath("rhena_database");
                if (dbFile.exists()) {
                    try (InputStream is = new FileInputStream(dbFile);
                         OutputStream os = appContext.getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = is.read(buffer)) > 0) {
                                os.write(buffer, 0, length);
                            }
                            os.flush();
                            success = true;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isSuccess = success;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isSuccess) {
                        Toast.makeText(getContext(), "Database exported successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to export database", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void importDatabase(Uri uri) {
        Context context = getContext();
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean success = false;
            try {
                AppDatabase.closeDatabase();

                File dbFile = appContext.getDatabasePath("rhena_database");
                File walFile = new File(dbFile.getPath() + "-wal");
                File shmFile = new File(dbFile.getPath() + "-shm");

                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                try (InputStream is = appContext.getContentResolver().openInputStream(uri);
                     OutputStream os = new FileOutputStream(dbFile)) {
                    if (is != null) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            os.write(buffer, 0, length);
                        }
                        os.flush();
                        success = true;
                    }
                }

                if (walFile.exists()) {
                    walFile.delete();
                }
                if (shmFile.exists()) {
                    shmFile.delete();
                }

                // Touch/re-open DB to verify integrity
                AppDatabase.getDatabase(appContext);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isSuccess = success;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isSuccess) {
                        Toast.makeText(getContext(), "Database imported successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to import database", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}