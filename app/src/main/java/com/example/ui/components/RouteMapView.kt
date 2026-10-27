package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutePoint
import java.util.Locale

@Composable
fun RouteMapView(
    title: String,
    routePoints: List<RoutePoint>,
    maxSpeed: Float,
    avgSpeed: Float,
    distanceMeters: Double,
    onBack: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030C16))
            .testTag("route_map_screen")
    ) {
        // Main Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Dark tactical grid background
            val gridSize = 40.dp.toPx()
            for (x in 0..(canvasWidth / gridSize).toInt() + 1) {
                drawLine(
                    color = Color(0xFF0B1F33),
                    start = Offset(x * gridSize, 0f),
                    end = Offset(x * gridSize, canvasHeight),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(canvasHeight / gridSize).toInt() + 1) {
                drawLine(
                    color = Color(0xFF0B1F33),
                    start = Offset(0f, y * gridSize),
                    end = Offset(canvasWidth, y * gridSize),
                    strokeWidth = 1f
                )
            }

            if (routePoints.size >= 2) {
                val minLat = routePoints.minOf { it.latitude }
                val maxLat = routePoints.maxOf { it.latitude }
                val minLng = routePoints.minOf { it.longitude }
                val maxLng = routePoints.maxOf { it.longitude }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)

                val padding = 60.dp.toPx()
                val mapWidth = canvasWidth - (padding * 2)
                val mapHeight = canvasHeight * 0.55f - (padding * 2)

                fun projectToScreen(lat: Double, lng: Double): Offset {
                    val normalizedX = ((lng - minLng) / lngSpan).toFloat()
                    val normalizedY = (1f - ((lat - minLat) / latSpan).toFloat()) // Invert Y for screen coords

                    val baseScreenX = padding + normalizedX * mapWidth
                    val baseScreenY = padding + 80.dp.toPx() + normalizedY * mapHeight

                    // Apply user zoom and pan relative to center
                    val centerX = canvasWidth / 2f
                    val centerY = (padding + 80.dp.toPx() + mapHeight / 2f)

                    val scaledX = centerX + (baseScreenX - centerX) * zoomScale + panOffsetX
                    val scaledY = centerY + (baseScreenY - centerY) * zoomScale + panOffsetY

                    return Offset(scaledX, scaledY)
                }

                // Draw route segments colored by speed
                for (i in 0 until routePoints.size - 1) {
                    val p1 = routePoints[i]
                    val p2 = routePoints[i + 1]

                    val startOffset = projectToScreen(p1.latitude, p1.longitude)
                    val endOffset = projectToScreen(p2.latitude, p2.longitude)

                    val segmentSpeed = (p1.speedKmh + p2.speedKmh) / 2f
                    val segmentColor = when {
                        segmentSpeed > 90f -> Color(0xFFFF1744) // Red for fast
                        segmentSpeed > 60f -> Color(0xFFFF9100) // Orange
                        segmentSpeed > 30f -> Color(0xFF00E5FF) // Cyan
                        else -> Color(0xFF00E676)               // Green for cruising
                    }

                    // Shadow/glow for visibility
                    drawLine(
                        color = segmentColor.copy(alpha = 0.35f),
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Core line
                    drawLine(
                        color = segmentColor,
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Start Marker (Green Flag/Circle)
                val startScreen = projectToScreen(routePoints.first().latitude, routePoints.first().longitude)
                drawCircle(
                    color = Color(0xFF00E676),
                    radius = 9.dp.toPx(),
                    center = startScreen
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = startScreen
                )

                // Current/End Marker (Pulsing Checkered/Cyan)
                val endScreen = projectToScreen(routePoints.last().latitude, routePoints.last().longitude)
                drawCircle(
                    color = Color(0x6600E5FF),
                    radius = 16.dp.toPx(),
                    center = endScreen
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 10.dp.toPx(),
                    center = endScreen
                )
                drawCircle(
                    color = Color(0xFFFF1744),
                    radius = 4.dp.toPx(),
                    center = endScreen
                )
            }
        }

        // Top Navigation Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 36.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xCC0B1F33))
                    .testTag("map_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${routePoints.size} GPS Telemetry Waypoints",
                    color = Color(0xFF80DEEA),
                    fontSize = 12.sp
                )
            }
        }

        // Floating Map Controls (Zoom In, Zoom Out, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp, top = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(5.0f) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0B1F33))
                    .border(1.dp, Color(0xFF00E5FF), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
            }

            IconButton(
                onClick = { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.5f) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0B1F33))
                    .border(1.dp, Color(0xFF00E5FF), CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }

            IconButton(
                onClick = {
                    zoomScale = 1.0f
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0B1F33))
                    .border(1.dp, Color(0xFF00E5FF), CircleShape)
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter Map", tint = Color(0xFF00E5FF))
            }
        }

        // Bottom Telemetry & Export Panel
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE091929)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B0FF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Speed Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPEED HEATMAP",
                        color = Color(0xFFFF9100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(Color(0xFF00E676), "<30")
                        LegendItem(Color(0xFF00E5FF), "30-60")
                        LegendItem(Color(0xFFFF9100), "60-90")
                        LegendItem(Color(0xFFFF1744), ">90")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricColumn("DISTANCE", String.format(Locale.US, "%.2f km", distanceMeters / 1000.0), Color(0xFF00E5FF))
                    MetricColumn("MAX SPEED", String.format(Locale.US, "%.1f km/h", maxSpeed), Color(0xFFFF1744))
                    MetricColumn("AVG SPEED", String.format(Locale.US, "%.1f km/h", avgSpeed), Color(0xFFFF9100))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Export Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_pdf_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onExportCsv,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = "CSV", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Color(0xFFB0BEC5), fontSize = 10.sp)
    }
}

@Composable
private fun MetricColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF78909C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
