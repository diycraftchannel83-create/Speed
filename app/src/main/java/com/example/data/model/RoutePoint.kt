package com.example.data.model

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val acceleration: Float,
    val altitude: Double,
    val timestamp: Long
)
