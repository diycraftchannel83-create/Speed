package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.RoutePoint
import com.example.telemetry.GpsLockState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

enum class FreeMapProvider(val label: String) {
    CARTO_DARK("Dark"),
    OPEN_STREET_MAP("Jalan"),
    ESRI_SATELLITE("Satelit"),
    OPEN_TOPO("Kontur")
}

@Composable
fun GpsMapView(
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
    isInteractive: Boolean = true,
    showControls: Boolean = true,
    initialZoom: Int = 16
) {
    var zoomLevel by remember { mutableIntStateOf(initialZoom) }
    var userPanX by remember { mutableFloatStateOf(0f) }
    var userPanY by remember { mutableFloatStateOf(0f) }
    var currentProvider by remember { mutableStateOf(FreeMapProvider.CARTO_DARK) }
    var isFollowingLocation by remember { mutableStateOf(true) }

    val isLocked = gpsState == GpsLockState.LOCKED

    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF040E1B))
            .testTag("free_gps_map_view")
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val tileSizePx = with(density) { 256.dp.toPx() }

        val centerLat = latitude
        val centerLon = longitude
        val n = 2.0.pow(zoomLevel.toDouble())

        fun lonToTileX(lon: Double): Double = (lon + 180.0) / 360.0 * n
        fun latToTileY(lat: Double): Double {
            val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
            val latRad = Math.toRadians(clampedLat)
            return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        }

        val centerTileX = lonToTileX(centerLon)
        val centerTileY = latToTileY(centerLat)

        val effectivePanX = if (isFollowingLocation) 0f else userPanX
        val effectivePanY = if (isFollowingLocation) 0f else userPanY

        fun geoToScreen(lat: Double, lon: Double): Offset {
            val tx = lonToTileX(lon)
            val ty = latToTileY(lat)
            val screenX = (widthPx / 2f) + ((tx - centerTileX) * tileSizePx).toFloat() + effectivePanX
            val screenY = (heightPx / 2f) + ((ty - centerTileY) * tileSizePx).toFloat() + effectivePanY
            return Offset(screenX, screenY)
        }

        val halfW = widthPx / 2f
        val halfH = heightPx / 2f

        val minTx = floor((centerTileX - ((halfW + effectivePanX) / tileSizePx)) - 1).toInt().coerceAtLeast(0)
        val maxTx = floor((centerTileX + ((halfW - effectivePanX) / tileSizePx)) + 1).toInt().coerceAtMost(n.toInt() - 1)
        val minTy = floor((centerTileY - ((halfH + effectivePanY) / tileSizePx)) - 1).toInt().coerceAtLeast(0)
        val maxTy = floor((centerTileY + ((halfH - effectivePanY) / tileSizePx)) + 1).toInt().coerceAtMost(n.toInt() - 1)

        val tileList = remember(minTx, maxTx, minTy, maxTy, zoomLevel, currentProvider) {
            val list = mutableListOf<Triple<Int, Int, String>>()
            for (tx in minTx..maxTx) {
                for (ty in minTy..maxTy) {
                    val url = when (currentProvider) {
                        FreeMapProvider.CARTO_DARK ->
                            "https://a.basemaps.cartocdn.com/dark_all/$zoomLevel/$tx/$ty.png"
                        FreeMapProvider.OPEN_STREET_MAP ->
                            "https://tile.openstreetmap.org/$zoomLevel/$tx/$ty.png"
                        FreeMapProvider.ESRI_SATELLITE ->
                            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoomLevel/$ty/$tx"
                        FreeMapProvider.OPEN_TOPO ->
                            "https://a.tile.opentopomap.org/$zoomLevel/$tx/$ty.png"
                    }
                    list.add(Triple(tx, ty, url))
                }
            }
            list
        }

        val gestureModifier = if (isInteractive) {
            Modifier.pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    isFollowingLocation = false
                    userPanX += pan.x
                    userPanY += pan.y
                    if (zoom > 1.35f && zoomLevel < 19) {
                        zoomLevel++
                    } else if (zoom < 0.75f && zoomLevel > 4) {
                        zoomLevel--
                    }
                }
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            // 1. Free Slippy Map Tiles Layer
            Box(modifier = Modifier.fillMaxSize()) {
                val context = LocalContext.current
                tileList.forEach { (tx, ty, url) ->
                    val tileScreenX = (widthPx / 2f) + ((tx - centerTileX) * tileSizePx).toFloat() + effectivePanX
                    val tileScreenY = (heightPx / 2f) + ((ty - centerTileY) * tileSizePx).toFloat() + effectivePanY

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .addHeader("User-Agent", "AlangmotorSpeedRecorder/1.0 (Android; Mobile)")
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset { IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt()) }
                            .size(256.dp)
                    )
                }
            }

            // 2. Vector Canvas Overlay: Route Polyline, GPS Accuracy Circle & Vehicle Marker
            Canvas(modifier = Modifier.fillMaxSize()) {
                // A. Draw Route Trail (Speed colored)
                if (routePoints.size >= 2) {
                    for (i in 0 until routePoints.size - 1) {
                        val p1 = routePoints[i]
                        val p2 = routePoints[i + 1]
                        val start = geoToScreen(p1.latitude, p1.longitude)
                        val end = geoToScreen(p2.latitude, p2.longitude)

                        val avgSpeed = (p1.speedKmh + p2.speedKmh) / 2f
                        val color = when {
                            avgSpeed > 90f -> Color(0xFFFF1744)
                            avgSpeed > 60f -> Color(0xFFFF9100)
                            avgSpeed > 30f -> Color(0xFF00E5FF)
                            else -> Color(0xFF00E676)
                        }

                        // Glow line
                        drawLine(
                            color = color.copy(alpha = 0.4f),
                            start = start,
                            end = end,
                            strokeWidth = 9.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Core line
                        drawLine(
                            color = color,
                            start = start,
                            end = end,
                            strokeWidth = 4.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // B. Current Vehicle Location Screen Position
                val vehiclePos = geoToScreen(centerLat, centerLon)

                // C. Exact GPS Accuracy Circle (Calculated based on map ground resolution in meters)
                // metersPerPixel = (156543.03392 * cos(latRad)) / (2^zoom * density)
                val latRad = Math.toRadians(centerLat)
                val groundResolutionMetersPerPixel = (156543.03392 * cos(latRad)) / (n * density.density)
                val actualAccuracy = if (accuracyMeters > 0) accuracyMeters else 8f
                val accuracyRadiusPx = (actualAccuracy / groundResolutionMetersPerPixel).toFloat().coerceIn(16.dp.toPx(), 260.dp.toPx())

                val circleColor = if (isLocked) Color(0xFF00E676) else Color(0xFFFF1744)

                // Translucent Accuracy Zone Fill
                drawCircle(
                    color = circleColor.copy(alpha = 0.16f),
                    radius = accuracyRadiusPx,
                    center = vehiclePos
                )
                // Accuracy Zone Border
                drawCircle(
                    color = circleColor.copy(alpha = 0.65f),
                    radius = accuracyRadiusPx,
                    center = vehiclePos,
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // Pulsing Radar Ring
                drawCircle(
                    color = circleColor.copy(alpha = 0.35f),
                    radius = 24.dp.toPx() * pulseScale,
                    center = vehiclePos,
                    style = Stroke(width = 2.dp.toPx())
                )

                // D. Vehicle Marker with Directional Heading Arrow
                drawCircle(
                    color = Color(0xFF020E1C),
                    radius = 16.dp.toPx(),
                    center = vehiclePos
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 16.dp.toPx(),
                    center = vehiclePos,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val headingRad = Math.toRadians((headingDegrees - 90.0))
                val arrowTip = Offset(
                    vehiclePos.x + 13.dp.toPx() * cos(headingRad).toFloat(),
                    vehiclePos.y + 13.dp.toPx() * sin(headingRad).toFloat()
                )
                val leftRad = Math.toRadians((headingDegrees + 140.0))
                val arrowLeft = Offset(
                    vehiclePos.x + 8.dp.toPx() * cos(leftRad).toFloat(),
                    vehiclePos.y + 8.dp.toPx() * sin(leftRad).toFloat()
                )
                val rightRad = Math.toRadians((headingDegrees + 220.0))
                val arrowRight = Offset(
                    vehiclePos.x + 8.dp.toPx() * cos(rightRad).toFloat(),
                    vehiclePos.y + 8.dp.toPx() * sin(rightRad).toFloat()
                )

                val arrowPath = Path().apply {
                    moveTo(arrowTip.x, arrowTip.y)
                    lineTo(arrowLeft.x, arrowLeft.y)
                    lineTo(vehiclePos.x, vehiclePos.y)
                    lineTo(arrowRight.x, arrowRight.y)
                    close()
                }
                drawPath(path = arrowPath, color = Color(0xFFFF9100))
            }

            // 3. UI Overlays (Coordinates, Accuracy Badge, Floating Speed, Map Controls)
            if (showControls) {
                // Top Address & GPS Accuracy Banner
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 40.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD031120)),
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
                                text = if (locationAddress.isNotEmpty()) locationAddress else "Membaca Koordinat GPS...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = String.format(Locale.US, "%.5f, %.5f", centerLat, centerLon),
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

                // Map Action Controls (Right side: Recenter, Zoom, Free Provider Switcher)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Recenter on Vehicle / My Location
                    IconButton(
                        onClick = {
                            isFollowingLocation = true
                            userPanX = 0f
                            userPanY = 0f
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isFollowingLocation) Color(0xFF00E5FF) else Color(0xDD031424))
                            .border(1.dp, Color(0xFF00E5FF), CircleShape)
                            .testTag("map_recenter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Posisi Saya",
                            tint = if (isFollowingLocation) Color(0xFF031424) else Color(0xFF00E5FF)
                        )
                    }

                    // Zoom In
                    IconButton(
                        onClick = { if (zoomLevel < 19) zoomLevel++ },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xDD031424))
                            .border(1.dp, Color(0xFF00E5FF), CircleShape)
                            .testTag("map_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Perbesar", tint = Color.White)
                    }

                    // Zoom Out
                    IconButton(
                        onClick = { if (zoomLevel > 5) zoomLevel-- },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xDD031424))
                            .border(1.dp, Color(0xFF00E5FF), CircleShape)
                            .testTag("map_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Perkecil", tint = Color.White)
                    }

                    // Free Map Provider Switcher (Dark -> Street -> Satellite -> Topo)
                    IconButton(
                        onClick = {
                            val values = FreeMapProvider.values()
                            val nextIndex = (currentProvider.ordinal + 1) % values.size
                            currentProvider = values[nextIndex]
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xDD031424))
                            .border(1.dp, Color(0xFFFF9100), CircleShape)
                            .testTag("map_style_toggle")
                    ) {
                        Icon(
                            imageVector = when (currentProvider) {
                                FreeMapProvider.CARTO_DARK -> Icons.Default.DarkMode
                                FreeMapProvider.OPEN_STREET_MAP -> Icons.Default.Map
                                FreeMapProvider.ESRI_SATELLITE -> Icons.Default.Public
                                FreeMapProvider.OPEN_TOPO -> Icons.Default.Terrain
                            },
                            contentDescription = "Ganti Tipe Peta (${currentProvider.label})",
                            tint = Color(0xFFFF9100)
                        )
                    }
                }
            }
        }
    }
}
