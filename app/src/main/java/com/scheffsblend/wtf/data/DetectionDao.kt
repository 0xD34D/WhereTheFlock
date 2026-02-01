package com.scheffsblend.wtf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detection: Detection)

    @Delete
    suspend fun delete(detection: Detection)

    @Query("SELECT * FROM detections ORDER BY timestamp DESC")
    fun getAllDetections(): Flow<List<Detection>>

    @Query("DELETE FROM detections")
    suspend fun deleteAll()

    @Query("SELECT * FROM detections WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getByMacAddress(macAddress: String): Detection?
}
