package com.example.telemetry

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.model.RoutePoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class GpsLockState {
    SEARCHING, // Red ring
    LOCKED     // Green ring
}

enum class LaunchStage {
    IDLE,
    STAGE_RED,
    STAGE_YELLOW,
    STAGE_GREEN,
    LAUNCHED
}

data class TelemetryState(
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val totalDistanceMeters: Double = 0.0,
    val tripDurationSeconds: Long = 0L,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val speedUnit: String = "km/h", // or "mph"
    val gpsState: GpsLockState = GpsLockState.SEARCHING,
    val gpsAccuracyMeters: Float = 0f,
    val satellitesCount: Int = 0,
    val currentLatitude: Double = -6.2088,
    val currentLongitude: Double = 106.8456,
    val altitudeMeters: Double = 25.0,
    val accelerationMs2: Float = 0.0f,
    val launchStage: LaunchStage = LaunchStage.IDLE,
    val race0to60: Float? = null,
    val race0to100: Float? = null,
    val race402m: Float? = null,
    val batteryPct: Int = 96,
    val weatherTempCelsius: Int = 27,
    val weatherCondition: String = "light rain",
    val snapshotCount: Int = 0,
    val routePoints: List<RoutePoint> = emptyList(),
    val isSimulationMode: Boolean = false
)

