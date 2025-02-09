package hua.dit.mobdev.micalendari;

import androidx.appcompat.app.AlertDialog;

import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import android.widget.EditText;

import hua.dit.mobdev.micalendari.adapter.TaskAdapter;
import hua.dit.mobdev.micalendari.crud.TaskContentProvider;
import hua.dit.mobdev.micalendari.data.AppDatabase;
import hua.dit.mobdev.micalendari.entity.Task;
import hua.dit.mobdev.micalendari.worker.TaskStatusWorker;

// Main activity for task management, handles display and CRUD operations
public class MainActivity extends AppCompatActivity implements TaskAdapter.TaskAdapterListener {
    private TaskAdapter taskAdapter;
    private AppDatabase db;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    // Update interval for refreshing task status
    private static final long UPDATE_INTERVAL = 2000; // 2 seconds
    // Cache current tasks to optimize UI updates
    private List<Task> currentTasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getDatabase(this);
        setupRecyclerView();
        setupButtons();
        setupPeriodicWorker();
        setupPeriodicUIUpdates();
    }

    // Initialize periodic UI updates using Handler
    private void setupPeriodicUIUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                loadTasks();
                updateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentTasks.clear();
        loadTasks();
        updateHandler.post(updateRunnable);
    }

    // Stop UI updates when activity is not visible
    @Override
    protected void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }

    // Mark task as completed in background thread and refresh UI
    @Override
    public void markTaskAsCompleted(Task task, String newStatus) {
        Log.e("MainActivity", "Marking task completed: " + task.getUid());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int statusId = db.statusDao().getIdByName(newStatus);
            task.setStatus_id(statusId);
            db.taskDao().updateTask(task);
            Log.e("MainActivity", "Database updated for task: " + task.getUid());

            currentTasks.clear();
            runOnUiThread(this::loadTasks);
        });
    }

    // Initialize RecyclerView with empty adapter on background thread
    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.tasksRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        AppDatabase.databaseWriteExecutor.execute(() -> {
            taskAdapter = new TaskAdapter(new ArrayList<>(), this, this);
            mainHandler.post(() -> recyclerView.setAdapter(taskAdapter));
        });
    }

    // Set up click listeners for main action buttons
    private void setupButtons() {
        ImageButton addButton = findViewById(R.id.addButton);
        ImageButton downloadButton = findViewById(R.id.downloadButton);
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        ImageButton deleteByIdButton = findViewById(R.id.deleteByIdButton);

        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateTaskActivity.class);
            startActivity(intent);
        });

        downloadButton.setOnClickListener(v -> {
            exportTasksToHtml();
            Toast.makeText(MainActivity.this, "Exported tasks", Toast.LENGTH_SHORT).show();
        });

        settingsButton.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Testing CRUD", Toast.LENGTH_SHORT).show();
            testContentProvider();
        });

        deleteByIdButton.setOnClickListener(v -> showDeleteByIdDialog());
    }

    // Load tasks and update UI only if changes detected
    private void loadTasks() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Task> allTasks = db.taskDao().getAllUncompletedTasks();
            Log.d("TaskLoad", "Loaded " + allTasks.size() + " tasks");
            Log.d("TaskLoad", "Current tasks size: " + currentTasks.size());
            boolean needsUpdate = false;

            // Check for status changes
            for (Task task : allTasks) {
                int oldStatus = task.getStatus_id();
                updateTaskStatus(task);
                if (oldStatus != task.getStatus_id()) {
                    needsUpdate = true;
                    break;
                }
            }

            // Compare current and new tasks
            if (!needsUpdate && currentTasks.size() == allTasks.size()) {
                Log.d("TaskLoad", "Comparing tasks for changes");
                for (int i = 0; i < allTasks.size(); i++) {
                    if (!tasksAreEqual(currentTasks.get(i), allTasks.get(i))) {
                        Log.d("TaskLoad", "Found difference in task comparison");
                        needsUpdate = true;
                        break;
                    }
                }
            } else if (currentTasks.size() != allTasks.size()) {
                needsUpdate = true;
            }
            else {
                Log.d("TaskLoad", "Size mismatch or status change detected");
                needsUpdate = true;
            }

            // Update UI only if changes detected
            if (needsUpdate) {
                currentTasks = new ArrayList<>(allTasks);
                mainHandler.post(() -> taskAdapter.updateTasks(currentTasks));
            }
        });
    }

    // Compare two tasks for equality
    private boolean tasksAreEqual(Task t1, Task t2) {
        boolean equal = t1.getUid() == t2.getUid() &&
                t1.getStatus_id() == t2.getStatus_id() &&
                Objects.equals(t1.getShortName(), t2.getShortName()) &&
                Objects.equals(t1.getDescription(), t2.getDescription()) &&
                Objects.equals(t1.getStartTime(), t2.getStartTime()) &&
                Objects.equals(t1.getDate(), t2.getDate()) &&
                t1.getDurationHours() == t2.getDurationHours() &&
                Objects.equals(t1.getLocation(), t2.getLocation());


        Log.d("TaskCompare", String.format("Comparing tasks: %d == %d, names: %s == %s",
                t1.getUid(), t2.getUid(), t1.getShortName(), t2.getShortName()));

        return equal;
    }

    // Launch task edit activity with existing task data
    @Override
    public void onTaskEdit(Task task) {
        Intent intent = new Intent(this, CreateTaskActivity.class);
        intent.putExtra("task_id", task.getUid());
        intent.putExtra("task_name", task.getShortName());
        intent.putExtra("task_description", task.getDescription());
        intent.putExtra("task_date", task.getDate());
        intent.putExtra("task_start_time", task.getStartTime());
        intent.putExtra("task_duration", task.getDurationHours());
        intent.putExtra("task_location", task.getLocation());
        startActivity(intent);
        onResume();
    }

    // Update task status based on current time and task schedule
    private void updateTaskStatus(Task task) {
        try {
            long currentTime = System.currentTimeMillis();
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String dateTimeStr = task.getDate() + " " + task.getStartTime();
            Date taskDateTime = dateTimeFormat.parse(dateTimeStr);

            if (taskDateTime != null) {
                long taskStartTime = taskDateTime.getTime();
                long taskEndTime = taskStartTime + ((long) task.getDurationHours() * 60 * 60 * 1000);

                int expiredStatusId = db.statusDao().getIdByName("expired");
                int inProgressStatusId = db.statusDao().getIdByName("in_progress");
                int completedStatusId = db.statusDao().getIdByName("completed");

                // Only update non-completed tasks
                if (task.getStatus_id() != completedStatusId) {
                    if (currentTime >= taskEndTime) {
                        task.setStatus_id(expiredStatusId);
                        db.taskDao().updateTask(task);
                    } else if (currentTime >= taskStartTime) {
                        task.setStatus_id(inProgressStatusId);
                        db.taskDao().updateTask(task);
                    }
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    // Set up periodic worker for background task status updates
    private void setupPeriodicWorker() {
        PeriodicWorkRequest statusCheckRequest =
                new PeriodicWorkRequest.Builder(TaskStatusWorker.class, 1, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "taskStatusCheck",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        statusCheckRequest
                );
    }

    // Show dialog for deleting task by ID
    private void showDeleteByIdDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Task by ID");

        final EditText input = new EditText(this);
        input.setHint("Enter Task ID");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Delete", (dialog, which) -> {
            String idStr = input.getText().toString().trim();
            if (!idStr.isEmpty()) {
                deleteTaskById(Integer.parseInt(idStr));
            } else {
                Toast.makeText(MainActivity.this,
                        "No ID provided. Operation canceled.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Delete task by ID and show result toast
    private void deleteTaskById(final int taskId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            final int rowsDeleted = db.taskDao().deleteTaskById(taskId);
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                        "Deleted " + rowsDeleted + " row(s)",
                        Toast.LENGTH_SHORT).show();
                loadTasks();
            });
        });
    }

    // Export uncompleted tasks to HTML file in Downloads directory
    private void exportTasksToHtml() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Task> tasks = db.taskDao().getAllUncompletedTasks();
                StringBuilder html = new StringBuilder();
                html.append("<html><body>");
                html.append("<h1>Uncompleted Tasks</h1>");
                html.append("<table border='1'>");
                html.append("<tr><th>ID</th><th>Name</th><th>Description</th><th>Date</th><th>Time</th><th>Duration</th><th>Location</th><th>Status</th></tr>");

                for (Task task : tasks) {
                    html.append("<tr>");
                    html.append("<td>").append(task.getUid()).append("</td>");
                    html.append("<td>").append(task.getShortName()).append("</td>");
                    html.append("<td>").append(task.getDescription()).append("</td>");
                    html.append("<td>").append(task.getDate()).append("</td>");
                    html.append("<td>").append(task.getStartTime()).append("</td>");
                    html.append("<td>").append(task.getDurationHours()).append("</td>");
                    html.append("<td>").append(task.getLocation()).append("</td>");
                    html.append("<td>").append(db.statusDao().getNameById(task.getStatus_id())).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table></body></html>");

                // Set up file metadata
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "tasks.html");
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                // Write file to downloads directory
                Uri fileUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (fileUri != null) {
                    OutputStream os = getContentResolver().openOutputStream(fileUri, "w");
                    if (os != null) {
                        os.write(html.toString().getBytes());
                        os.flush();
                        os.close();
                        Log.d("Export", "File successfully created!");
                    }
                }
            } catch (Throwable t) {
                Log.e("Export", "Error exporting tasks", t);
            }
        });
    }


    private void testContentProvider() {
        new Thread(() -> {
            try {
                final Uri TASK_URI = TaskContentProvider.CONTENT_URI;
                Log.i("ContentProvider", "Using Content URI: " + TASK_URI.toString());

                // CREATE
                ContentValues values = new ContentValues();
                values.put("shortName", "Provider Test");
                values.put("description", "Task created via Content Provider");
                values.put("startTime", "10:00");
                values.put("durationHours", 2);
                values.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                values.put("location", "Test Location");
                values.put("status_id", db.statusDao().getIdByName("recorded"));

                Uri uri = getContentResolver().insert(TASK_URI, values);
                Log.i("ContentProvider", "INSERT: Created new task with URI: " + uri);
                assert uri != null;
                long taskId = ContentUris.parseId(uri);

                // Force UI refresh after creation
                currentTasks.clear();
                mainHandler.post(() -> {
                    loadTasks();
                    Toast.makeText(MainActivity.this, "Created: Provider Test", Toast.LENGTH_SHORT).show();
                });

                // Delay 5 seconds
                Thread.sleep(5000);

                // READ
                Cursor cursor = getContentResolver().query(
                        TASK_URI,
                        null,
                        null,
                        null,
                        null
                );

                if (cursor != null && cursor.moveToFirst()) {
                    int shortNameIndex = cursor.getColumnIndex("shortName");
                    int descriptionIndex = cursor.getColumnIndex("description");
                    int startTimeIndex = cursor.getColumnIndex("startTime");

                    String taskName = cursor.getString(shortNameIndex);
                    String description = cursor.getString(descriptionIndex);
                    String startTime = cursor.getString(startTimeIndex);

                    Log.i("ContentProvider", "READ: Found task: " + taskName + ", " + description + ", " + startTime);

                    mainHandler.post(() ->
                            Toast.makeText(MainActivity.this, "Read Upcoming: " + taskName, Toast.LENGTH_SHORT).show()
                    );
                    cursor.close();
                }

                // Delay 5 seconds
                Thread.sleep(5000);

                // UPDATE
                ContentValues updateValues = new ContentValues();
                updateValues.put("shortName", "Provider Updated");
                updateValues.put("description", "Updated via Content Provider");

                int updatedRows = getContentResolver().update(
                        TASK_URI,
                        updateValues,
                        "uid=?",
                        new String[]{String.valueOf(taskId)}
                );

                Log.i("ContentProvider", "UPDATE: Updated rows: " + updatedRows);

                // Force UI refresh after update
                currentTasks.clear();
                mainHandler.post(() -> {
                    loadTasks();
                    Toast.makeText(MainActivity.this, "Updated to: Provider Updated", Toast.LENGTH_SHORT).show();
                });

                // Delay 5 seconds
                Thread.sleep(5000);

                // DELETE
                int deletedRows = getContentResolver().delete(
                        TASK_URI,
                        "uid=?",
                        new String[]{String.valueOf(taskId)}
                );

                Log.i("ContentProvider", "DELETE: Deleted rows: " + deletedRows);

                // Force UI refresh after deletion
                currentTasks.clear();
                mainHandler.post(() -> {
                    loadTasks();
                    Toast.makeText(MainActivity.this, "Deleted: Provider Updated", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e("ContentProvider", "Error in content provider operations", e);
                mainHandler.post(() ->
                        Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}