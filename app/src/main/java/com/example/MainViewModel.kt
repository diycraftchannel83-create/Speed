package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.TripDatabase
import com.example.data.model.RoutePoint
import com.example.data.model.TripEntity
import com.example.export.TripExporter
import com.example.telemetry.SpeedSensorManager
import com.example.telemetry.TelemetryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SelectedMapTrip(
    val title: String,
    val points: List<RoutePoint>,
    val maxSpeed: Float,
    val avgSpeed: Float,
    val distanceMeters: Double,
    val tripEntity: TripEntity?
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TripDatabase.getInstance(application)
    private val tripDao = db.tripDao()

    val sensorManager = SpeedSensorManager(application, viewModelScope)
    val telemetryState = sensorManager.telemetryState

    val tripsList = tripDao.getAllTrips().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedMapTrip = MutableStateFlow<SelectedMapTrip?>(null)
    val selectedMapTrip = _selectedMapTrip.asStateFlow()

    private val _showHistorySheet = MutableStateFlow(false)
    val showHistorySheet = _showHistorySheet.asStateFlow()

    private var currentTripStartTime: Long = 0L

    init {
        sensorManager.start()
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stop()
    }

    fun startTripRecording() {
        currentTripStartTime = System.currentTimeMillis()
        sensorManager.startRecording()
    }

    fun pauseTripRecording() {
        sensorManager.pauseRecording()
    }

    fun stopAndSaveTrip() {
        val state = telemetryState.value
        val endTime = System.currentTimeMillis()
        val startTime = if (currentTripStartTime > 0) currentTripStartTime else (endTime - state.tripDurationSeconds * 1000)

        sensorManager.stopRecording()

        // Only save if meaningful distance or duration
        if (state.totalDistanceMeters > 5 || state.tripDurationSeconds > 3 || state.routePoints.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val jsonArray = JSONArray()
                state.routePoints.forEach { pt ->
                    val obj = JSONObject().apply {
                        put("latitude", pt.latitude)
                        put("longitude", pt.longitude)
                        put("speedKmh", pt.speedKmh)
                        put("acceleration", pt.acceleration)
                        put("altitude", pt.altitude)
                        put("timestamp", pt.timestamp)
                    }
                    jsonArray.put(obj)
                }

                val titleFormat = SimpleDateFormat("Ride HH:mm - dd MMM", Locale.getDefault())
                val trip = TripEntity(
                    title = titleFormat.format(Date(startTime)),
                    startTime = startTime,
                    endTime = endTime,
                    maxSpeedKmh = state.maxSpeedKmh,
                    avgSpeedKmh = state.avgSpeedKmh,
                    distanceMeters = state.totalDistanceMeters,
                    durationSeconds = state.tripDurationSeconds,
                    race0to60Seconds = state.race0to60,
                    race0to100Seconds = state.race0to100,
                    snapshotCount = state.snapshotCount,
                    routePointsJson = jsonArray.toString(),
                    weatherCondition = state.weatherCondition,
                    temperatureCelsius = state.weatherTempCelsius
                )

                tripDao.insertTrip(trip)
            }
        }
    }

    fun resetTrip() {
        currentTripStartTime = 0L
        sensorManager.resetTrip()
    }

    fun triggerLaunchStaging() {
        sensorManager.triggerStagingSequence()
    }

    fun captureSnapshot() {
        sensorManager.captureSnapshot()
    }

    fun toggleSpeedUnit() {
        sensorManager.toggleSpeedUnit()
    }

    fun toggleSimulation() {
        sensorManager.toggleSimulation()
    }

    fun openHistorySheet() {
        _showHistorySheet.value = true
    }

    fun closeHistorySheet() {
        _showHistorySheet.value = false
    }

    fun viewActiveRouteMap() {
        val state = telemetryState.value
        _selectedMapTrip.value = SelectedMapTrip(
            title = "Active Ride Telemetry",
            points = state.routePoints,
            maxSpeed = state.maxSpeedKmh,
            avgSpeed = state.avgSpeedKmh,
            distanceMeters = state.totalDistanceMeters,
            tripEntity = null
        )
    }

    fun viewHistoricalTripMap(trip: TripEntity) {
        val points = TripExporter.parsePoints(trip.routePointsJson)
        _selectedMapTrip.value = SelectedMapTrip(
            title = trip.title,
            points = points,
            maxSpeed = trip.maxSpeedKmh,
            avgSpeed = trip.avgSpeedKmh,
            distanceMeters = trip.distanceMeters,
            tripEntity = trip
        )
        _showHistorySheet.value = false
    }

    fun closeRouteMap() {
        _selectedMapTrip.value = null
    }

    fun exportTripPdf(context: Context, trip: TripEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = TripExporter.exportToPdf(context, trip)
            TripExporter.shareFile(context, file, "application/pdf", "Alangmotor Telemetry - ${trip.title}")
        }
    }

    fun exportTripCsv(context: Context, trip: TripEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = TripExporter.exportToCsv(context, trip)
            TripExporter.shareFile(context, file, "text/csv", "Alangmotor Telemetry - ${trip.title}")
        }
    }

    fun exportCurrentActivePdf(context: Context) {
        val state = telemetryState.value
        val jsonArray = JSONArray()
        state.routePoints.forEach { pt ->
            val obj = JSONObject().apply {
                put("latitude", pt.latitude)
                put("longitude", pt.longitude)
                put("speedKmh", pt.speedKmh)
                put("acceleration", pt.acceleration)
                put("altitude", pt.altitude)
                put("timestamp", pt.timestamp)
            }
            jsonArray.put(obj)
        }

        val tempTrip = TripEntity(
            title = "Live Ride Report",
            startTime = System.currentTimeMillis() - state.tripDurationSeconds * 1000,
            endTime = System.currentTimeMillis(),
            maxSpeedKmh = state.maxSpeedKmh,
            avgSpeedKmh = state.avgSpeedKmh,
            distanceMeters = state.totalDistanceMeters,
            durationSeconds = state.tripDurationSeconds,
            race0to60Seconds = state.race0to60,
            race0to100Seconds = state.race0to100,
            snapshotCount = state.snapshotCount,
            routePointsJson = jsonArray.toString(),
            weatherCondition = state.weatherCondition,
            temperatureCelsius = state.weatherTempCelsius
        )
        exportTripPdf(context, tempTrip)
    }

    fun exportCurrentActiveCsv(context: Context) {
        val state = telemetryState.value
        val jsonArray = JSONArray()
        state.routePoints.forEach { pt ->
            val obj = JSONObject().apply {
                put("latitude", pt.latitude)
                put("longitude", pt.longitude)
                put("speedKmh", pt.speedKmh)
                put("acceleration", pt.acceleration)
                put("altitude", pt.altitude)
                put("timestamp", pt.timestamp)
            }
            jsonArray.put(obj)
        }

        val tempTrip = TripEntity(
            title = "Live Ride Log",
            startTime = System.currentTimeMillis() - state.tripDurationSeconds * 1000,
            endTime = System.currentTimeMillis(),
            maxSpeedKmh = state.maxSpeedKmh,
            avgSpeedKmh = state.avgSpeedKmh,
            distanceMeters = state.totalDistanceMeters,
            durationSeconds = state.tripDurationSeconds,
            race0to60Seconds = state.race0to60,
            race0to100Seconds = state.race0to100,
            snapshotCount = state.snapshotCount,
            routePointsJson = jsonArray.toString(),
            weatherCondition = state.weatherCondition,
            temperatureCelsius = state.weatherTempCelsius
        )
        exportTripCsv(context, tempTrip)
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            tripDao.deleteTrip(trip)
        }
    }
}
