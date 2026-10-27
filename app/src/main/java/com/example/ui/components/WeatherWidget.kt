package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
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
fun WeatherWidget(
    temperatureCelsius: Int,
    condition: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .testTag("weather_widget")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main temperature
            Text(
                text = "$temperatureCelsius",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Sub temperature (e.g. 27/27°C)
            Column {
                Text(
                    text = "${temperatureCelsius}",
                    color = Color(0xFF80DEEA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${temperatureCelsius}°C",
                    color = Color(0xFFFF9100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Weather icon (cloud with rain drops)
            Icon(
                imageVector = when {
                    condition.contains("rain", true) -> Icons.Outlined.WaterDrop
                    condition.contains("cloud", true) -> Icons.Outlined.WbCloudy
                    else -> Icons.Outlined.WbSunny
                },
                contentDescription = condition,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Condition label (e.g. "light rain")
        Text(
            text = condition,
            color = Color(0xFFECEFF1),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
