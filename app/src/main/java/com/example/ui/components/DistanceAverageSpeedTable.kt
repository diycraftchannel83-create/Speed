package com.example.ui.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutePoint
import java.util.Locale

private data class DistanceSpeedRow(val distanceMeters: Double, val averageSpeedKmh: Double?)

@Composable
fun DistanceAverageSpeedTable(routePoints: List<RoutePoint>, unit: String, modifier: Modifier = Modifier) {
    val rows = remember(routePoints) {
        val targets = listOf(100.0, 201.0, 402.0, 600.0, 800.0, 1000.0)
        val cumulative = mutableListOf<Pair<Double, Long>>()
        if (routePoints.isNotEmpty()) {
            cumulative += 0.0 to routePoints.first().timestamp
            var totalDistance = 0.0
            for (i in 1 until routePoints.size) {
                val previous = routePoints[i - 1]
                val current = routePoints[i]
                val result = FloatArray(1)
                Location.distanceBetween(previous.latitude, previous.longitude, current.latitude, current.longitude, result)
                val segment = result[0].toDouble()
                if (segment.isFinite() && segment >= 0.0 && segment < 200.0) {
                    totalDistance += segment
                    cumulative += totalDistance to current.timestamp
                }
            }
        }
        targets.map { target ->
            val crossingIndex = cumulative.indexOfFirst { it.first >= target }
            if (crossingIndex <= 0) DistanceSpeedRow(target, null)
            else {
                val (d1, t1) = cumulative[crossingIndex - 1]
                val (d2, t2) = cumulative[crossingIndex]
                val fraction = if (d2 > d1) ((target - d1) / (d2 - d1)).coerceIn(0.0, 1.0) else 0.0
                val elapsedMs = (t1 + ((t2 - t1) * fraction).toLong()) - cumulative.first().second
                val elapsedSeconds = elapsedMs / 1000.0
                val speed = if (elapsedSeconds > 0.0) target / elapsedSeconds * 3.6 else null
                DistanceSpeedRow(target, speed)
            }
        }
    }
    val multiplier = if (unit == "km/h") 1.0 else 0.621371
    val speedLabel = if (unit == "km/h") "km/jam" else "mph"
    Card(modifier = modifier.fillMaxWidth().border(1.dp, Color(0xFF0E3E64), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071B2D)), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("SKALA KECEPATAN RATA-RATA", color = Color(0xFF00E5FF), fontSize = 13.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0D2842), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("Skala", modifier = Modifier.weight(1f), color = Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Kecepatan rata-rata", modifier = Modifier.weight(1.5f), color = Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)) {
                    Text("\${row.distanceMeters.toInt()} m", modifier = Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
                    Text(text = row.averageSpeedKmh?.let { String.format(Locale.US, "%.2f %s", it * multiplier, speedLabel) } ?: "—",
                        modifier = Modifier.weight(1.5f), color = if (row.averageSpeedKmh == null) Color(0xFF78909C) else Color(0xFFFFB74D),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
