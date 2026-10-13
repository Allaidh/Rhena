package com.example.rhena;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Bezpieczny eksport / import bazy SQLite używanej przez Room.
 *
 * WAŻNE: do żywej bazy Rooma NIE WOLNO sięgać przez android.database.sqlite.SQLiteDatabase.
 * Room zwraca z getOpenHelper().getWritableDatabase() obiekt
 * androidx.sqlite.db.framework.FrameworkSQLiteDatabase, który jest OPAKOWANIEM:
 *
 *     internal class FrameworkSQLiteDatabase(private val delegate: SQLiteDatabase)
 *         : SupportSQLiteDatabase
 *
 * Czyli NIE dziedziczy po SQLiteDatabase, a pole delegate jest private.
 * Wcześniejsza wersja robiła "instanceof SQLiteDatabase" i zawsze dostawała false,
 * przez co PRAGMA wal_checkpoint(TRUNCATE) nigdy się nie wykonywała i eksport
 * oddawał plik bez danych siedzących w -wal.
 * Dlatego tutaj wszystko idzie przez interfejs SupportSQLiteDatabase.
 * SQLiteDatabase.openDatabase() służy wyłącznie do weryfikacji kopii.
 *
 * Metody są blokujące - wywołuj je z wątku w tle (Executor / coroutine IO).
 */
public final class DatabaseTransfer {

    private static final String TAG = "DatabaseTransfer";

    /** Dokładnie ta nazwa, którą podajesz w Room.databaseBuilder(..., NAZWA). */
    public static final String DB_NAME = "rhena_database";

    /**
     * SupportSQLiteDatabase.query() to metoda Kotlinowa z parametrem non-null,
     * wiec przekazanie null konczy sie
     * "Parameter specified as non-null is null: ... parameter bindArgs".
     */
    private static final Object[] NO_ARGS = new Object[0];

    /** Nagłówek każdego poprawnego pliku SQLite: "SQLite format 3\0". */
    private static final String SQLITE_MAGIC = "SQLite format 3\u0000";

    private DatabaseTransfer() {
    }

    // ---------------------------------------------------------------- export

