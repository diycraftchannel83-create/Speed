package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutePoint
import com.example.telemetry.GpsLockState
import java.util.Locale

@Composable
fun RouteMapView(
    title: String,
    routePoints: List<RoutePoint>,
    maxSpeed: Float,
    avgSpeed: Float,
    distanceMeters: Double,
    unit: String = "km/h",
    onBack: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val centerLat = routePoints.lastOrNull()?.latitude ?: -6.2088
    val centerLng = routePoints.lastOrNull()?.longitude ?: 106.8456
    val currentSpeed = routePoints.lastOrNull()?.speedKmh ?: 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030C16))
            .testTag("route_map_screen")
    ) {
        // OSMDroid Map Layer
        OsmMapView(
            latitude = centerLat,
            longitude = centerLng,
            accuracyMeters = 8f,
            gpsState = GpsLockState.LOCKED,
            currentSpeedKmh = currentSpeed,
            maxSpeedKmh = maxSpeed,
            headingDegrees = 0f,
            locationAddress = title,
            routePoints = routePoints,
            showControls = true,
            initialZoom = 16.0
        )

        // Top Navigation Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 38.dp)
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${routePoints.size} Titik GPS • Akurasi Real-Time",
                    color = Color(0xFF80DEEA),
                    fontSize = 12.sp
                )
            }
        }

        // Bottom Telemetry & Export Panel
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE091929)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B0FF))
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // Speed Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LEGENDA KECEPATAN",
                        color = Color(0xFFFF9100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(Color(0xFF00E676), "<30")
                        LegendItem(Color(0xFF00E5FF), "30-60")
                        LegendItem(Color(0xFFFF9100), "60-90")
                        LegendItem(Color(0xFFFF1744), ">90")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stats Row
                val isImperial = unit != "km/h"
                val distMult = if (isImperial) 0.000621371 else 0.001
                val distLabel = if (isImperial) "mil" else "km"
                val speedMult = if (isImperial) 0.621371f else 1f
                val speedLabel = if (isImperial) "mph" else "km/h"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricColumn("JARAK", String.format(Locale.US, "%.2f %s", distanceMeters * distMult, distLabel), Color(0xFF00E5FF))
                    MetricColumn("TOP SPEED", String.format(Locale.US, "%.1f %s", maxSpeed * speedMult, speedLabel), Color(0xFFFF1744))
                    MetricColumn("RATA-RATA", String.format(Locale.US, "%.1f %s", avgSpeed * speedMult, speedLabel), Color(0xFFFF9100))
                }

                Spacer(modifier = Modifier.height(12.dp))

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
