package hua.dit.mobdev.micalendari.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "tasks",
        foreignKeys = @ForeignKey(
                entity = Status.class,
                parentColumns = "id",
                childColumns = "status_id"
        ),
        indices = {@Index("status_id")}
)
public class Task {
    @PrimaryKey(autoGenerate = true)
    private int uid;

    private String shortName;
    private String description;
    private String startTime;
    private int durationHours;
    private String location;
    private String date;
    private int status_id; // Foreign key to Status table

    // Default no-arg constructor (used by Room)
    public Task() {}

    // Constructor to initialize all fields
    @Ignore
    public Task(String shortName, String description, String startTime, int durationHours, String location, String date, int status_id) {
        this.shortName = shortName;
        this.description = description;
        this.startTime = startTime;
        this.durationHours = durationHours;
        this.location = location;
        this.date = date;
        this.status_id = status_id;
    }

    // Getters and setters
    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(int durationHours) {
        this.durationHours = durationHours;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getStatus_id() {
        return status_id;
    }

    public void setStatus_id(int status_id) {
        this.status_id = status_id;
    }
}
