package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun RacePodView(
    race0to60: Float?,
    race0to100: Float?,
    race402m: Float?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(118.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag("race_pod_view"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(118.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 4.dp.toPx()

            // Dark Pod Background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D253D),
                        Color(0xFF071424),
                        Color(0xFF02070D)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
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
                text = "RACE",
                color = Color(0xFFFF9100),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 0-60 km/h
            val text0to60 = race0to60?.let { String.format(Locale.US, "%.1fs", it) } ?: "--"
            Text(
                text = text0to60,
                color = if (race0to60 != null) Color(0xFF00E5FF) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // 0-100 km/h
            val text0to100 = race0to100?.let { String.format(Locale.US, "%.1fs", it) } ?: "--"
            Text(
                text = text0to100,
                color = if (race0to100 != null) Color(0xFF00E5FF) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // 402m sprint
            val text402m = race402m?.let { String.format(Locale.US, "%.1fs", it) } ?: "--"
            Text(
                text = text402m,
                color = if (race402m != null) Color(0xFF00E5FF) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
