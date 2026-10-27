package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun AverageSpeedPodView(
    avgSpeed: Float,
    unit: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayAvg = if (unit == "km/h") avgSpeed else avgSpeed * 0.621371f

    Box(
        modifier = modifier
            .size(118.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag("average_speed_pod"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(118.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 4.dp.toPx()

            // Dark Pod Background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0C243B),
                        Color(0xFF061422),
                        Color(0xFF02070E)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Outer Cyan Glow
            drawCircle(
                color = Color(0x3300B0FF),
                radius = radius + 1.dp.toPx(),
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Outer Ring
            drawCircle(
                color = Color(0xFF00B0FF),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = "AVERAGE\nSPEEDOMETER",
                color = Color(0xFFB0BEC5),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )

            Text(
                text = String.format(Locale.US, "%.1f", displayAvg),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = unit,
                color = Color(0xFFFF9100),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
