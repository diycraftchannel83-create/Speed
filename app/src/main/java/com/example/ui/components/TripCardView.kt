package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun TripCardView(
    isRecording: Boolean,
    isPaused: Boolean,
    distanceMeters: Double,
    durationSeconds: Long,
    unit: String,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onResetTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayDist = if (unit == "km/h") {
        distanceMeters / 1000.0
    } else {
        (distanceMeters / 1000.0) * 0.621371
    }
    val distUnitLabel = if (unit == "km/h") "km" else "mil"

    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    val formattedDuration = String.format(Locale.US, "%01d:%02d:%02d", hours, minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "RecPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RecAlpha"
    )

    Column(
        modifier = modifier
            .testTag("trip_card_view")
    ) {
        // Status Row (e.g. "TRIP : RECORDING")
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusText = when {
                isRecording && !isPaused -> "RECORDING"
                isRecording && isPaused -> "PAUSED"
                else -> "READY"
            }
            val statusColor = when {
                isRecording && !isPaused -> Color(0xFF00E5FF)
                isRecording && isPaused -> Color(0xFFFF9100)
                else -> Color(0xFF78909C)
            }

            if (isRecording && !isPaused) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744).copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = "TRIP : $statusText",
                color = statusColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            if (isRecording && !isPaused) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300E676))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "☀️ NO SLEEP",
                        color = Color(0xFF00E676),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Large Distance Display (e.g. 0.0km)
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = String.format(Locale.US, "%.1f", displayDist),
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = distUnitLabel,
                color = Color(0xFFFF9100),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
            )
        }

        // Time Elapsed (e.g. 0:00:00)
        Text(
            text = formattedDuration,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Mini control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play / Pause Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isRecording) Color(0x3300E5FF) else Color(0x3300E676))
                    .clickable {
                        if (!isRecording) onStartRecording() else onPauseRecording()
                    }
                    .padding(6.dp)
                    .testTag("trip_play_pause_button")
            ) {
                Icon(
                    imageVector = if (!isRecording || isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (!isRecording) "Start Trip" else "Pause Trip",
                    tint = if (!isRecording) Color(0xFF00E676) else Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Stop & Save Button
            if (isRecording || durationSeconds > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x33FF1744))
                        .clickable { onStopRecording() }
                        .padding(6.dp)
                        .testTag("trip_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop and Save Trip",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Reset Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { onResetTrip() }
                    .padding(6.dp)
                    .testTag("trip_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Trip",
                    tint = Color(0xFFB0BEC5),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
