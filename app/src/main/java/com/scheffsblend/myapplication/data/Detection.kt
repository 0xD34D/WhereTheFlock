package com.scheffsblend.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detections")
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
