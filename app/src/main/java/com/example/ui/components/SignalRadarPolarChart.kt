package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WifiAccessPoint
import com.example.model.WifiBand
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CyberBluePrimary
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SignalRed
import com.example.ui.theme.SignalYellow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class PolarNodePosition(
    val ap: WifiAccessPoint,
    val x: Float,
    val y: Float,
    val radius: Float,
    val angleDeg: Float
)

@Composable
fun SignalRadarPolarChart(
    accessPoints: List<WifiAccessPoint>,
    selectedApBssid: String?,
    onSelectAp: (WifiAccessPoint) -> Unit,
    isSweeping: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep_anim")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    // Pulse animation for connected node
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    val calculatedPositions = remember(accessPoints) {
        val total = accessPoints.size
        accessPoints.mapIndexed { index, ap ->
            // Distribute angles evenly with pseudo-random jitter based on BSSID
            val baseAngle = (index * (360f / maxOf(total, 1))) + ((ap.bssid.hashCode() and 0x1F) - 15f)
            baseAngle % 360f
        }
    }

    Box(
        modifier = modifier
            .size(330.dp)
            .background(Color(0xFF090E17), CircleShape)
            .padding(10.dp)
            .testTag("signal_radar_polar_chart"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(accessPoints, calculatedPositions) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.width / 2f - 18.dp.toPx()

                        // Find closest node to tap point
                        var closestAp: WifiAccessPoint? = null
                        var minDistance = 35.dp.toPx()

                        accessPoints.forEachIndexed { index, ap ->
                            val angleDeg = calculatedPositions.getOrElse(index) { 0f }
                            val angleRad = (angleDeg * PI / 180f).toFloat()

                            // Normalized radius: -30dBm is 0.15, -95dBm is 0.95
                            val normDist = ((abs(ap.rssi) - 30f) / 65f).coerceIn(0.15f, 0.95f)
                            val r = normDist * maxRadius

                            val nodeX = center.x + r * cos(angleRad)
                            val nodeY = center.y + r * sin(angleRad)

                            val dist = hypot(tapOffset.x - nodeX, tapOffset.y - nodeY)
                            if (dist < minDistance) {
                                minDistance = dist
                                closestAp = ap
                            }
                        }

                        closestAp?.let { onSelectAp(it) }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadarRadius = size.width / 2f - 18.dp.toPx()

            // 1. Draw Background Polar Concentric Rings (-40 dBm, -60 dBm, -75 dBm, -90 dBm)
            val ringLevels = listOf(
                -40 to 0.25f,
                -60 to 0.50f,
                -75 to 0.75f,
                -90 to 0.98f
            )

            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#475569")
                textSize = 9.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            val ringLabelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#38BDF8")
                textSize = 8.5.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            ringLevels.forEach { (dbm, fraction) ->
                val ringRadius = maxRadarRadius * fraction
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                )

                // Label dBm level on vertical axis
                drawContext.canvas.nativeCanvas.drawText(
                    "$dbm dBm",
                    center.x - 4.dp.toPx(),
                    center.y - ringRadius + 11.dp.toPx(),
                    ringLabelPaint
                )
            }

            // 2. Draw Crosshair Azimuth Grid Lines (0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°)
            val azimuthAngles = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
            azimuthAngles.forEach { deg ->
                val rad = (deg * PI / 180f).toFloat()
                val endX = center.x + maxRadarRadius * cos(rad)
                val endY = center.y + maxRadarRadius * sin(rad)
                drawLine(
                    color = Color(0x1F38BDF8),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Compass Cardinal Markers
            val cardinalLabels = listOf(
                "U" to Offset(center.x, center.y - maxRadarRadius - 6.dp.toPx()),
                "T" to Offset(center.x + maxRadarRadius + 12.dp.toPx(), center.y + 3.dp.toPx()),
                "S" to Offset(center.x, center.y + maxRadarRadius + 14.dp.toPx()),
                "B" to Offset(center.x - maxRadarRadius - 12.dp.toPx(), center.y + 3.dp.toPx())
            )
            cardinalLabels.forEach { (label, pos) ->
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    pos.x,
                    pos.y,
                    textPaint
                )
            }

            // 3. Draw Rotating Radar Sweep Cone
            if (isSweeping) {
                val currentRad = (sweepAngle * PI / 180f).toFloat()

                // Sweep pie wedge gradient (trailing glow)
                val sweepWedgeAngle = 45f
                val sweepPath = Path().apply {
                    moveTo(center.x, center.y)
                    val steps = 15
                    for (i in 0..steps) {
                        val angleFraction = i / steps.toFloat()
                        val aDeg = sweepAngle - (sweepWedgeAngle * (1f - angleFraction))
                        val aRad = (aDeg * PI / 180f).toFloat()
                        val edgeX = center.x + maxRadarRadius * cos(aRad)
                        val edgeY = center.y + maxRadarRadius * sin(aRad)
                        if (i == 0) lineTo(edgeX, edgeY) else lineTo(edgeX, edgeY)
                    }
                    close()
                }

                drawPath(
                    path = sweepPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentCyan.copy(alpha = 0.35f),
                            Color(0x1A00E5FF),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadarRadius
                    )
                )

                // Leading glowing beam line
                val leadX = center.x + maxRadarRadius * cos(currentRad)
                val leadY = center.y + maxRadarRadius * sin(currentRad)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, AccentCyan),
                        start = center,
                        end = Offset(leadX, leadY)
                    ),
                    start = center,
                    end = Offset(leadX, leadY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Center Origin Point (You / Device)
            drawCircle(
                color = AccentCyan,
                radius = 5.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = center
            )

            // 5. Plot WiFi AP Nodes on the Polar Chart
            accessPoints.forEachIndexed { index, ap ->
                val angleDeg = calculatedPositions.getOrElse(index) { 0f }
                val angleRad = (angleDeg * PI / 180f).toFloat()

                // Normalized radius: -30 dBm is center (0.15), -95 dBm is perimeter (0.95)
                val normDist = ((abs(ap.rssi) - 30f) / 65f).coerceIn(0.15f, 0.95f)
                val r = normDist * maxRadarRadius

                val nodeX = center.x + r * cos(angleRad)
                val nodeY = center.y + r * sin(angleRad)
                val isSelected = ap.bssid == selectedApBssid

                val nodeColor = when {
                    ap.rssi >= -55 -> SignalGreen
                    ap.rssi >= -67 -> AccentCyan
                    ap.rssi >= -75 -> SignalYellow
                    ap.rssi >= -85 -> SignalOrange
                    else -> SignalRed
                }

                // Node blip glow when radar beam is close
                val angleDiff = abs((sweepAngle - angleDeg + 360f) % 360f)
                val isLitBySweep = isSweeping && angleDiff < 30f

                // Pulse circle if connected
                if (ap.isConnected) {
                    drawCircle(
                        color = SignalGreen.copy(alpha = (1f - (pulseScale - 1f) / 0.8f).coerceIn(0f, 0.6f)),
                        radius = 8.dp.toPx() * pulseScale,
                        center = Offset(nodeX, nodeY)
                    )
                }

                // Selection ring
                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = 12.dp.toPx(),
                        center = Offset(nodeX, nodeY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Outer halo
                drawCircle(
                    color = nodeColor.copy(alpha = if (isLitBySweep || isSelected) 0.5f else 0.25f),
                    radius = if (isLitBySweep) 10.dp.toPx() else 7.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )

                // Main Node Dot
                drawCircle(
                    color = if (ap.isConnected) SignalGreen else nodeColor,
                    radius = 5.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )

                // Node Tag Label (SSID & dBm)
                val ssidPaint = android.graphics.Paint().apply {
                    color = if (isSelected) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#94A3B8")
                    textSize = 8.5.sp.toPx()
                    isFakeBoldText = isSelected || ap.isConnected
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                val tagLabel = if (ap.ssid.length > 9) ap.ssid.take(8) + "…" else ap.ssid
                drawContext.canvas.nativeCanvas.drawText(
                    "$tagLabel (${ap.rssi})",
                    nodeX,
                    nodeY + 14.dp.toPx(),
                    ssidPaint
                )
            }
        }
    }
}
