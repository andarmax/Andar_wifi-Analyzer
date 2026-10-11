package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CyberBluePrimary
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalOrange
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    currentSpeedMbps: Double,
    maxSpeedDisplay: Double = 100.0,
    modifier: Modifier = Modifier,
    unitLabel: String = "Mbps",
    phaseLabel: String = "DOWNLOAD"
) {
    val animatedSpeed = remember { Animatable(0f) }

    LaunchedEffect(currentSpeedMbps) {
        animatedSpeed.animateTo(
            targetValue = currentSpeedMbps.toFloat(),
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    // Determine scale: dynamically adjust max if speed surpasses 100 Mbps
    val dynamicMax = when {
        currentSpeedMbps > 500 -> 1000.0
        currentSpeedMbps > 200 -> 500.0
        currentSpeedMbps > 100 -> 200.0
        else -> maxSpeedDisplay
    }

    val speedFraction = (animatedSpeed.value / dynamicMax.toFloat()).coerceIn(0f, 1f)
    // Gauge sweep angle: 240 degrees (from 150° to 390°)
    val startAngle = 150f
    val totalSweep = 240f
    val currentSweep = totalSweep * speedFraction

    Box(
        modifier = modifier
            .size(260.dp)
            .testTag("speedometer_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val strokeWidth = 18.dp.toPx()
            val arcSize = size.width - strokeWidth
            val arcTopLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background Track Arc
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Colored Speed Arc
            if (currentSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            CyberBluePrimary,
                            AccentCyan,
                            SignalGreen,
                            SignalOrange
                        )
                    ),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Draw Tick Marks
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = arcSize / 2
            val tickCount = 10

            for (i in 0..tickCount) {
                val tickFraction = i / tickCount.toFloat()
                val tickAngleDeg = startAngle + (tickFraction * totalSweep)
                val tickAngleRad = (tickAngleDeg * PI / 180.0).toFloat()

                val innerRadius = outerRadius - if (i % 2 == 0) 14.dp.toPx() else 8.dp.toPx()
                val p1 = Offset(
                    x = center.x + (outerRadius - 6.dp.toPx()) * cos(tickAngleRad),
                    y = center.y + (outerRadius - 6.dp.toPx()) * sin(tickAngleRad)
                )
                val p2 = Offset(
                    x = center.x + innerRadius * cos(tickAngleRad),
                    y = center.y + innerRadius * sin(tickAngleRad)
                )

                drawLine(
                    color = if (tickFraction <= speedFraction) AccentCyan else Color(0xFF475569),
                    start = p1,
                    end = p2,
                    strokeWidth = if (i % 2 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Needle Pointer
            val needleAngleDeg = startAngle + currentSweep
            val needleAngleRad = (needleAngleDeg * PI / 180.0).toFloat()
            val needleLength = outerRadius - 20.dp.toPx()

            val needleEnd = Offset(
                x = center.x + needleLength * cos(needleAngleRad),
                y = center.y + needleLength * sin(needleAngleRad)
            )

            // Draw glowing needle
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, AccentCyan)
                ),
                start = center,
                end = needleEnd,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Needle Center Hub
            drawCircle(
                color = CyberBluePrimary,
                radius = 10.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = center
            )
        }

        // Central Readout Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                color = AccentCyan,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%.1f".format(animatedSpeed.value),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = unitLabel,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
