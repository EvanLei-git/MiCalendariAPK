package hua.dit.mobdev.micalendari.crud;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import hua.dit.mobdev.micalendari.data.AppDatabase;
import hua.dit.mobdev.micalendari.entity.Task;

// Content Provider for external access to task data
public class TaskContentProvider extends ContentProvider {
    private static final String TAG = "TaskContentProvider";

    // Content Provider configuration
    private static final String MY_PROVIDER = "hua.dit.mobdev.micalendari.provider";
    private static final String CONTENT_URI_STR = "content://" + MY_PROVIDER + "/tasks";
    public static final Uri CONTENT_URI = Uri.parse(CONTENT_URI_STR);

    // URI matcher setup for request handling
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int URI_CODE = 1;
    static {
        uriMatcher.addURI(MY_PROVIDER, "tasks", URI_CODE);
    }

    private AppDatabase db;

    @Override
    public boolean onCreate() {
        db = AppDatabase.getDatabase(getContext());
        return true;
    }

    // Handle task insertion requests
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        Log.d(TAG, "Insert Data: uri=" + uri + " , values=" + values);
        if (uriMatcher.match(uri) == URI_CODE) {
            // Create new task from provided values
            Task task = new Task();
            task.setShortName(values.getAsString("shortName"));
            task.setDescription(values.getAsString("description"));
            task.setStartTime(values.getAsString("startTime"));
            task.setDurationHours(values.getAsInteger("durationHours"));
            task.setLocation(values.getAsString("location"));
            task.setDate(values.getAsString("date"));
            task.setStatus_id(values.getAsInteger("status_id"));

            long taskId = db.taskDao().insertTask(task);
            Log.i(TAG, "Insert Data: NEW Task ID: " + taskId);
            return ContentUris.withAppendedId(CONTENT_URI, taskId);
        }

        throw new RuntimeException("Insert Method - Not supported URI: " + uri);
    }

    // Handle task query requests
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "Query Data: uri=" + uri + " ...");
        if (uriMatcher.match(uri) == URI_CODE) {
            // Only return recorded tasks for security/simplicity
            return db.taskDao().getRecordedTasksCursor();
        }
        throw new RuntimeException("Query Method - Not supported URI: " + uri);
    }

    // Return MIME type for tasks
    @Override
    public String getType(@NonNull Uri uri) {
        if (uriMatcher.match(uri) == URI_CODE)
            return "vnd.android.cursor.dir/tasks";
        throw new RuntimeException("Get Type Method - Not supported URI: " + uri);
    }

    // Handle task update requests
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (uriMatcher.match(uri) == URI_CODE && values != null && selectionArgs != null) {
            int taskId = Integer.parseInt(selectionArgs[0]);
            Task task = db.taskDao().getTaskById(taskId);
            if (task != null) {
                // Update only provided fields
                if (values.containsKey("shortName"))
                    task.setShortName(values.getAsString("shortName"));
                if (values.containsKey("description"))
                    task.setDescription(values.getAsString("description"));
                if (values.containsKey("startTime"))
                    task.setStartTime(values.getAsString("startTime"));
                if (values.containsKey("durationHours"))
                    task.setDurationHours(values.getAsInteger("durationHours"));
                if (values.containsKey("location"))
                    task.setLocation(values.getAsString("location"));
                if (values.containsKey("date"))
                    task.setDate(values.getAsString("date"));
                if (values.containsKey("status_id"))
                    task.setStatus_id(values.getAsInteger("status_id"));

                db.taskDao().updateTask(task);
                return 1;
            }
        }
        throw new RuntimeException("Update Method - Not supported URI: " + uri);
    }

    // Handle task deletion requests
    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        if (uriMatcher.match(uri) == URI_CODE && selectionArgs != null) {
            return db.taskDao().deleteTaskById(Integer.parseInt(selectionArgs[0]));
        }
        throw new RuntimeException("Delete Method - Not supported URI: " + uri);
    }
}