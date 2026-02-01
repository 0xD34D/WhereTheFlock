package com.scheffsblend.wtf.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "detections",
    indices = [Index(value = ["macAddress"], unique = true)]
)
data class Detection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "WiFi" or "Bluetooth"
    val macAddress: String,
    val name: String?,
    val uuid: String?,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val threatLevel: Int = 0, // 0-5, where 5 is highest
    val reason: String? = null, // The pattern or reason for detection
    val timestamp: Long = System.currentTimeMillis()
)
