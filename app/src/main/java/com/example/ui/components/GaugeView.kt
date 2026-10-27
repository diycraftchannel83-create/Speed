package com.example.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GaugeView(
    speed: Float,
    maxSpeed: Float,
    unit: String,
    mode: String,
    snapshotCount: Int,
    onUnitClick: () -> Unit,
    onSnapshotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Max dial scale: 220 km/h or 140 mph
    val maxDialSpeed = if (unit == "km/h") 220f else 140f
    val displaySpeed = if (unit == "km/h") speed else speed * 0.621371f
    val displayMaxSpeed = if (unit == "km/h") maxSpeed else maxSpeed * 0.621371f

    // Needle sweep angle: from -220 degrees to 40 degrees (260 degree arc)
    val startAngle = 140f
    val sweepTotal = 260f
    val clampedRatio = (displaySpeed / maxDialSpeed).coerceIn(0f, 1f)
    val targetNeedleAngle = startAngle + clampedRatio * sweepTotal

    val animatedNeedleAngle by animateFloatAsState(
        targetValue = targetNeedleAngle,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "NeedleAngle"
    )

    Box(
        modifier = modifier
            .size(310.dp)
            .testTag("speedometer_gauge"),
        contentAlignment = Alignment.Center
    ) {
        // Custom Canvas for Dial, Ticks, Glowing Rings, and Thick Needle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 12.dp.toPx()

            // 1. Outer Dark Circular Backdrop
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF031422),
                        Color(0xFF020C17),
                        Color(0xFF00050A)
                    ),
                    center = center,
                    radius = radius + 12.dp.toPx()
                ),
                radius = radius + 10.dp.toPx(),
                center = center
            )

            // 2. Glowing Outer Rings (Cyan & Deep Blue)
            drawCircle(
                color = Color(0x3300E5FF),
                radius = radius + 6.dp.toPx(),
                center = center,
                style = Stroke(width = 8.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF00B0FF),
                radius = radius + 2.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 3. Inner Dial Arc & Tick Marks
            val totalTicks = 55 // Minor ticks
            val tickRadiusOuter = radius - 8.dp.toPx()

            for (i in 0..totalTicks) {
                val tickRatio = i.toFloat() / totalTicks
                val angleDeg = startAngle + tickRatio * sweepTotal
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val isMajor = i % 5 == 0
                val tickLength = if (isMajor) 14.dp.toPx() else 7.dp.toPx()
                val tickStroke = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx()

                // Red tick at start (zero line)
                val tickColor = if (i == 0) {
                    Color(0xFFFF3D00)
                } else if (isMajor) {
                    Color.White.copy(alpha = 0.95f)
                } else {
                    Color(0xFF75A0BA).copy(alpha = 0.6f)
                }

                val startX = center.x + (tickRadiusOuter - tickLength) * cos(angleRad).toFloat()
                val startY = center.y + (tickRadiusOuter - tickLength) * sin(angleRad).toFloat()
                val endX = center.x + tickRadiusOuter * cos(angleRad).toFloat()
                val endY = center.y + tickRadiusOuter * sin(angleRad).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickStroke,
                    cap = StrokeCap.Round
                )

                // Draw numbers for major ticks (20, 40, 60, 80 ...)
                if (isMajor && i > 0 && i < totalTicks) {
                    val speedVal = ((i.toFloat() / totalTicks) * maxDialSpeed).toInt()
                    val textRadius = tickRadiusOuter - 25.dp.toPx()
                    val textX = center.x + textRadius * cos(angleRad).toFloat()
                    val textY = center.y + textRadius * sin(angleRad).toFloat()

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            color = android.graphics.Color.argb(200, 150, 190, 220)
                            textSize = 12.sp.toPx()
                            textAlign = Paint.Align.CENTER
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                        drawText(speedVal.toString(), textX, textY + 4.dp.toPx(), paint)
                    }
                }
            }

            // 4. Active Speed Glowing Arc
            val activeSweep = clampedRatio * sweepTotal
            if (activeSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFF3D00),
                            Color(0xFFFF9100),
                            Color(0xFF00E5FF)
                        ),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 5. Thick Responsive Needle (Glowing Orange / Red)
            val needleLength = radius - 16.dp.toPx()
            val needleRad = Math.toRadians(animatedNeedleAngle.toDouble())

            val needleEndX = center.x + needleLength * cos(needleRad).toFloat()
            val needleEndY = center.y + needleLength * sin(needleRad).toFloat()

            // Needle shadow / glow
            drawLine(
                color = Color(0x66FF3D00),
                start = center,
                end = Offset(needleEndX, needleEndY),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Core needle
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF9100), Color(0xFFFF1744)),
                    start = center,
                    end = Offset(needleEndX, needleEndY)
                ),
                start = center,
                end = Offset(needleEndX, needleEndY),
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center Pivot Hub
            drawCircle(
                color = Color(0xFF041324),
                radius = 16.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 16.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFFF3D00),
                radius = 6.dp.toPx(),
                center = center
            )
        }

        // Center Digital Readout & Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            // Unit Switcher (km/h / mph)
            Text(
                text = unit,
                color = Color(0xFF00E5FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onUnitClick() }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .testTag("unit_toggle_button")
            )

            // Mode Label (Standard / Race)
            Text(
                text = mode,
                color = Color(0xFFFF9100),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Large Digital Speed Display
            Text(
                text = displaySpeed.toInt().toString(),
                color = Color.White,
                fontSize = 76.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.testTag("current_speed_text")
            )

            // Max Speed Readout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayMaxSpeed.toInt().toString(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " max",
                    color = Color(0xFF90A4AE),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Camera / Snapshot Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x3300E5FF))
                    .clickable { onSnapshotClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("snapshot_button")
            ) {
                Text(
                    text = snapshotCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture Speed Snapshot",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
