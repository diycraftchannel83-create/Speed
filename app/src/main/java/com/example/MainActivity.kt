package com.example

import android.Manifest
import android.os.Bundle
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.components.RacePodView
import com.example.ui.components.RouteMapView
import com.example.ui.components.TripCardView
import com.example.ui.components.WeatherWidget
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DashboardViewMode {
    COCKPIT,
    LIVE_MAP
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

        // Handle Back button when in Live Map mode
        if (activeViewMode == DashboardViewMode.LIVE_MAP) {
            BackHandler {
                activeViewMode = DashboardViewMode.COCKPIT
            }
        }

        if (activeViewMode == DashboardViewMode.LIVE_MAP) {
            // FULL LIVE GPS MAP VIEW
            Box(modifier = Modifier.fillMaxSize()) {
                GpsMapView(
                    latitude = telemetryState.currentLatitude,
                    longitude = telemetryState.currentLongitude,
                    accuracyMeters = telemetryState.gpsAccuracyMeters,
                    gpsState = telemetryState.gpsState,
                    currentSpeedKmh = telemetryState.currentSpeedKmh,
                    maxSpeedKmh = telemetryState.maxSpeedKmh,
                    headingDegrees = telemetryState.headingDegrees,
                    locationAddress = telemetryState.locationAddress,
                    routePoints = telemetryState.routePoints,
                    isInteractive = true,
                    showControls = true,
                    initialZoom = 16
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
        } else {
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

                        // Center: Segmented View Switcher: [ SPIDOMETER | PETA ]
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("top_switch_to_map")
                            ) {
                                Text(
                                    text = "PETA",
                                    color = Color(0xFF80DEEA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right: Date & Day + Sim Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (telemetryState.isSimulationMode) Color(0x4400E676) else Color(0x2200E5FF))
                                    .clickable { viewModel.toggleSimulation() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .testTag("sim_mode_toggle")
                            ) {
                                Text(
                                    text = if (telemetryState.isSimulationMode) "SIM ON" else "SIM",
                                    color = if (telemetryState.isSimulationMode) Color(0xFF00E676) else Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
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
                                onClick = {
                                    // Tap GPS Pod to open Live Map or trigger launch
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
                                        "GPS LOCKED (±${telemetryState.gpsAccuracyMeters.toInt()}m) • TAP GPS UNTUK PETA"
                                    else
                                        "MENCARI SINYAL GPS...",
                                    color = if (telemetryState.gpsState == GpsLockState.LOCKED) Color(0xFF00E676) else Color(0xFFFF5252),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // 5. BOTTOM SECTION: WEATHER (Left) + RACE POD (Center) + LIVE MAP BUTTON & BATTERY (Right)
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

                        // Right: Prominent Live Map Button + Battery Indicator
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            // High-visibility Live Map Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF00B0FF))
                                    .clickable { activeViewMode = DashboardViewMode.LIVE_MAP }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("open_route_map_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = "Buka Peta",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "PETA GPS",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            // Battery Level
                            BatteryWidget(batteryPct = telemetryState.batteryPct)
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
