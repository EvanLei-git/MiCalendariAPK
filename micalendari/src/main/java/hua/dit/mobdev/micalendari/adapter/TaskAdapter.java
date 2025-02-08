package hua.dit.mobdev.micalendari.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.net.ParseException;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;

import hua.dit.mobdev.micalendari.data.AppDatabase;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import hua.dit.mobdev.micalendari.R;
import hua.dit.mobdev.micalendari.entity.Task;

// RecyclerView adapter for displaying and managing task items
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> tasks;
    private final TaskAdapterListener listener;

    // Interface for task interaction callbacks
    public interface TaskAdapterListener {
        void onTaskEdit(Task task);
        void markTaskAsCompleted(Task task, String newStatus);
    }

    private final AppDatabase db;
    // Initialize adapter with tasks, listener and database instance
    public TaskAdapter(List<Task> tasks, TaskAdapterListener listener, Context context) {
        this.tasks = tasks;
        this.listener = listener;
        this.db = AppDatabase.getDatabase(context);
        sortTasks();
    }

    // Helper method to get status ID from database
    private int getStatusIdByName(String statusName) {
        return db.statusDao().getIdByName(statusName);
    }

    // Sort tasks by priority: expired -> in_progress -> recorded -> completed
    private void sortTasks() {
        if (tasks != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                // Get status IDs once to avoid multiple database calls
                final int expiredStatusId = getStatusIdByName("expired");
                final int inProgressStatusId = getStatusIdByName("in_progress");
                final int completedStatusId = getStatusIdByName("completed");

                ArrayList<Task> sortedTasks = new ArrayList<>(tasks);
                Collections.sort(sortedTasks, (t1, t2) -> {
                    // Put completed tasks at the bottom
                    if (t1.getStatus_id() == completedStatusId && t2.getStatus_id() != completedStatusId) {
                        return 1;
                    } else if (t2.getStatus_id() == completedStatusId && t1.getStatus_id() != completedStatusId) {
                        return -1;
                    }

                    // Expired tasks get highest priority
                    if (t1.getStatus_id() == expiredStatusId && t2.getStatus_id() != expiredStatusId) {
                        return -1;
                    } else if (t2.getStatus_id() == expiredStatusId && t1.getStatus_id() != expiredStatusId) {
                        return 1;
                    }

                    // In-progress tasks come next
                    if (t1.getStatus_id() == inProgressStatusId && t2.getStatus_id() != inProgressStatusId) {
                        return -1;
                    } else if (t2.getStatus_id() == inProgressStatusId && t1.getStatus_id() != inProgressStatusId) {
                        return 1;
                    }

                    // Sort by date/time if status is the same
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                        Date date1 = sdf.parse(t1.getDate() + " " + t1.getStartTime());
                        Date date2 = sdf.parse(t2.getDate() + " " + t2.getStartTime());
                        assert date1 != null;
                        return date1.compareTo(date2);
                    } catch (ParseException e) {
                        return 0;
                    } catch (java.text.ParseException e) {
                        throw new RuntimeException(e);
                    }
                });

                // Update UI on main thread
                mainHandler.post(() -> {
                    tasks = sortedTasks;
                    notifyDataSetChanged();
                });
            });
        }
    }

    // Create new view holders
    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    // Handler for main thread UI updates
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Bind task data to view holder
    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        final int currentTaskId = task.getUid();

        // Set basic task information
        holder.taskName.setText(task.getShortName() + " (ID: " + task.getUid() + ")");
        holder.taskTime.setText("Start: " + task.getStartTime() + " (Duration: " + task.getDurationHours() + "h)");

        // Show/hide location if available
        holder.taskLocation.setVisibility(task.getLocation() != null && !task.getLocation().isEmpty() ? View.VISIBLE : View.GONE);
        if (holder.taskLocation.getVisibility() == View.VISIBLE) {
            holder.taskLocation.setText("Location: " + task.getLocation());
        }

        // Show/hide date if available
        holder.taskDate.setVisibility(task.getDate() != null && !task.getDate().isEmpty() ? View.VISIBLE : View.GONE);
        if (holder.taskDate.getVisibility() == View.VISIBLE) {
            holder.taskDate.setText("Date: " + task.getDate());
        }

        // Get status from database and update UI accordingly
        AppDatabase.databaseWriteExecutor.execute(() -> {
            final String status = db.statusDao().getNameById(task.getStatus_id());
            final Task finalTask = task;

            mainHandler.post(() -> {
                // Set status color based on task state
                int textColor;
                switch (status) {
                    case "expired":
                        textColor = Color.RED;
                        break;
                    case "in_progress":
                        textColor = Color.parseColor("#4CAF50"); // Material Green
                        break;
                    case "recorded":
                        textColor = Color.parseColor("#2196F3"); // Material Blue
                        break;
                    default:
                        textColor = Color.GRAY;
                        break;
                }

                holder.taskStatus.setTextColor(textColor);
                holder.taskStatus.setText(status);

                // Grey out completed tasks
                if (status.equals("completed")) {
                    holder.itemView.setBackgroundColor(Color.parseColor("#E0E0E0"));
                } else {
                    holder.itemView.setBackgroundColor(Color.WHITE);
                }

                // Configure complete button state
                if (status.equals("completed")) {
                    holder.completeButton.setImageResource(android.R.drawable.checkbox_on_background);
                    holder.completeButton.setEnabled(false);
                } else {
                    holder.completeButton.setImageResource(android.R.drawable.checkbox_off_background);
                    holder.completeButton.setEnabled(true);
                }

                // Set complete button click listener
                holder.completeButton.setOnClickListener(v -> {
                    if (!status.equals("completed")) {
                        Log.e("TaskAdapter", "Complete button clicked for task ID: " + currentTaskId);
                        holder.completeButton.setImageResource(android.R.drawable.checkbox_on_background);
                        listener.markTaskAsCompleted(finalTask, "completed");
                    }
                });
            });
        });

        // Set click listener for task editing
        holder.itemView.setOnClickListener(v -> listener.onTaskEdit(task));
    }

    @Override
    public int getItemCount() {
        return tasks != null ? tasks.size() : 0;
    }

    // Update task list with optimized change detection
    public void updateTasks(List<Task> newTasks) {
        if (tasks == null || newTasks == null) {
            this.tasks = newTasks != null ? new ArrayList<>(newTasks) : new ArrayList<>();
            notifyDataSetChanged();
            return;
        }

        // Check if tasks actually changed before updating
        boolean hasChanges = tasks.size() != newTasks.size();
        if (!hasChanges) {
            for (int i = 0; i < tasks.size(); i++) {
                if (!Objects.equals(tasks.get(i).getUid(), newTasks.get(i).getUid()) ||
                        tasks.get(i).getStatus_id() != newTasks.get(i).getStatus_id()) {
                    hasChanges = true;
                    break;
                }
            }
        }

        // Only sort and update if changes detected
        if (hasChanges) {
            this.tasks = new ArrayList<>(newTasks);
            sortTasks();
        }
    }

    // ViewHolder class for task items
    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView taskName;
        TextView taskTime;
        TextView taskLocation;
        TextView taskDate;
        TextView taskStatus;
        ImageButton completeButton;
        TaskViewHolder(View itemView) {
            super(itemView);
            taskName = itemView.findViewById(R.id.taskName);
            taskTime = itemView.findViewById(R.id.taskTime);
            taskLocation = itemView.findViewById(R.id.taskLocation);
            taskDate = itemView.findViewById(R.id.taskDate);
            taskStatus = itemView.findViewById(R.id.taskStatus);
            completeButton = itemView.findViewById(R.id.completeButton);
        }
    }
}