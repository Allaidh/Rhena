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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Eksport i import bazy.
 *
 * Zamiast MediaStore + Environment.getExternalStoragePublicDirectory()
 * (na Androidzie 11+ zapis do publicznego Documents zwraca EACCES,
 * a WRITE_EXTERNAL_STORAGE nic już nie daje) używamy Storage Access Framework:
 *   eksport -> ACTION_CREATE_DOCUMENT (użytkownik wskazuje gdzie zapisać)
 *   import  -> ACTION_OPEN_DOCUMENT   (użytkownik wskazuje co wczytać)
 * Nie wymaga żadnych uprawnień i działa na każdej wersji Androida.
 */
public class AccountFragment extends Fragment {

    private static final String EXPORT_MIME = "application/octet-stream";

    /**
     * Jeden wspólny wątek: eksport i import nie wejdą sobie w drogę,
     * a wątek nie jest tworzony od nowa przy każdym kliknięciu.
     */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(EXPORT_MIME),
            uri -> {
                if (uri == null) {
                    return; // użytkownik anulował
                }
                ioExecutor.execute(() -> doExport(uri));
            }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) {
                    return; // użytkownik anulował
                }
                ioExecutor.execute(() -> doImport(uri));
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btnExport)
                .setOnClickListener(v -> exportLauncher.launch(DatabaseTransfer.suggestedFileName()));
        view.findViewById(R.id.btnImport)
                .setOnClickListener(v -> importLauncher.launch(new String[]{EXPORT_MIME}));
    }

    @Override
    public void onDestroyView() {
        ioExecutor.shutdown();
        super.onDestroyView();
    }

    // ------------------------------------------------------------- eksport

    private void doExport(@NonNull Uri targetUri) {
        Context appContext = requireContext().getApplicationContext();
        String error = "Nie udało się otworzyć pliku docelowego do zapisu.";
        try (OutputStream out = appContext.getContentResolver().openOutputStream(targetUri, "rwt")) {
            if (out != null) {
                error = DatabaseTransfer.export(appContext, out);
            }
        } catch (Exception e) {
            android.util.Log.e("AccountFragment", "doExport", e);
            error = "Eksport nie powiódł się: " + e.getClass().getSimpleName();
        }

        showToast(error == null ? "Baza wyeksportowana." : error, error != null);
    }

    // -------------------------------------------------------------- import

    private void doImport(@NonNull Uri sourceUri) {
        Context appContext = requireContext().getApplicationContext();
        File staged = new File(appContext.getCacheDir(), "import_incoming.db");

        // 1. Najpierw kopiujemy wybrany plik do cache - NIE ruszamy jeszcze bazy.
        if (!copyToCache(appContext, sourceUri, staged)) {
            showToast("Nie udało się odczytać wybranego pliku.", true);
            return;
        }

        // 2. Walidacja i wgranie. Dopiero tu cokolwiek się zmienia.
        String error = DatabaseTransfer.importFrom(appContext, staged);
        staged.delete();

        if (error != null) {
            showToast("Import odrzucony: " + error, true);
            return;
        }

        // 3. Konieczny restart: InvalidationTracker tworzy swoje obiekty jako TEMP
        //    (na połączenie) i nie odtworzy ich na ponownie otwartej instancji Rooma.
        //    Bez tego LiveData rzuci "no such table: room_table_modification_log".
        showToast("Baza zaimportowana. Aplikacja zostanie uruchomiona ponownie.", false);
        DatabaseTransfer.restartAppAfterImport(appContext);
    }

    private boolean copyToCache(@NonNull Context appContext, @NonNull Uri uri, @NonNull File dest) {
        try (InputStream in = appContext.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            android.util.Log.e("AccountFragment", "copyToCache", e);
            return false;
        }
    }

    // --------------------------------------------------------------- helpers

    /** Toast odporny na odpięty fragment. */
    private void showToast(@NonNull String message, boolean isError) {
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) {
                return;
            }
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            Toast.makeText(ctx, message, isError ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        });
    }
}
