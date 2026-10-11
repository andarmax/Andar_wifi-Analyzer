package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WifiAccessPoint
import com.example.model.WifiBand
import com.example.ui.theme.SpectrumColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

@Composable
fun ChannelSpectrumGraph(
    band: WifiBand,
    accessPoints: List<WifiAccessPoint>,
    selectedApBssid: String? = null,
    modifier: Modifier = Modifier
) {
    val filteredAps = accessPoints.filter { it.band == band }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("channel_spectrum_graph")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val bottomMargin = 28.dp.toPx()
                val topMargin = 22.dp.toPx()
                val leftMargin = 38.dp.toPx()
                val graphWidth = canvasWidth - leftMargin - 12.dp.toPx()
                val graphHeight = canvasHeight - bottomMargin - topMargin

                // dBm scale: from -100 dBm (bottom) to -30 dBm (top)
                val minDbm = -100f
                val maxDbm = -30f

                fun dbmToY(dbm: Float): Float {
                    val fraction = (dbm - minDbm) / (maxDbm - minDbm)
                    return topMargin + graphHeight * (1f - fraction.coerceIn(0f, 1f))
                }

                // Draw horizontal dBm grid lines (-90, -70, -50, -30)
                val gridDbms = listOf(-90f, -70f, -50f, -30f)
                val gridPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#64748B")
                    textSize = 9.sp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }

                for (dbm in gridDbms) {
                    val y = dbmToY(dbm)
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(leftMargin, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "${dbm.toInt()}dB",
                        leftMargin - 6.dp.toPx(),
                        y + 3.dp.toPx(),
                        gridPaint
                    )
                }

                // X-Axis mapping based on Band
                val channelList = if (band == WifiBand.BAND_2_4_GHZ) {
                    (1..14).toList()
                } else {
                    listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165)
                }

                val minFreq = if (band == WifiBand.BAND_2_4_GHZ) 2400f else 5160f
                val maxFreq = if (band == WifiBand.BAND_2_4_GHZ) 2490f else 5840f

                fun freqToX(freq: Float): Float {
                    val fraction = (freq - minFreq) / (maxFreq - minFreq)
                    return leftMargin + (graphWidth * fraction.coerceIn(0f, 1f))
                }

                // Channel tick labels
                val channelTextPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val keyChannelTextPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#00E5FF")
                    textSize = 11.sp.toPx()
                    isFakeBoldText = true
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                // Draw vertical channel markers
                for (ch in channelList) {
                    val freq = if (band == WifiBand.BAND_2_4_GHZ) {
                        if (ch == 14) 2484f else (2407f + ch * 5f)
                    } else {
                        (5000f + ch * 5f)
                    }

                    val x = freqToX(freq)
                    val isKeyChannel = (band == WifiBand.BAND_2_4_GHZ && (ch == 1 || ch == 6 || ch == 11)) ||
                                       (band == WifiBand.BAND_5_GHZ && (ch == 36 || ch == 48 || ch == 149 || ch == 161))

                    // Channel dashed indicator line
                    drawLine(
                        color = if (isKeyChannel) Color(0x3300E5FF) else Color(0x15FFFFFF),
                        start = Offset(x, topMargin),
                        end = Offset(x, topMargin + graphHeight),
                        strokeWidth = if (isKeyChannel) 1.5.dp.toPx() else 1.dp.toPx()
                    )

                    // Draw label at bottom
                    val paintToUse = if (isKeyChannel) keyChannelTextPaint else channelTextPaint
                    drawContext.canvas.nativeCanvas.drawText(
                        "$ch",
                        x,
                        topMargin + graphHeight + 16.dp.toPx(),
                        paintToUse
                    )
                }

                // Draw Parabolic Spectral Curves for Access Points
                val baseY = dbmToY(-100f)

                filteredAps.forEachIndexed { index, ap ->
                    val color = SpectrumColors[index % SpectrumColors.size]
                    val isSelected = ap.bssid == selectedApBssid || (selectedApBssid == null && ap.isConnected)

                    val centerFreq = ap.frequency.toFloat()
                    val halfWidthFreq = (ap.channelWidth.toFloat()) / 2f + 2f // spread curve slightly
                    val leftFreq = centerFreq - halfWidthFreq
                    val rightFreq = centerFreq + halfWidthFreq

                    val centerX = freqToX(centerFreq)
                    val leftX = freqToX(leftFreq)
                    val rightX = freqToX(rightFreq)
                    val peakY = dbmToY(ap.rssi.toFloat())

                    // Build parabolic curve: left base -> apex peak -> right base
                    val path = Path().apply {
                        moveTo(leftX, baseY)
                        // Cubic curve for smooth WiFi spectrum bell curve
                        val cp1X = (leftX + centerX) / 2f
                        val cp2X = (centerX + rightX) / 2f
                        cubicTo(cp1X, baseY, leftX + (centerX - leftX) * 0.3f, peakY, centerX, peakY)
                        cubicTo(rightX - (rightX - centerX) * 0.3f, peakY, cp2X, baseY, rightX, baseY)
                        close()
                    }

                    // Fill under curve
                    drawPath(
                        path = path,
                        color = color.copy(alpha = if (isSelected) 0.35f else 0.15f)
                    )

                    // Outline curve
                    drawPath(
                        path = path,
                        color = if (isSelected) Color.White else color,
                        style = Stroke(
                            width = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx()
                        )
                    )

                    // Peak tag text (SSID & dBm)
                    val ssidPaint = android.graphics.Paint().apply {
                        this.color = if (isSelected) android.graphics.Color.WHITE else color.hashCode()
                        textSize = 9.sp.toPx()
                        isFakeBoldText = isSelected
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    val tagY = max(topMargin + 6.dp.toPx(), peakY - 4.dp.toPx())
                    val displaySsid = if (ap.ssid.length > 10) ap.ssid.take(9) + "…" else ap.ssid
                    drawContext.canvas.nativeCanvas.drawText(
                        "$displaySsid (${ap.rssi}dBm)",
                        centerX,
                        tagY,
                        ssidPaint
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Spectrum Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Keterangan Saluran:",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF00E5FF), CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (band == WifiBand.BAND_2_4_GHZ) "Ch 1, 6, 11 (Bebas Tumpukan)" else "Saluran Utama Bebas Interferensi",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
