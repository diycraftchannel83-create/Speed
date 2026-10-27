package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BatteryWidget(
    batteryPct: Int,
    modifier: Modifier = Modifier
) {
    val batteryColor = when {
        batteryPct > 20 -> Color(0xFF00E676)
        batteryPct > 10 -> Color(0xFFFFD600)
        else -> Color(0xFFFF1744)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag("battery_widget")
    ) {
        // Battery Frame (bracket style)
        Box(
            modifier = Modifier
                .border(
                    width = 2.dp,
                    color = batteryColor,
                    shape = RoundedCornerShape(3.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${batteryPct}%",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
