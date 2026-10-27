package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val maxSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val race0to60Seconds: Float? = null,
    val race0to100Seconds: Float? = null,
    val snapshotCount: Int = 0,
    val routePointsJson: String = "[]",
    val weatherCondition: String = "Clear",
    val temperatureCelsius: Int = 27
)
