package hua.dit.mobdev.micalendari.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hua.dit.mobdev.micalendari.entity.Status;

@Dao
public interface StatusDao {

    @Query("SELECT id FROM status WHERE name = :statusName LIMIT 1")
    int getIdByName(String statusName);
    // Insert a single status row
    @Insert
    void insertStatus(Status status);


    // Get ALL statuses
    @Query("SELECT * FROM status")
    List<Status> getAllStatuses();


    @Query("SELECT name FROM status WHERE id = :statusId")
    String getNameById(int statusId);
}
