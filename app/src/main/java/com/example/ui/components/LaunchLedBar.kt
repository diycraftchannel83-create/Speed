package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.telemetry.LaunchStage

@Composable
fun LaunchLedBar(
    launchStage: LaunchStage,
    onTapStaging: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 6 LED dots representing the Staging Christmas Tree
    val dotCount = 6

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(CircleShape)
            .clickable { onTapStaging() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("launch_led_bar")
    ) {
        for (i in 0 until dotCount) {
            val targetColor = when (launchStage) {
                LaunchStage.IDLE -> {
                    if (i == 0) Color(0xFFFF9100) else Color(0xFF1E3A52)
                }
                LaunchStage.STAGE_RED -> {
                    if (i < 2) Color(0xFFFF1744) else Color(0xFF1E3A52)
                }
                LaunchStage.STAGE_YELLOW -> {
                    if (i < 4) Color(0xFFFFD600) else Color(0xFF1E3A52)
                }
                LaunchStage.STAGE_GREEN, LaunchStage.LAUNCHED -> {
                    Color(0xFF00E676)
                }
            }

            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(150),
                label = "LedColor_$i"
            )

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
                    .border(
                        width = 1.dp,
                        color = animatedColor.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
            )
        }
    }
}