    /**
     * Eksportuje bazę do strumienia (np. OutputStream z SAF).
     *
     * @return null gdy sukces, w przeciwnym razie czytelny powód odmowy
     */
    @Nullable
    public static String export(@NonNull Context context, @NonNull OutputStream out) {
        File workDir = new File(context.getCacheDir(), "db_transfer");
        if (!workDir.exists() && !workDir.mkdirs()) {
            return "Nie można utworzyć katalogu tymczasowego.";
        }

        File snapshot = new File(workDir, "export_snapshot.db");
        try {
            deleteQuietly(snapshot);

            // 1. Spłukanie -wal do pliku głównego. Bez tego eksport jest niekompletny.
            Log.i(TAG, "export: baza widziana przez aplikację -> " + describeLive(context));

            String checkpointError = checkpointTruncate(context);
            if (checkpointError != null) {
                return checkpointError;
            }

            File dbFile = context.getDatabasePath(DB_NAME);
            File walFile = new File(dbFile.getPath() + "-wal");
            File shmFile = new File(dbFile.getPath() + "-shm");
            Log.i(TAG, "export: źródło " + describeFile(dbFile)
                    + ", wal=" + describeFile(walFile)
                    + ", shm=" + describeFile(shmFile));

            long walBefore = walFile.exists() ? walFile.length() : 0L;

            // 2. Kopia bazy. Gdy checkpoint wyzerował -wal, wystarczy sam plik główny.
            //    Gdy nie (busy, albo checkpoint w ogóle się nie wykonał), kopiujemy -wal
            //    razem z nim: SQLite odtwarza go przy otwieraniu kopii, więc weryfikacja
            //    i eksport widzą pełne dane.
            if (!copyFile(dbFile, snapshot)) {
                return "Nie udało się przygotować kopii bazy.";
            }
            if (!snapshot.exists() || snapshot.length() == 0) {
                return "Kopia bazy jest pusta.";
            }

            File snapshotWal = new File(snapshot.getPath() + "-wal");
            if (walBefore > 0) {
                if (!copyFile(walFile, snapshotWal)) {
                    return "Nie udało się dołączyć pliku -wal do kopii bazy.";
                }
                Log.w(TAG, "export: -wal miał " + walBefore + " B i nie został spłukany,"
                        + " kopiuję go razem z bazą (snapshot wal=" + snapshotWal.length() + " B)");
            }

            // 3. Weryfikacja kopii na osobnym, tylko-do-odczytu połączeniu.
            try (SQLiteDatabase source = openReadOnly(snapshot)) {
                if (source == null) {
                    return "Nie udało się otworzyć kopii bazy do weryfikacji.";
                }
                if (!integrityOk(source)) {
                    return "Kopia bazy nie przeszła PRAGMA integrity_check.";
                }
            }

            String summary = describeDatabase(snapshot);
            Log.i(TAG, "export: snapshot " + summary);
            if (summary.contains("tabel=0")) {
                return "Wyeksportowana baza nie zawiera żadnych tabel.";
            }
            if (summary.contains("Drink=0 PresetDrink=0")) {
                return "Wyeksportowana baza ma puste tabele Drink i PresetDrink"
                        + " (plik główny " + dbFile.length() + " B)."
                        + " Jeżeli w aplikacji widzisz napoje, to znaczy że pochodzą"
                        + " z danych zaszytych w kodzie, a nie z bazy.";
            }

            // 4. Zapis do docelowego strumienia: najpierw plik główny, potem -wal.
            try (InputStream in = new FileInputStream(snapshot)) {
                copy(in, out);
            }
            if (snapshotWal.exists() && snapshotWal.length() > 0) {
                try (InputStream in = new FileInputStream(snapshotWal)) {
                    copy(in, out);
                }
            }
            out.flush();
            Log.i(TAG, "export: OK, " + snapshot.length() + " bajtów zapisane");
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "export: nie udało się", t);
            return "Eksport nie powiódł się: " + t.getClass().getSimpleName();
        } finally {
            deleteQuietly(snapshot);
            deleteQuietly(new File(snapshot.getPath() + "-wal"));
        }
    }

    /**
     * Ile wierszy widzi ŻYWE połączenie Rooma, a ile leży w pliku na dysku.
     * Rozjazd między tymi dwiema liczbami oznacza dane uwięzione w pliku -wal.
     */
    @NonNull
    private static String describeLive(@NonNull Context context) {
        SupportSQLiteDatabase live = openLiveDatabase(context);
        if (live == null) {
            return "nie udało się otworzyć bazy";
        }
        return "Drink=" + countLive(live, "SELECT COUNT(*) FROM Drink")
                + " PresetDrink=" + countLive(live, "SELECT COUNT(*) FROM PresetDrink");
    }

    private static long countLive(@NonNull SupportSQLiteDatabase live, @NonNull String sql) {
        Cursor c = null;
        try {
            c = live.query(sql, NO_ARGS);
            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
            return -1;
        } catch (Throwable t) {
            Log.w(TAG, "countLive: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return -1;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    // ---------------------------------------------------------------- import

    /**
     * Importuje bazę z pliku pobranego wcześniej przez SAF do cache.
     *
     * @param stagedFile plik wskazany przez użytkownika, już skopiowany do cacheDir
     * @return null gdy sukces, w przeciwnym razie czytelny powód odmowy
     */
    @Nullable
    public static String importFrom(@NonNull Context context, @NonNull File stagedFile) {
        try {
            Log.i(TAG, "import: wybrany plik " + describeFile(stagedFile));

            if (!stagedFile.exists() || stagedFile.length() < 100) {
                return "Plik jest pusty lub nie istnieje.";
            }

            // 1. To w ogóle plik SQLite?
            if (!hasSqliteHeader(stagedFile)) {
                return "To nie jest plik bazy danych SQLite.";
            }

            // 2. Da się otworzyć, nie jest uszkodzony, pasuje schemat?
            try (SQLiteDatabase imported = SQLiteDatabase.openDatabase(
                    stagedFile.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {

                if (!integrityOk(imported)) {
                    return "Plik bazy jest uszkodzony (PRAGMA integrity_check).";
                }

                String liveHash = readSchemaHash(context);
                String fileHash = readSchemaHash(imported);
                Log.i(TAG, "import: identity_hash aplikacja=" + liveHash + " plik=" + fileHash);

                if (liveHash == null) {
                    Log.w(TAG, "import: nie udało się odczytać hashu z żywej bazy - pomijam sprawdzenie schematu");
                } else if (fileHash == null) {
                    return "Ten plik nie pochodzi z aplikacji Rhena (brak room_master_table).";
                } else if (!liveHash.equals(fileHash)) {
                    return "Niezgodna wersja schematu.\n"
                            + "Aplikacja: " + liveHash + "\nPlik: " + fileHash + "\n"
                            + "Plik pochodzi z innej (starszej lub nowszej) wersji aplikacji.";
                }
            }

            // To najważniejsza linia diagnostyczna: ile wierszy naprawdę jest w pliku.
            Log.i(TAG, "import: zawartość pliku -> " + describeDatabase(stagedFile));

            // 3. Podmiana z kopią bezpieczeństwa.
            return restoreInto(context, stagedFile);
        } catch (Throwable t) {
            Log.e(TAG, "import: nie udało się", t);
            return "Import nie powiódł się: " + t.getClass().getSimpleName();
        }
    }

    // ------------------------------------------------------------- internals

    /**
     * PRAGMA wal_checkpoint(TRUNCATE) na żywym połączeniu Rooma.
     * Zwraca null gdy plik -wal został faktycznie spłukany i wyzerowany.
     */
    @Nullable
    private static String checkpointTruncate(@NonNull Context context) {
        SupportSQLiteDatabase live = openLiveDatabase(context);
        if (live == null) {
            return "Nie udało się otworzyć bazy do eksportu.";
        }
        Cursor c = null;
        try {
            c = live.query("PRAGMA wal_checkpoint(TRUNCATE)", NO_ARGS);
            if (c.moveToFirst()) {
                int busy = c.getInt(0);
                int log = c.getInt(1);
                Log.i(TAG, "wal_checkpoint: busy=" + busy + " log=" + log
                        + " checkpointed=" + c.getInt(2));
                if (busy != 0 || log != 0) {
                    return "Baza jest zajęta, checkpoint WAL się nie powiódł (busy="
                            + busy + ", log=" + log + "). Spróbuj ponownie za chwilę.";
                }
            } else {
                Log.i(TAG, "wal_checkpoint: brak wiersza (baza prawdopodobnie nie w trybie WAL)");
            }
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "wal_checkpoint: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return "Nie udało się spłukać pliku -wal (" + t.getClass().getSimpleName()
                    + "). Eksport byłby niekompletny.";
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** Podmiana pliku bazy z kopią bezpieczeństwa i rollbackiem. */
    @Nullable
    private static String restoreInto(@NonNull Context context, @NonNull File stagedFile) {
        File backupDir = new File(context.getCacheDir(), "db_backup");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            return "Nie można utworzyć katalogu kopii bezpieczeństwa.";
        }

        File dbFile = context.getDatabasePath(DB_NAME);
        File walFile = new File(dbFile.getPath() + "-wal");
        File shmFile = new File(dbFile.getPath() + "-shm");

        File backupDb = new File(backupDir, "current.db");
        File backupWal = new File(backupDir, "current.db-wal");
        boolean hadDb = dbFile.exists();
        boolean hadWal = walFile.exists();

        try {
            Log.i(TAG, "restoreInto: obecna baza " + describeFile(dbFile)
                    + ", wal=" + (hadWal ? walFile.length() + "B" : "brak"));

            // 1. Kopia bezpieczeństwa OBECNEJ bazy.
            deleteQuietly(backupDb);
            deleteQuietly(backupWal);
            if (hadDb && !copyFile(dbFile, backupDb)) {
                return "Nie udało się zrobić kopii bezpieczeństwa obecnej bazy.";
            }
            if (hadWal && !copyFile(walFile, backupWal)) {
                return "Nie udało się zrobić kopii bezpieczeństwa pliku WAL.";
            }

            // 2. Zamknięcie Rooma i podmiana plików.
            AppDatabase.closeDatabase();
            deleteQuietly(dbFile);
            deleteQuietly(walFile);
            deleteQuietly(shmFile);

            if (!copyFile(stagedFile, dbFile)) {
                rollback(hadDb, hadWal, backupDb, backupWal, dbFile, walFile);
                return "Nie udało się zapisać zaimportowanej bazy. Przywrócono poprzednią.";
            }

            // 3. Weryfikacja tego, co faktycznie wylądowało na dysku.
            try (SQLiteDatabase testDb = openReadOnly(dbFile)) {
                if (testDb == null || !integrityOk(testDb)) {
                    rollback(hadDb, hadWal, backupDb, backupWal, dbFile, walFile);
                    return "Zaimportowana baza nie przeszła weryfikacji. Przywrócono poprzednią.";
                }
            }

            Log.i(TAG, "restoreInto: baza po imporcie -> " + describeDatabase(dbFile));
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "restoreInto: błąd", t);
            rollback(hadDb, hadWal, backupDb, backupWal, dbFile, walFile);
            return "Import nie powiódł się: " + t.getClass().getSimpleName()
                    + ". Przywrócono poprzednią bazę.";
        }
    }

    /** Przywrócenie bazy sprzed importu. */
    private static void rollback(boolean hadDb, boolean hadWal,
                                 @NonNull File backupDb, @NonNull File backupWal,
                                 @NonNull File dbFile, @NonNull File walFile) {
        AppDatabase.closeDatabase();
        deleteQuietly(dbFile);
        deleteQuietly(walFile);
        deleteQuietly(new File(dbFile.getPath() + "-shm"));
        if (hadDb && backupDb.exists()) {
            copyFile(backupDb, dbFile);
        }
        if (hadWal && backupWal.exists()) {
            copyFile(backupWal, walFile);
        }
        Log.w(TAG, "rollback: przywrócono bazę sprzed importu");
    }

    /**
     * Restart procesu po imporcie.
     *
     * Konieczne: InvalidationTracker tworzy room_table_modification_log i triggery
     * jako obiekty TEMP (na połączenie), a jego flaga mInitialized nie jest zerowana
     * przy close(). Bez restartu LiveData rzuci
     * "no such table: room_table_modification_log", albo pokaże stare dane
     * z instancji Rooma, która nadal trzyma połączenie do usuniętego pliku.
     */
    public static void restartAppAfterImport(@NonNull Context context) {
        android.content.Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
        Log.i(TAG, "restartAppAfterImport: zamykam proces");
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    /**
     * Żywe połączenie Rooma jako SupportSQLiteDatabase.
     * UWAGA: zwraca cudze połączenie - nie wolno go zamykać.
     */
    @Nullable
    private static SupportSQLiteDatabase openLiveDatabase(@NonNull Context context) {
        try {
            RoomDatabase db = AppDatabase.getDatabase(context);
            SupportSQLiteDatabase live = db.getOpenHelper().getWritableDatabase();
            if (live == null) {
                Log.e(TAG, "openLiveDatabase: getWritableDatabase() zwróciło null");
                return null;
            }
            Log.i(TAG, "openLiveDatabase: " + live.getClass().getName() + ", path=" + live.getPath());
            return live;
        } catch (Throwable t) {
            Log.e(TAG, "openLiveDatabase: błąd", t);
            return null;
        }
    }

    @Nullable
    private static SQLiteDatabase openReadOnly(@NonNull File file) {
        try {
            return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        } catch (Throwable t) {
            Log.e(TAG, "openReadOnly: " + t.getClass().getSimpleName() + " " + t.getMessage());
            return null;
        }
    }

    /** Hash schematu Room (identity) z żywej bazy. */
    @Nullable
    private static String readSchemaHash(@NonNull Context context) {
        SupportSQLiteDatabase live = openLiveDatabase(context);
        if (live == null) {
            return null;
        }
        Cursor c = null;
        try {
            c = live.query("SELECT identity_hash FROM room_master_table LIMIT 1", NO_ARGS);
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Throwable t) {
            Log.i(TAG, "readSchemaHash: " + t.getClass().getSimpleName() + " " + t.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    /** Hash schematu Room (identity) z otwartego pliku. */
    @Nullable
    private static String readSchemaHash(@NonNull SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT identity_hash FROM room_master_table LIMIT 1", null);
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Throwable t) {
            Log.i(TAG, "readSchemaHash: " + t.getClass().getSimpleName() + " " + t.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    private static boolean integrityOk(@NonNull SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA integrity_check", null);
            return c.moveToFirst() && "ok".equalsIgnoreCase(c.getString(0));
        } catch (Throwable t) {
            Log.e(TAG, "integrityOk: błąd", t);
            return false;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** Podsumowanie zawartości pliku bazy - do logów diagnostycznych. */
    @NonNull
    private static String describeDatabase(@NonNull File file) {
        StringBuilder sb = new StringBuilder();
        sb.append(describeFile(file));
        try (SQLiteDatabase db = openReadOnly(file)) {
            if (db == null) {
                return sb.append(" [nie dało się otworzyć]").toString();
            }
            sb.append(" integrity=").append(integrityOk(db) ? "ok" : "BŁĄD");
            sb.append(" user_version=").append(scalar(db, "PRAGMA user_version"));

            java.util.List<String> tables = new java.util.ArrayList<>();
            Cursor c = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' "
                            + "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' ORDER BY name", null);
            try {
                while (c.moveToNext()) {
                    tables.add(c.getString(0));
                }
            } finally {
                c.close();
            }

            sb.append(" tabel=").append(tables.size());
            for (String t : tables) {
                sb.append(" ").append(t).append("=").append(scalar(db, "SELECT COUNT(*) FROM `" + t + "`"));
            }
        } catch (Throwable t) {
            sb.append(" [").append(t.getClass().getSimpleName()).append("]");
        }
        return sb.toString();
    }

    @NonNull
    private static String describeFile(@Nullable File file) {
        if (file == null) {
            return "null";
        }
        if (!file.exists()) {
            return file.getName() + " [nie istnieje]";
        }
        return file.getName() + " (" + file.length() + " B)";
    }

    @NonNull
    private static String scalar(@NonNull SQLiteDatabase db, @NonNull String sql) {
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            if (c.moveToFirst()) {
                return String.valueOf(c.getString(0));
            }
        } catch (Throwable t) {
            return "?";
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return "?";
    }

    /** Czysto plikowa walidacja nagłówka - bez otwierania bazy. */
    static boolean hasSqliteHeader(@NonNull File file) {
        byte[] head = new byte[16];
        try (InputStream in = new FileInputStream(file)) {
            int read = 0;
            while (read < head.length) {
                int r = in.read(head, read, head.length - read);
                if (r < 0) {
                    return false;
                }
                read += r;
            }
        } catch (IOException e) {
            return false;
        }
        return SQLITE_MAGIC.equals(new String(head, 0, 16, java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static boolean copyFile(@NonNull File src, @NonNull File dest) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            copy(in, out);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "copyFile: " + src.getName() + " -> " + dest.getName(), e);
            return false;
        }
    }

    private static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    private static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "deleteQuietly: nie usunięto " + file.getName());
        }
    }

    /** Nazwa pliku eksportu, np. rhena_database_2026-10-13.db. */
    @NonNull
    public static String suggestedFileName() {
        String date = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new java.util.Date());
        return DB_NAME + "_" + date + ".db";
    }
}
