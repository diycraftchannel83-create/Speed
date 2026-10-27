package com.example

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telemetry.GpsLockState
import com.example.telemetry.LaunchStage
import com.example.ui.TripHistorySheet
import com.example.ui.components.AverageSpeedPodView
import com.example.ui.components.BatteryWidget
import com.example.ui.components.GaugeView
import com.example.ui.components.GpsMapView
import com.example.ui.components.GpsPodView
import com.example.ui.components.LaunchLedBar
import com.example.ui.components.OsmMapView
import com.example.ui.components.RacePodView
import com.example.ui.components.RouteMapView
import com.example.ui.components.SpeedOverTimeChart
import com.example.ui.components.TripCardView
import com.example.ui.components.WeatherWidget
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DashboardViewMode {
    COCKPIT,
    LIVE_MAP,
    CHART
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val telemetryState by viewModel.telemetryState.collectAsStateWithLifecycle()
    val tripsList by viewModel.tripsList.collectAsStateWithLifecycle()
    val selectedMapTrip by viewModel.selectedMapTrip.collectAsStateWithLifecycle()
    val showHistorySheet by viewModel.showHistorySheet.collectAsStateWithLifecycle()

    var activeViewMode by remember { mutableStateOf(DashboardViewMode.COCKPIT) }

    val activity = context as? Activity
    val isRecordingActive = telemetryState.isRecording && !telemetryState.isPaused

    // Keep screen on during recording and prevent sleep mode
    DisposableEffect(isRecordingActive) {
        if (isRecordingActive) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Request Location Permissions automatically on start
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled; sensors start automatically
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Live clock for top bar
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }
    var currentDayString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("H.mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

        while (true) {
            val now = Date()
            currentTimeString = timeFormat.format(now)
            currentDateString = dateFormat.format(now)
            currentDayString = dayFormat.format(now)
            delay(1000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF020912)
    ) {
        // If user is viewing Saved Historical Trip Route Map
        selectedMapTrip?.let { mapTrip ->
            BackHandler {
                viewModel.closeRouteMap()
            }
                RouteMapView(
                    title = mapTrip.title,
                    routePoints = mapTrip.points,
                    maxSpeed = mapTrip.maxSpeed,
                    avgSpeed = mapTrip.avgSpeed,
                    distanceMeters = mapTrip.distanceMeters,
                    unit = telemetryState.speedUnit,
                    onBack = { viewModel.closeRouteMap() },
                onExportPdf = {
                    if (mapTrip.tripEntity != null) {
                        viewModel.exportTripPdf(context, mapTrip.tripEntity)
                    } else {
                        viewModel.exportCurrentActivePdf(context)
                    }
                },
                onExportCsv = {
                    if (mapTrip.tripEntity != null) {
                        viewModel.exportTripCsv(context, mapTrip.tripEntity)
                    } else {
                        viewModel.exportCurrentActiveCsv(context)
                    }
                }
            )
            return@Surface
        }

        // Handle Back button when in Live Map or Chart mode
        if (activeViewMode != DashboardViewMode.COCKPIT) {
            BackHandler {
                activeViewMode = DashboardViewMode.COCKPIT
            }
        }

        when (activeViewMode) {
            DashboardViewMode.LIVE_MAP -> {
                // FULL LIVE GPS MAP VIEW
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmMapView(
                        latitude = telemetryState.currentLatitude,
                        longitude = telemetryState.currentLongitude,
                        accuracyMeters = telemetryState.gpsAccuracyMeters,
                        gpsState = telemetryState.gpsState,
                        currentSpeedKmh = telemetryState.currentSpeedKmh,
                        maxSpeedKmh = telemetryState.maxSpeedKmh,
                        headingDegrees = telemetryState.headingDegrees,
                        locationAddress = telemetryState.locationAddress,
                        routePoints = telemetryState.routePoints,
                        showControls = true,
                        initialZoom = 16.5
                    )

                    // Top View Switcher & Trip Status Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back to Cockpit Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xDD031424))
                                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                                .clickable { activeViewMode = DashboardViewMode.COCKPIT }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("back_to_cockpit_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Cockpit",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "KOKPIT",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Export / Share Actions on Map
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // View Chart from Map
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xDD00E5FF))
                                    .clickable { activeViewMode = DashboardViewMode.CHART }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ShowChart, contentDescription = "Grafik", tint = Color(0xFF031424), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GRAFIK", color = Color(0xFF031424), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xDD00B0FF))
                                    .clickable { viewModel.exportCurrentActivePdf(context) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("map_export_pdf_quick")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xDDFF9100))
                                    .clickable { viewModel.exportCurrentActiveCsv(context) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("map_export_csv_quick")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TableChart, contentDescription = "CSV", tint = Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CSV", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            DashboardViewMode.CHART -> {
                // SPEED OVER TIME CHART VIEW
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF020B16))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top Navigation in Chart
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF071C2E))
                                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                                    .clickable { activeViewMode = DashboardViewMode.COCKPIT }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = "Kokpit", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("KOKPIT", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Unit Switcher (km/jam vs mil/jam)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0D2842))
                                    .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleSpeedUnit() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (telemetryState.speedUnit == "km/h") "MODE: KM/JAM (m)" else "MODE: MIL/JAM (ft)",
                                    color = Color(0xFFFF9100),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Speed Over Time Chart Component
                        SpeedOverTimeChart(
                            routePoints = telemetryState.routePoints,
                            currentSpeedKmh = telemetryState.currentSpeedKmh,
                            maxSpeedKmh = telemetryState.maxSpeedKmh,
                            avgSpeedKmh = telemetryState.avgSpeedKmh,
                            unit = telemetryState.speedUnit,
                            durationSeconds = telemetryState.tripDurationSeconds,
                            onClose = { activeViewMode = DashboardViewMode.COCKPIT }
                        )

                        // Live Telemetry Breakdown Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF071B2D)),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0E3E64)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "STATISTIK SESI AKTIF",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                val speedMult = if (telemetryState.speedUnit == "km/h") 1f else 0.621371f
                                val distMult = if (telemetryState.speedUnit == "km/h") 0.001 else 0.000621371
                                val distUnit = if (telemetryState.speedUnit == "km/h") "km" else "mil"
                                val spdUnit = if (telemetryState.speedUnit == "km/h") "km/h" else "mph"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("DURASI WAKTU", color = Color(0xFF78909C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        val s = telemetryState.tripDurationSeconds
                                        Text(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    }
                                    Column {
                                        Text("JARAK TEMPUH", color = Color(0xFF78909C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(String.format(Locale.US, "%.2f %s", telemetryState.totalDistanceMeters * distMult, distUnit), color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    }
                                    Column {
                                        Text("PUNCAK SPEED", color = Color(0xFF78909C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(String.format(Locale.US, "%.1f %s", telemetryState.maxSpeedKmh * speedMult, spdUnit), color = Color(0xFFFF1744), fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }

                        // Export Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.exportCurrentActivePdf(context) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ekspor PDF", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.exportCurrentActiveCsv(context) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = "CSV", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ekspor CSV", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            DashboardViewMode.COCKPIT -> {
                // SPEEDOMETER COCKPIT DASHBOARD (Matching Reference Image)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF031422),
                                    Color(0xFF020C17),
                                    Color(0xFF01060C)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. TOP STATUS BAR (Matching Screenshot: ☰ 9.39   26 Oct Mon)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Hamburger Menu + Digital Time
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.openHistorySheet() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("menu_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "History & Settings",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Text(
                                    text = currentTimeString.ifEmpty { "9.39" },
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }

                            // Center: Segmented View Switcher: [ SPEED | PETA | GRAFIK ]
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF061A2B))
                                    .border(1.dp, Color(0xFF0E3E64), RoundedCornerShape(16.dp))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF00B0FF))
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "SPEED",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { activeViewMode = DashboardViewMode.LIVE_MAP }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                        .testTag("top_switch_to_map")
                                ) {
                                    Text(
                                        text = "PETA",
                                        color = Color(0xFF80DEEA),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { activeViewMode = DashboardViewMode.CHART }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                        .testTag("top_switch_to_chart")
                                ) {
                                    Text(
                                        text = "GRAFIK",
                                        color = Color(0xFFFF9100),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Right: Date & Day + Unit Toggle Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Unit Switcher (KM/JAM vs MIL/JAM)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF092138))
                                        .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(12.dp))
                                        .clickable { viewModel.toggleSpeedUnit() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .testTag("top_unit_toggle_button")
                                ) {
                                    Text(
                                        text = if (telemetryState.speedUnit == "km/h") "KM/m" else "MIL/ft",
                                        color = Color(0xFFFF9100),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = currentDateString.ifEmpty { "26 Oct" },
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentDayString.ifEmpty { "Mon" },
                                        color = Color(0xFFFF9100),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        // 2. SPEEDOMETER DIAL (Main Centerpiece)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            GaugeView(
                                speed = telemetryState.currentSpeedKmh,
                                maxSpeed = telemetryState.maxSpeedKmh,
                                unit = telemetryState.speedUnit,
                                mode = if (telemetryState.isSimulationMode) "Simulation" else "Standard",
                                snapshotCount = telemetryState.snapshotCount,
                                onUnitClick = { viewModel.toggleSpeedUnit() },
                                onSnapshotClick = { viewModel.captureSnapshot() }
                            )
                        }

                        // 3. MIDDLE SECTION: TRIP CARD (Left) + AVERAGE & GPS PODS (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Trip Card
                            TripCardView(
                                isRecording = telemetryState.isRecording,
                                isPaused = telemetryState.isPaused,
                                distanceMeters = telemetryState.totalDistanceMeters,
                                durationSeconds = telemetryState.tripDurationSeconds,
                                unit = telemetryState.speedUnit,
                                onStartRecording = { viewModel.startTripRecording() },
                                onPauseRecording = { viewModel.pauseTripRecording() },
                                onStopRecording = { viewModel.stopAndSaveTrip() },
                                onResetTrip = { viewModel.resetTrip() },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )

                            // Right: Circular Pods
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AverageSpeedPodView(
                                    avgSpeed = telemetryState.avgSpeedKmh,
                                    unit = telemetryState.speedUnit,
                                    onClick = { viewModel.openHistorySheet() }
                                )

                                GpsPodView(
                                    gpsState = telemetryState.gpsState,
                                    accuracyMeters = telemetryState.gpsAccuracyMeters,
                                    unit = telemetryState.speedUnit,
                                    onClick = {
                                        activeViewMode = DashboardViewMode.LIVE_MAP
                                    }
                                )
                            }
                        }

                        // 4. LAUNCH STAGING TREE INDICATOR (Red -> Yellow -> Green LEDs)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LaunchLedBar(
                                launchStage = telemetryState.launchStage,
                                onTapStaging = { viewModel.triggerLaunchStaging() }
                            )

                            val isImperial = telemetryState.speedUnit == "mph"
                            val accuracyNum = if (isImperial) (telemetryState.gpsAccuracyMeters * 3.28084f).toInt() else telemetryState.gpsAccuracyMeters.toInt()
                            val accuracyUnit = if (isImperial) "ft" else "m"

                            when (telemetryState.launchStage) {
                                LaunchStage.STAGE_RED -> {
                                    Text(
                                        text = "STAGING: READY...",
                                        color = Color(0xFFFF1744),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LaunchStage.STAGE_YELLOW -> {
                                    Text(
                                        text = "SET: STEADY...",
                                        color = Color(0xFFFFD600),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LaunchStage.STAGE_GREEN -> {
                                    Text(
                                        text = "GO! PEREKAMAN DIMULAI!",
                                        color = Color(0xFF00E676),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                LaunchStage.LAUNCHED -> {
                                    Text(
                                        text = "RECORDING ACTIVE",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                else -> {
                                    Text(
                                        text = if (telemetryState.gpsState == GpsLockState.LOCKED)
                                            "GPS LOCKED (±$accuracyNum$accuracyUnit) • TAP GPS UNTUK PETA"
                                        else
                                            "MENCARI SINYAL GPS...",
                                        color = if (telemetryState.gpsState == GpsLockState.LOCKED) Color(0xFF00E676) else Color(0xFFFF5252),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // 5. BOTTOM SECTION: WEATHER (Left) + RACE POD (Center) + LIVE MAP & CHART BUTTONS (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Left: Weather widget
                            WeatherWidget(
                                temperatureCelsius = telemetryState.weatherTempCelsius,
                                condition = telemetryState.weatherCondition,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            // Center: RACE pod
                            RacePodView(
                                race0to60 = telemetryState.race0to60,
                                race0to100 = telemetryState.race0to100,
                                race402m = telemetryState.race402m,
                                onClick = { activeViewMode = DashboardViewMode.LIVE_MAP }
                            )

                            // Right: Map & Chart Shortcut Buttons + Battery Indicator
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Live Chart Shortcut Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF0D2842))
                                            .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(10.dp))
                                            .clickable { activeViewMode = DashboardViewMode.CHART }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .testTag("open_chart_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ShowChart,
                                                contentDescription = "Buka Grafik",
                                                tint = Color(0xFFFF9100),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "GRAFIK",
                                                color = Color(0xFFFF9100),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Live Map Shortcut Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF00B0FF))
                                            .clickable { activeViewMode = DashboardViewMode.LIVE_MAP }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .testTag("open_route_map_button")
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Map,
                                                contentDescription = "Buka Peta",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "PETA",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Battery Level
                                BatteryWidget(batteryPct = telemetryState.batteryPct)
                            }
                        }
                    }
                }
            }
        }

        // Trip Recording History Bottom Sheet
        if (showHistorySheet) {
            TripHistorySheet(
                trips = tripsList,
                onDismiss = { viewModel.closeHistorySheet() },
                onViewRoute = { trip -> viewModel.viewHistoricalTripMap(trip) },
                onExportPdf = { trip -> viewModel.exportTripPdf(context, trip) },
                onExportCsv = { trip -> viewModel.exportTripCsv(context, trip) },
                onDeleteTrip = { trip -> viewModel.deleteTrip(trip) }
            )
        }
    }
}
