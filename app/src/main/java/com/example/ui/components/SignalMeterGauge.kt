package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SignalRed
import com.example.ui.theme.SignalYellow

@Composable
fun SignalMeterGauge(
    rssi: Int,
    signalPercent: Int,
    modifier: Modifier = Modifier
) {
    val signalFraction = (signalPercent / 100f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = signalFraction,
        animationSpec = tween(durationMillis = 400),
        label = "signal_anim"
    )

    val signalColor = when {
        rssi >= -55 -> SignalGreen
        rssi >= -67 -> Color(0xFF00E5FF)
        rssi >= -75 -> SignalYellow
        rssi >= -85 -> SignalOrange
        else -> SignalRed
    }

    val animatedColor by animateColorAsState(
        targetValue = signalColor,
        animationSpec = tween(durationMillis = 300),
        label = "color_anim"
    )

    val qualityLabel = when {
        rssi >= -55 -> "Sangat Baik"
        rssi >= -67 -> "Baik"
        rssi >= -75 -> "Cukup"
        rssi >= -85 -> "Lemah"
        else -> "Sangat Lemah"
    }

    Box(
        modifier = modifier
            .size(190.dp)
            .testTag("signal_meter_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = size.width - strokeWidth
            val arcTopLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // 180° semi-circle from 180° to 360° (bottom flat)
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Filled signal arc
            if (animatedFraction > 0.01f) {
                drawArc(
                    color = animatedColor,
                    startAngle = 180f,
                    sweepAngle = 180f * animatedFraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "WiFi Signal",
                    tint = animatedColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$rssi dBm",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$signalPercent% • $qualityLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = animatedColor,
                fontSize = 12.sp
            )
        }
    }
}
