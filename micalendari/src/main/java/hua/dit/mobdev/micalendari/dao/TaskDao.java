package hua.dit.mobdev.micalendari.dao;

import android.database.Cursor;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hua.dit.mobdev.micalendari.entity.Task;

@Dao
public interface TaskDao {

    // Basically TaskProvider Cursor
    @Query("SELECT * FROM tasks WHERE status_id = (SELECT id FROM status WHERE name = 'recorded')")
    Cursor getRecordedTasksCursor();

    // Insert a task
    @Insert
    long insertTask(Task task);

    // Get all tasks
    @Query("SELECT * FROM tasks")
    List<Task> getAllTasks();

    // Get a task by its ID
    @Query("SELECT * FROM tasks WHERE uid = :taskId")
    Task getTaskById(int taskId);

    // Update a task
    @Update
    void updateTask(Task task);

    // Delete a specific task by object
    @Delete
    void deleteTask(Task task);

    // Delete a task by its ID (optional method)
    @Query("DELETE FROM tasks WHERE uid = :taskId")
    int deleteTaskById(int taskId);

    @Query("SELECT * FROM tasks WHERE status_id != (SELECT id FROM status WHERE name = 'completed')")
    List<Task> getAllUncompletedTasks();
}
