package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.RoutePoint
import com.example.telemetry.GpsLockState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

// Custom Dark Matter TileSource for OSMDroid
val CartoDarkTileSource = XYTileSource(
    "CartoDark",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap, © CARTO"
)

// Free Esri Satellite TileSource for OSMDroid
val EsriSatelliteTileSource = XYTileSource(
    "EsriSatellite",
    0, 19, 256, "",
    arrayOf(
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
    ),
    "© Esri, Maxar, Earthstar Geographics"
)

@Composable
fun OsmMapView(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float,
    gpsState: GpsLockState,
    currentSpeedKmh: Float,
    maxSpeedKmh: Float,
    headingDegrees: Float,
    locationAddress: String,
    routePoints: List<RoutePoint>,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    initialZoom: Double = 16.5
) {
    val context = LocalContext.current
    val isLocked = gpsState == GpsLockState.LOCKED

    var currentTileMode by remember { mutableIntStateOf(0) } // 0: Dark, 1: OSM Street, 2: Satellite
    var isFollowingLocation by remember { mutableStateOf(true) }

    // Remember MapView instance
    val mapView = remember {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "AlangmotorSpeedRecorder/1.0 (${context.packageName})"

        MapView(context).apply {
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setTileSource(CartoDarkTileSource)
            controller.setZoom(initialZoom)
            controller.setCenter(GeoPoint(latitude, longitude))
        }
    }

    // Lifecycle handling for MapView
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    // Update Tile Source on mode change
    LaunchedEffect(currentTileMode) {
        val newSource = when (currentTileMode) {
            0 -> CartoDarkTileSource
            1 -> TileSourceFactory.MAPNIK
            else -> EsriSatelliteTileSource
        }
        mapView.setTileSource(newSource)
        mapView.invalidate()
    }

    // Update Vehicle Marker, Accuracy Circle & Route Polyline
    LaunchedEffect(latitude, longitude, accuracyMeters, headingDegrees, isLocked, routePoints.size) {
        mapView.overlays.clear()

        val vehicleGeoPoint = GeoPoint(latitude, longitude)

        // 1. Draw Route Polyline
        if (routePoints.size >= 2) {
            val polyline = Polyline(mapView).apply {
                outlinePaint.color = AndroidColor.argb(220, 0, 229, 255) // Cyan
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                setPoints(routePoints.map { GeoPoint(it.latitude, it.longitude) })
            }
            mapView.overlays.add(polyline)
        }

        // 2. Draw Exact GPS Accuracy Circle in meters
        val accuracyCircle = Polygon(mapView).apply {
            points = Polygon.pointsAsCircle(vehicleGeoPoint, (accuracyMeters.coerceAtLeast(5f)).toDouble())
            fillPaint.color = if (isLocked) {
                AndroidColor.argb(40, 0, 230, 118) // Translucent Green
            } else {
                AndroidColor.argb(45, 255, 23, 68) // Translucent Red
            }
            outlinePaint.color = if (isLocked) {
                AndroidColor.argb(180, 0, 230, 118)
            } else {
                AndroidColor.argb(180, 255, 23, 68)
            }
            outlinePaint.strokeWidth = 3f
        }
        mapView.overlays.add(accuracyCircle)

        // 3. Draw Vehicle Position Marker
        val marker = Marker(mapView).apply {
            position = vehicleGeoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            rotation = headingDegrees
            title = "Posisi Kendaraan: ${currentSpeedKmh.toInt()} km/h"
        }
        mapView.overlays.add(marker)

        if (isFollowingLocation) {
            mapView.controller.animateTo(vehicleGeoPoint)
        }

        mapView.invalidate()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030D1A))
            .testTag("osmdroid_map_container")
    ) {
        // OSMDroid MapView
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Overlays & HUD Controls
        if (showControls) {
            // Top Address & GPS Accuracy Banner
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 40.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE031120)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B0FF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (locationAddress.isNotEmpty()) locationAddress else "OSMDroid GPS Tracker",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.US, "%.5f, %.5f", latitude, longitude),
                            color = Color(0xFF80DEEA),
                            fontSize = 11.sp
                        )
                    }

                    // Accuracy Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLocked) Color(0x3300E676) else Color(0x33FF1744))
                            .border(1.dp, if (isLocked) Color(0xFF00E676) else Color(0xFFFF1744), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isLocked) "Akurasi ±${accuracyMeters.toInt().coerceAtLeast(1)}m" else "Mencari GPS",
                            color = if (isLocked) Color(0xFF00E676) else Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Floating Live Speed Telemetry Badge (Bottom Left)
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE031222)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SPEED",
                        color = Color(0xFFFF9100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = currentSpeedKmh.toInt().toString(),
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "km/h",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp, start = 3.dp)
                        )
                    }
                    Text(
                        text = "Max: ${maxSpeedKmh.toInt()} km/h",
                        color = Color(0xFF90A4AE),
                        fontSize = 11.sp
                    )
                }
            }

            // Map Controls (Right Side)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Recenter / Follow Location Toggle
                IconButton(
                    onClick = {
                        isFollowingLocation = true
                        mapView.controller.animateTo(GeoPoint(latitude, longitude))
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isFollowingLocation) Color(0xFF00E5FF) else Color(0xDD031424))
                        .border(1.dp, Color(0xFF00E5FF), CircleShape)
                        .testTag("osm_recenter_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Posisi Saya",
                        tint = if (isFollowingLocation) Color(0xFF031424) else Color(0xFF00E5FF)
                    )
                }

                // Zoom In
                IconButton(
                    onClick = {
                        mapView.controller.zoomIn()
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xDD031424))
                        .border(1.dp, Color(0xFF00E5FF), CircleShape)
                        .testTag("osm_zoom_in")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Perbesar", tint = Color.White)
                }

                // Zoom Out
                IconButton(
                    onClick = {
                        mapView.controller.zoomOut()
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xDD031424))
                        .border(1.dp, Color(0xFF00E5FF), CircleShape)
                        .testTag("osm_zoom_out")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Perkecil", tint = Color.White)
                }

                // Tile Layer Switcher: Carto Dark -> OSM Street -> Esri Satellite
                IconButton(
                    onClick = {
                        currentTileMode = (currentTileMode + 1) % 3
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xDD031424))
                        .border(1.dp, Color(0xFFFF9100), CircleShape)
                        .testTag("osm_style_toggle")
                ) {
                    Icon(
                        imageVector = when (currentTileMode) {
                            0 -> Icons.Default.DarkMode
                            1 -> Icons.Default.Map
                            else -> Icons.Default.Public
                        },
                        contentDescription = "Ganti Tipe Peta",
                        tint = Color(0xFFFF9100)
                    )
                }
            }
        }
    }
}
