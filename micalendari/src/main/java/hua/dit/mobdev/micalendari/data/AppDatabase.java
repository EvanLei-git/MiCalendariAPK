package hua.dit.mobdev.micalendari.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hua.dit.mobdev.micalendari.dao.StatusDao;
import hua.dit.mobdev.micalendari.dao.TaskDao;
import hua.dit.mobdev.micalendari.entity.Status;
import hua.dit.mobdev.micalendari.entity.Task;

// Room database class
@Database(entities = {Status.class, Task.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    // DAOs for database access
    public abstract StatusDao statusDao();
    public abstract TaskDao taskDao();

    // Singleton instance of the database
    private static volatile AppDatabase INSTANCE;

    // Thread pool for database operations
    // Using 4 threads as a balanced approach for concurrent database operations
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // Singleton pattern implementation
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "app_database")
                            // Allows database recreation instead of migration
                            .fallbackToDestructiveMigration()
                            // Initialize database with default status values
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    // Populate status table in background thread
                                    databaseWriteExecutor.execute(() -> {
                                        AppDatabase database = getDatabase(context);
                                        StatusDao sDao = database.statusDao();

                                        // Order matters here as it matches the state transition
                                        sDao.insertStatus(new Status("recorded"));
                                        sDao.insertStatus(new Status("in_progress"));
                                        sDao.insertStatus(new Status("expired"));
                                        sDao.insertStatus(new Status("completed"));
                                    });
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}