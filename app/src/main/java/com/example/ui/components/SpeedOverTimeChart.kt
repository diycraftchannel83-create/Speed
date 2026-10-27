package com.example.ui.components

import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoutePoint
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SpeedOverTimeChart(
    routePoints: List<RoutePoint>,
    currentSpeedKmh: Float,
    maxSpeedKmh: Float,
    avgSpeedKmh: Float,
    unit: String,
    durationSeconds: Long,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val speedMultiplier = if (unit == "km/h") 1.0f else 0.621371f
    val speedUnitLabel = if (unit == "km/h") "km/jam" else "mph (mil/jam)"

    // Convert route points to (elapsedSeconds, speedInUnit)
    val startTimestamp = routePoints.firstOrNull()?.timestamp ?: System.currentTimeMillis()
    val telemetryData = remember(routePoints, unit) {
        if (routePoints.isEmpty()) {
            emptyList()
        } else {
            routePoints.map { pt ->
                val elapsedSec = ((pt.timestamp - startTimestamp) / 1000f).coerceAtLeast(0f)
                val speed = pt.speedKmh * speedMultiplier
                Pair(elapsedSec, speed)
            }
        }
    }

    val displayMax = (maxSpeedKmh * speedMultiplier).coerceAtLeast(if (unit == "km/h") 60f else 40f)
    val displayAvg = avgSpeedKmh * speedMultiplier
    val displayCurrent = currentSpeedKmh * speedMultiplier

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var scrubTouchX by remember { mutableFloatStateOf(-1f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speed_over_time_chart"),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE041221)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B0FF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Row: Title, Unit & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x3300E5FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "GRAFIK KECEPATAN / WAKTU",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Sesi Aktif • Skala $speedUnitLabel",
                            color = Color(0xFF80DEEA),
                            fontSize = 10.sp
                        )
                    }
                }

                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color(0xFFB0BEC5))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Summary Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF061A2C))
                    .padding(vertical = 6.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatPill("SAAT INI", String.format(Locale.US, "%.1f", displayCurrent), Color(0xFF00E5FF))
                StatPill("RATA-RATA", String.format(Locale.US, "%.1f", displayAvg), Color(0xFFFF9100))
                StatPill("MAKSIMUM", String.format(Locale.US, "%.1f", maxSpeedKmh * speedMultiplier), Color(0xFFFF1744))
                StatPill("SAMPEL", "${telemetryData.size} pts", Color(0xFF80DEEA))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(telemetryData) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    scrubTouchX = offset.x
                                },
                                onDragEnd = {
                                    scrubTouchX = -1f
                                    selectedIndex = null
                                },
                                onDragCancel = {
                                    scrubTouchX = -1f
                                    selectedIndex = null
                                },
                                onDrag = { change, _ ->
                                    scrubTouchX = change.position.x
                                }
                            )
                        }
                        .pointerInput(telemetryData) {
                            detectTapGestures(
                                onPress = { offset ->
                                    scrubTouchX = offset.x
                                    tryAwaitRelease()
                                    scrubTouchX = -1f
                                    selectedIndex = null
                                }
                            )
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val paddingLeft = 38.dp.toPx()
                    val paddingRight = 16.dp.toPx()
                    val paddingTop = 12.dp.toPx()
                    val paddingBottom = 26.dp.toPx()

                    val chartWidth = canvasWidth - paddingLeft - paddingRight
                    val chartHeight = canvasHeight - paddingTop - paddingBottom

                    // Draw Horizontal Grid Lines & Y-axis labels
                    val ySteps = 4
                    val gridPaint = AndroidPaint().apply {
                        color = android.graphics.Color.argb(140, 120, 160, 190)
                        textSize = 9.sp.toPx()
                        textAlign = AndroidPaint.Align.RIGHT
                        isAntiAlias = true
                    }

                    for (i in 0..ySteps) {
                        val ratio = i.toFloat() / ySteps
                        val y = paddingTop + chartHeight * (1f - ratio)
                        val speedValue = displayMax * ratio

                        // Grid line
                        drawLine(
                            color = Color(0xFF0F3250),
                            start = Offset(paddingLeft, y),
                            end = Offset(canvasWidth - paddingRight, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Y-axis label
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format(Locale.US, "%.0f", speedValue),
                            paddingLeft - 6.dp.toPx(),
                            y + 3.dp.toPx(),
                            gridPaint
                        )
                    }

                    // Average speed horizontal dashed line
                    if (displayAvg > 0f) {
                        val avgY = paddingTop + chartHeight * (1f - (displayAvg / displayMax).coerceIn(0f, 1f))
                        drawLine(
                            color = Color(0xFFFF9100).copy(alpha = 0.7f),
                            start = Offset(paddingLeft, avgY),
                            end = Offset(canvasWidth - paddingRight, avgY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    }

                    if (telemetryData.size >= 2) {
                        val maxElapsed = (telemetryData.last().first).coerceAtLeast(1f)

                        fun dataToScreen(elapsed: Float, speed: Float): Offset {
                            val x = paddingLeft + (elapsed / maxElapsed) * chartWidth
                            val y = paddingTop + chartHeight * (1f - (speed / displayMax).coerceIn(0f, 1f))
                            return Offset(x, y)
                        }

                        // Path for Line Stroke & Filled Gradient Area
                        val strokePath = Path()
                        val fillPath = Path()

                        val firstPoint = dataToScreen(telemetryData.first().first, telemetryData.first().second)
                        strokePath.moveTo(firstPoint.x, firstPoint.y)
                        fillPath.moveTo(firstPoint.x, paddingTop + chartHeight)
                        fillPath.lineTo(firstPoint.x, firstPoint.y)

                        for (i in 1 until telemetryData.size) {
                            val pt = dataToScreen(telemetryData[i].first, telemetryData[i].second)
                            strokePath.lineTo(pt.x, pt.y)
                            fillPath.lineTo(pt.x, pt.y)
                        }

                        fillPath.lineTo(canvasWidth - paddingRight, paddingTop + chartHeight)
                        fillPath.close()

                        // Draw Gradient Area Under Curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x5500E5FF),
                                    Color(0x2200E5FF),
                                    Color(0x0000E5FF)
                                ),
                                startY = paddingTop,
                                endY = paddingTop + chartHeight
                            )
                        )

                        // Draw Neon Stroke Line
                        drawPath(
                            path = strokePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E676),
                                    Color(0xFF00E5FF),
                                    Color(0xFFFF9100),
                                    Color(0xFFFF1744)
                                )
                            ),
                            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Scrubber Cursor on Drag/Tap
                        if (scrubTouchX >= paddingLeft && scrubTouchX <= canvasWidth - paddingRight) {
                            val normalizedX = ((scrubTouchX - paddingLeft) / chartWidth).coerceIn(0f, 1f)
                            val targetElapsed = normalizedX * maxElapsed
                            val closestIdx = telemetryData.indices.minByOrNull {
                                kotlin.math.abs(telemetryData[it].first - targetElapsed)
                            } ?: 0

                            selectedIndex = closestIdx
                            val closestPt = telemetryData[closestIdx]
                            val scrubPos = dataToScreen(closestPt.first, closestPt.second)

                            // Vertical crosshair line
                            drawLine(
                                color = Color.White.copy(alpha = 0.8f),
                                start = Offset(scrubPos.x, paddingTop),
                                end = Offset(scrubPos.x, paddingTop + chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                            )

                            // Glowing Cursor Dot
                            drawCircle(
                                color = Color(0x6600E5FF),
                                radius = 9.dp.toPx(),
                                center = scrubPos
                            )
                            drawCircle(
                                color = Color(0xFF00E5FF),
                                radius = 5.dp.toPx(),
                                center = scrubPos
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = scrubPos
                            )
                        }

                        // X-axis Time Labels
                        val xSteps = 4
                        val timePaint = AndroidPaint().apply {
                            color = android.graphics.Color.argb(140, 120, 160, 190)
                            textSize = 9.sp.toPx()
                            textAlign = AndroidPaint.Align.CENTER
                            isAntiAlias = true
                        }

                        for (i in 0..xSteps) {
                            val ratio = i.toFloat() / xSteps
                            val x = paddingLeft + chartWidth * ratio
                            val sec = (maxElapsed * ratio).toLong()
                            val label = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                x,
                                canvasHeight - 4.dp.toPx(),
                                timePaint
                            )
                        }
                    } else {
                        // Empty State when recording just started
                        drawContext.canvas.nativeCanvas.apply {
                            val emptyPaint = AndroidPaint().apply {
                                color = android.graphics.Color.argb(120, 150, 180, 200)
                                textSize = 11.sp.toPx()
                                textAlign = AndroidPaint.Align.CENTER
                                isAntiAlias = true
                            }
                            drawText(
                                "Merekam telemetri kecepatan... Pacu kendaraan Anda!",
                                canvasWidth / 2f,
                                canvasHeight / 2f,
                                emptyPaint
                            )
                        }
                    }
                }

                // Floating Scrubber Tooltip
                selectedIndex?.let { idx ->
                    if (idx in telemetryData.indices) {
                        val pt = telemetryData[idx]
                        val sec = pt.first.toLong()
                        val timeStr = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xDD00E5FF))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Waktu $timeStr • ${String.format(Locale.US, "%.1f", pt.second)} $unit",
                                color = Color(0xFF020E1C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF78909C), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}