class SpeedSensorManager(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener, LocationListener {

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState = _telemetryState.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    private var lastLocation: Location? = null
    private var lastLocationTimestamp: Long = 0L
    private var lastRecordedSpeed: Float = 0f
    private var speedSum: Double = 0.0
    private var speedCount: Long = 0L

    // Drag race tracking
    private var raceStartTime: Long = 0L
    private var isRacing: Boolean = false
    private var dragStartDistance: Double = 0.0

    // Staging countdown job
    private var launchCountdownJob: Job? = null
    private var tripTimerJob: Job? = null
    private var simulationJob: Job? = null
    private var hasAutoLaunchedOnFirstLock = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    _telemetryState.update { it.copy(batteryPct = pct) }
                }
            }
        }
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleNewLocation(it) }
        }
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (_: Exception) {}

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        try {
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {}
    }

    fun start() {
        // Register accelerometer
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Register linear acceleration if available
        val linearAcc = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        linearAcc?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        startLocationUpdates()
        startTripDurationLoop()
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        stopLocationUpdates()
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        toneGenerator?.release()
        toneGenerator = null
        launchCountdownJob?.cancel()
        tripTimerJob?.cancel()
        simulationJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
                .setMinUpdateIntervalMillis(250L)
                .setMinUpdateDistanceMeters(0.5f)
                .build()

            fusedLocationClient.requestLocationUpdates(req, fusedCallback, context.mainLooper)

            // Direct GPS provider check for instant fix / accuracy detection
            locationManager?.getProvider(LocationManager.GPS_PROVIDER)?.let {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L,
                    0.5f,
                    this,
                    context.mainLooper
                )
            }

            // Also check last known location immediately
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                handleNewLocation(it)
            } ?: fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) handleNewLocation(loc)
            }
        } catch (_: Exception) {
            // Location permissions might not yet be granted
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(fusedCallback)
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
    }

    private fun handleNewLocation(location: Location) {
        val now = System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 30f

        // Convert speed from m/s to km/h
        var rawSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        if (rawSpeedKmh < 0.8f) rawSpeedKmh = 0f // noise gate

        // GPS Lock State: accuracy <= 20m means LOCKED (Green indicator!)
        val isLocked = accuracy <= 25f
        val newGpsState = if (isLocked) GpsLockState.LOCKED else GpsLockState.SEARCHING

        val previousState = _telemetryState.value
        val isTransitionToLocked = previousState.gpsState != GpsLockState.LOCKED && newGpsState == GpsLockState.LOCKED

        // Distance accumulation
        var addedDistance = 0.0
        lastLocation?.let { prevLoc ->
            if (previousState.isRecording && !previousState.isPaused) {
                val dist = prevLoc.distanceTo(location).toDouble()
                if (dist in 0.5..300.0) { // filter teleport jitter
                    addedDistance = dist
                }
            }
        }

        lastLocation = location
        lastLocationTimestamp = now

        val updatedDistance = previousState.totalDistanceMeters + addedDistance
        val currentSpeed = rawSpeedKmh
        val newMax = maxOf(previousState.maxSpeedKmh, currentSpeed)

        if (previousState.isRecording && !previousState.isPaused && currentSpeed > 0f) {
            speedSum += currentSpeed
            speedCount++
        }
        val newAvg = if (speedCount > 0) (speedSum / speedCount).toFloat() else 0f

        // Update race benchmarks
        updateRaceMetrics(currentSpeed, updatedDistance)

        // Add to route points if recording
        val updatedRoute = if (previousState.isRecording && !previousState.isPaused) {
            val point = RoutePoint(
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = currentSpeed,
                acceleration = previousState.accelerationMs2,
                altitude = location.altitude,
                timestamp = now
            )
            previousState.routePoints + point
        } else {
            previousState.routePoints
        }

        _telemetryState.update {
            it.copy(
                currentSpeedKmh = currentSpeed,
                maxSpeedKmh = newMax,
                avgSpeedKmh = newAvg,
                totalDistanceMeters = updatedDistance,
                gpsState = newGpsState,
                gpsAccuracyMeters = accuracy,
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                altitudeMeters = location.altitude,
                routePoints = updatedRoute
            )
        }

        // Trigger drag launch sequence when GPS locks on first time
        if (isTransitionToLocked && !hasAutoLaunchedOnFirstLock) {
            hasAutoLaunchedOnFirstLock = true
            triggerStagingSequence()
        }
    }

    private fun updateRaceMetrics(speedKmh: Float, currentDistMeters: Double) {
        val now = System.currentTimeMillis()
        if (speedKmh > 1.5f && !isRacing && _telemetryState.value.isRecording) {
            isRacing = true
            raceStartTime = now
            dragStartDistance = currentDistMeters
        } else if (speedKmh < 0.5f && !_telemetryState.value.isRecording) {
            isRacing = false
        }

        if (isRacing) {
            val elapsedSec = (now - raceStartTime) / 1000f
            val sprintDist = currentDistMeters - dragStartDistance

            if (speedKmh >= 60f && _telemetryState.value.race0to60 == null) {
                _telemetryState.update { it.copy(race0to60 = elapsedSec) }
            }
            if (speedKmh >= 100f && _telemetryState.value.race0to100 == null) {
                _telemetryState.update { it.copy(race0to100 = elapsedSec) }
            }
            if (sprintDist >= 402.0 && _telemetryState.value.race402m == null) {
                _telemetryState.update { it.copy(race402m = elapsedSec) }
            }
        }
    }

    fun triggerStagingSequence() {
        launchCountdownJob?.cancel()
        launchCountdownJob = scope.launch(Dispatchers.Main) {
            // Step 1: Red LED on + 1st Beep
            _telemetryState.update { it.copy(launchStage = LaunchStage.STAGE_RED) }
            playBeep(ToneGenerator.TONE_PROP_BEEP, 250)
            vibrate(80)
            delay(1000)

            // Step 2: Yellow LED on + 2nd Beep
            _telemetryState.update { it.copy(launchStage = LaunchStage.STAGE_YELLOW) }
            playBeep(ToneGenerator.TONE_PROP_BEEP, 250)
            vibrate(80)
            delay(1000)

            // Step 3: Green LED on + 3rd Beep (higher confirmation pitch) + GO!
            _telemetryState.update { it.copy(launchStage = LaunchStage.STAGE_GREEN) }
            playBeep(ToneGenerator.TONE_PROP_BEEP2, 400)
            vibrate(250)

            // Auto-start recording as requested:
            // "setelah hijau pengguna dapat segera memacu kendaraan dan perekaman data kecepatan direkam secara akurat"
            startRecording()
            delay(1200)

            _telemetryState.update { it.copy(launchStage = LaunchStage.LAUNCHED) }
        }
    }

    private fun playBeep(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {}
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun startRecording() {
        _telemetryState.update {
            it.copy(
                isRecording = true,
                isPaused = false
            )
        }
    }

    fun pauseRecording() {
        _telemetryState.update {
            it.copy(isPaused = !it.isPaused)
        }
    }

    fun stopRecording() {
        _telemetryState.update {
            it.copy(
                isRecording = false,
                isPaused = false
            )
        }
    }

    fun resetTrip() {
        speedSum = 0.0
        speedCount = 0L
        isRacing = false
        _telemetryState.update {
            it.copy(
                currentSpeedKmh = 0f,
                maxSpeedKmh = 0f,
                avgSpeedKmh = 0f,
                totalDistanceMeters = 0.0,
                tripDurationSeconds = 0L,
                isRecording = false,
                isPaused = false,
                race0to60 = null,
                race0to100 = null,
                race402m = null,
                snapshotCount = 0,
                routePoints = emptyList(),
                launchStage = LaunchStage.IDLE
            )
        }
    }

    fun captureSnapshot() {
        vibrate(50)
        playBeep(ToneGenerator.TONE_PROP_BEEP, 100)
        _telemetryState.update {
            it.copy(snapshotCount = it.snapshotCount + 1)
        }
    }

    fun toggleSpeedUnit() {
        _telemetryState.update {
            val nextUnit = if (it.speedUnit == "km/h") "mph" else "km/h"
            it.copy(speedUnit = nextUnit)
        }
    }

    /**
     * Simulation Mode for Emulator testing:
     * Simulates vehicle acceleration, GPS lock, route progress, and sensor fusion.
     */
    fun toggleSimulation() {
        val currentSim = _telemetryState.value.isSimulationMode
        if (currentSim) {
            simulationJob?.cancel()
            _telemetryState.update { it.copy(isSimulationMode = false) }
        } else {
            _telemetryState.update {
                it.copy(
                    isSimulationMode = true,
                    gpsState = GpsLockState.LOCKED,
                    gpsAccuracyMeters = 6.5f
                )
            }
            triggerStagingSequence()
            startSimulationLoop()
        }
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = scope.launch(Dispatchers.Default) {
            var simSpeed = 0f
            var accelerating = true
            var lat = -6.2088
            var lng = 106.8456
            val step = 0.00015

            while (_telemetryState.value.isSimulationMode) {
                delay(300)
                if (accelerating) {
                    simSpeed += (3.5f + (Math.random() * 2f).toFloat())
                    if (simSpeed >= 128f) accelerating = false
                } else {
                    simSpeed -= (2.0f + (Math.random() * 1.5f).toFloat())
                    if (simSpeed <= 45f) accelerating = true
                }

                lat += step * (simSpeed / 60.0)
                lng += step * (simSpeed / 80.0)

                val simulatedLocation = Location("sim").apply {
                    latitude = lat
                    longitude = lng
                    speed = simSpeed / 3.6f
                    accuracy = 6.0f
                    time = System.currentTimeMillis()
                    altitude = 32.0
                }
                handleNewLocation(simulatedLocation)
            }
        }
    }

    private fun startTripDurationLoop() {
        tripTimerJob?.cancel()
        tripTimerJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(1000)
                val state = _telemetryState.value
                if (state.isRecording && !state.isPaused) {
                    _telemetryState.update {
                        it.copy(tripDurationSeconds = it.tripDurationSeconds + 1)
                    }
                }
            }
        }
    }

    // SensorEventListener
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val netAcc = (magnitude - SensorManager.GRAVITY_EARTH).coerceAtLeast(0f)

                _telemetryState.update { it.copy(accelerationMs2 = netAcc) }

                // Accelerometer needle smoothing:
                // When accelerating, interpolate speed slightly for instant responsiveness
                if (netAcc > 0.4f && _telemetryState.value.isRecording) {
                    val smoothedSpeed = _telemetryState.value.currentSpeedKmh + (netAcc * 0.05f)
                    val newMax = maxOf(_telemetryState.value.maxSpeedKmh, smoothedSpeed)
                    _telemetryState.update {
                        it.copy(
                            currentSpeedKmh = smoothedSpeed,
                            maxSpeedKmh = newMax
                        )
                    }
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val forwardAcc = abs(event.values[1])
                _telemetryState.update { it.copy(accelerationMs2 = forwardAcc) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // LocationListener
    override fun onLocationChanged(location: Location) {
        handleNewLocation(location)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _telemetryState.update {
                it.copy(gpsState = GpsLockState.SEARCHING, gpsAccuracyMeters = 0f)
            }
        }
    }

    private fun abs(value: Float): Float = if (value < 0f) -value else value
}
