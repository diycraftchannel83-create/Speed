package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telemetry.GpsLockState

@Composable
fun GpsPodView(
    gpsState: GpsLockState,
    accuracyMeters: Float,
    unit: String = "km/h",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = gpsState == GpsLockState.LOCKED

    val targetRingColor = if (isLocked) Color(0xFF00E676) else Color(0xFFFF1744)
    val ringColor by animateColorAsState(
        targetValue = targetRingColor,
        animationSpec = tween(500),
        label = "GpsRingColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "GpsPulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GpsGlow"
    )

    Box(
        modifier = modifier
            .size(92.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag("gps_pod_view"),
        contentAlignment = Alignment.Center
    ) {
        // Pod Canvas
        Canvas(modifier = Modifier.size(92.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 4.dp.toPx()

            // Dark Pod Background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0B1E2E),
                        Color(0xFF050E17),
                        Color(0xFF02070D)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Outer Pulsing Glow
            drawCircle(
                color = ringColor.copy(alpha = 0.35f * pulseGlow),
                radius = radius + 2.dp.toPx(),
                center = center,
                style = Stroke(width = 5.dp.toPx())
            )

            // Crisp Ring
            drawCircle(
                color = ringColor,
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // GPS Pod Labels
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GPS",
                color = if (isLocked) Color(0xFF00E676) else Color(0xFFFF5252),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            val isImperial = unit == "mph"
            val displayAccuracy = if (isLocked) {
                if (accuracyMeters > 0) {
                    if (isImperial) (accuracyMeters * 3.28084f).toInt().toString() else accuracyMeters.toInt().toString()
                } else {
                    if (isImperial) "25" else "8"
                }
            } else {
                "--"
            }

            Text(
                text = displayAccuracy,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = if (isLocked) (if (isImperial) "ft" else "m") else "search",
                color = Color(0xFF90A4AE),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
