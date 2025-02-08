package hua.dit.mobdev.micalendari;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

import hua.dit.mobdev.micalendari.data.AppDatabase;
import hua.dit.mobdev.micalendari.entity.Task;
import android.content.Intent;
import android.net.Uri;


import android.app.DatePickerDialog;
import java.util.Calendar;
import java.util.Objects;

// Activity for creating and editing tasks
public class CreateTaskActivity extends AppCompatActivity {
    // UI elements for task input
    private EditText shortNameEditText;
    private EditText descriptionEditText;
    private EditText startTimeEditText;
    private EditText durationEditText;
    private EditText locationEditText;
    private EditText dateEditText;

    private AppDatabase db;
    // -1 indicates new task, otherwise stores existing task ID
    private int taskId = -1;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_task);

        // Enable back button in action bar
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        db = AppDatabase.getDatabase(this);

        // Initialize all EditText views
        shortNameEditText   = findViewById(R.id.shortNameEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);
        startTimeEditText   = findViewById(R.id.startTimeEditText);
        durationEditText    = findViewById(R.id.durationEditText);
        locationEditText    = findViewById(R.id.locationEditText);
        dateEditText        = findViewById(R.id.dateEditText);
        Button deleteButton = findViewById(R.id.deleteButton);
        Button testLocationButton = findViewById(R.id.testLocationButton);
        Button saveButton   = findViewById(R.id.saveButton);

        // Populate fields if editing existing task
        if (getIntent().hasExtra("task_id")) {
            taskId = getIntent().getIntExtra("task_id", -1);
            shortNameEditText.setText(getIntent().getStringExtra("task_name"));
            descriptionEditText.setText(getIntent().getStringExtra("task_description"));
            dateEditText.setText(getIntent().getStringExtra("task_date"));
            startTimeEditText.setText(getIntent().getStringExtra("task_start_time"));
            durationEditText.setText(String.valueOf(getIntent().getIntExtra("task_duration", 0)));
            locationEditText.setText(getIntent().getStringExtra("task_location"));
            setTitle("Edit Task");
            deleteButton.setVisibility(View.VISIBLE);
            testLocationButton.setText("Location");
        } else {
            setTitle("Create Task");
            deleteButton.setVisibility(View.GONE);
        }

        // Setup time picker dialog for start time field
        startTimeEditText.setOnClickListener(v -> showTimePickerDialog());

        // Setup save functionality
        saveButton.setOnClickListener(v -> saveTask());

        // Setup delete functionality
        deleteButton.setOnClickListener(v -> showDeleteConfirmation());

        // Setup date picker dialog
        dateEditText.setOnClickListener(v -> showDatePickerDialog());

        // Setup location testing with map intent
        testLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String locationStr = locationEditText.getText().toString().trim();
                if (locationStr.isEmpty()) {
                    Toast.makeText(CreateTaskActivity.this,
                            "Please enter a location first",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Create map intent with location query
                Uri locationUri = Uri.parse("geo:0,0?q=" + locationStr);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, locationUri);

                // Launch map app if available
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Toast.makeText(CreateTaskActivity.this,
                            "No application found to handle map requests!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Handle action bar back button
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Show confirmation dialog when back pressed with data
    @Override
    public void onBackPressed() {
        if (hasAnyFieldFilled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Discard Event")
                    .setMessage("Are you sure you want to discard this event?")
                    .setPositiveButton("Discard", (dialog, which) -> super.onBackPressed())
                    .setNegativeButton("Keep Editing", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    // Check if any fields contain data
    private boolean hasAnyFieldFilled() {
        return !TextUtils.isEmpty(shortNameEditText.getText()) ||
                !TextUtils.isEmpty(descriptionEditText.getText()) ||
                !TextUtils.isEmpty(startTimeEditText.getText()) ||
                !TextUtils.isEmpty(durationEditText.getText()) ||
                !TextUtils.isEmpty(locationEditText.getText());
    }

    // Show delete confirmation dialog
    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTask())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Delete task from database
    private void deleteTask() {
        if (taskId != -1) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                Task task = db.taskDao().getTaskById(taskId); // Fetch task by ID
                if (task != null) {
                    db.taskDao().deleteTask(task); // Delete the task
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show()
                    );
                }
            });
        }
    }

    // Show time picker dialog
    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> startTimeEditText.setText(
                        String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                ),
                0, 0, true);
        timePickerDialog.show();
    }

    // Show date picker dialog
    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, monthOfYear, dayOfMonth) -> {
                    String date = String.format(Locale.getDefault(), "%02d/%02d/%d",
                            dayOfMonth, monthOfYear + 1, selectedYear);
                    dateEditText.setText(date);
                }, year, month, day);
        datePickerDialog.show();
    }

    // Handler for UI updates
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Save task to database
    private void saveTask() {
        if (!validateInput()) {
            return;
        }

        // Get values from input fields
        String shortName = shortNameEditText.getText().toString();
        String description = descriptionEditText.getText().toString();
        String startTime = startTimeEditText.getText().toString();
        int duration = Integer.parseInt(durationEditText.getText().toString());
        String location = locationEditText.getText().toString();
        String date = dateEditText.getText().toString();

        // Perform database operation in background
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int statusId = db.statusDao().getIdByName("recorded");

            Task task = new Task(shortName, description, startTime, duration, location, date, statusId);
            if (taskId != -1) {
                task.setUid(taskId);
            }

            // Update or insert based on whether editing existing task
            if (taskId != -1) {
                db.taskDao().updateTask(task);
            } else {
                db.taskDao().insertTask(task);
            }

            // Show success message and finish activity
            mainHandler.post(() -> {
                Toast.makeText(CreateTaskActivity.this,
                        taskId != -1 ? "Task updated" : "Task saved",
                        Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    // Validate all input fields
    private boolean validateInput() {
        if (TextUtils.isEmpty(shortNameEditText.getText())) {
            shortNameEditText.setError("Short name is required");
            return false;
        }

        if (TextUtils.isEmpty(startTimeEditText.getText())) {
            startTimeEditText.setError("Start time is required");
            return false;
        }

        String durationStr = durationEditText.getText().toString();
        if (TextUtils.isEmpty(durationStr)) {
            durationEditText.setError("Duration is required");
            return false;
        }

        try {
            int duration = Integer.parseInt(durationStr);
            if (duration <= 0) {
                durationEditText.setError("Duration must be positive");
                return false;
            }
        } catch (NumberFormatException e) {
            durationEditText.setError("Invalid duration");
            return false;
        }

        return true;
    }
}
