package hua.dit.mobdev.micalendari.worker;

import android.content.Context;
import android.icu.text.SimpleDateFormat;

import androidx.annotation.NonNull;
import androidx.core.net.ParseException;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import hua.dit.mobdev.micalendari.crud.TaskContentProvider;
import hua.dit.mobdev.micalendari.data.AppDatabase;
import hua.dit.mobdev.micalendari.entity.Task;

// Worker class to periodically check and update task statuses in the background
public class TaskStatusWorker extends Worker {
    private final AppDatabase db;

    // Constructor required for WorkManager, initializes database instance
    public TaskStatusWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getDatabase(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Track if any task status changes occur
        boolean hasChanges = false;
        // Get only uncompleted tasks to optimize performance
        List<Task> tasks = db.taskDao().getAllUncompletedTasks();

        // Check each task's status and update if needed
        for (Task task : tasks) {
            int oldStatus = task.getStatus_id();
            updateTaskStatus(task);
            if (oldStatus != task.getStatus_id()) {
                db.taskDao().updateTask(task);
                hasChanges = true;
            }
        }

        // Notify ContentProvider observers only if changes occurred
        // This is an optimization to prevent unnecessary UI updates
        if (hasChanges) {
            getApplicationContext().getContentResolver().notifyChange(TaskContentProvider.CONTENT_URI, null);
        }

        return Result.success();
    }

    // Update task status based on current time and task schedule
    private void updateTaskStatus(Task task) {
        try {
            long currentTime = System.currentTimeMillis();
            // Using SimpleDateFormat for date parsing
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String dateTimeStr = task.getDate() + " " + task.getStartTime();
            Date taskDateTime = dateTimeFormat.parse(dateTimeStr);

            if (taskDateTime != null) {
                // Calculate task timing boundaries
                long taskStartTime = taskDateTime.getTime();
                // Convert hours to milliseconds for comparison
                long taskEndTime = taskStartTime + ((long) task.getDurationHours() * 60 * 60 * 1000);

                // Get status IDs once to avoid multiple database calls
                int expiredStatusId = db.statusDao().getIdByName("expired");
                int inProgressStatusId = db.statusDao().getIdByName("in_progress");
                int completedStatusId = db.statusDao().getIdByName("completed");
                int recordedStatusId = db.statusDao().getIdByName("recorded");

                // Only update non-completed tasks
                if (task.getStatus_id() != completedStatusId) {
                    if (currentTime >= taskEndTime) {
                        task.setStatus_id(expiredStatusId);
                    } else if (currentTime >= taskStartTime) {
                        task.setStatus_id(inProgressStatusId);
                    } else {
                        task.setStatus_id(recordedStatusId);
                    }
                }
            }
        } catch (ParseException | java.text.ParseException e) {
            e.printStackTrace();
        }
    }
}