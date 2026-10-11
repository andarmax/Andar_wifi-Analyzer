package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SpeedTestEntity
import com.example.model.SpeedTestPhase
import com.example.model.SpeedTestState
import com.example.ui.components.SpeedometerGauge
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CyberBluePrimary
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SignalYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpeedTestScreen(
    speedState: SpeedTestState,
    historyList: List<SpeedTestEntity>,
    onStartTest: () -> Unit,
    onResetTest: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = speedState.phase == SpeedTestPhase.PING ||
            speedState.phase == SpeedTestPhase.DOWNLOAD ||
            speedState.phase == SpeedTestPhase.UPLOAD

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("speed_test_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Uji Kecepatan Internet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Download, Upload, Ping & Jitter riil",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Speedometer Dial Container
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val phaseText = when (speedState.phase) {
                        SpeedTestPhase.IDLE -> "SIAP"
                        SpeedTestPhase.PING -> "MENGUKUR PING"
                        SpeedTestPhase.DOWNLOAD -> "DOWNLOAD"
                        SpeedTestPhase.UPLOAD -> "UPLOAD"
                        SpeedTestPhase.FINISHED -> "SELESAI"
                        SpeedTestPhase.ERROR -> "GAGAL"
                    }

                    SpeedometerGauge(
                        currentSpeedMbps = speedState.currentTransferMbps,
                        phaseLabel = phaseText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isRunning) {
                        LinearProgressIndicator(
                            progress = { speedState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = AccentCyan,
                            trackColor = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = speedState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (speedState.phase == SpeedTestPhase.ERROR) Color(0xFFFF5252) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Sub-metrics cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBox(
                            title = "Download",
                            value = "%.1f".format(speedState.downloadMbps),
                            unit = "Mbps",
                            icon = Icons.Default.ArrowDownward,
                            color = AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            title = "Upload",
                            value = "%.1f".format(speedState.uploadMbps),
                            unit = "Mbps",
                            icon = Icons.Default.ArrowUpward,
                            color = Color(0xFFB388FF),
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            title = "Ping",
                            value = "%.1f".format(speedState.pingMs),
                            unit = "ms",
                            icon = Icons.Default.Speed,
                            color = SignalGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            title = "Jitter",
                            value = "%.1f".format(speedState.jitterMs),
                            unit = "ms",
                            icon = Icons.Default.Speed,
                            color = SignalYellow,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Button
                    Button(
                        onClick = onStartTest,
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberBluePrimary,
                            disabledContainerColor = Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_speed_test_button")
                    ) {
                        Icon(
                            imageVector = if (speedState.phase == SpeedTestPhase.FINISHED) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "Sedang Menguji..." else if (speedState.phase == SpeedTestPhase.FINISHED) "Uji Ulang Kecepatan" else "Mulai Uji Kecepatan",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (speedState.phase == SpeedTestPhase.FINISHED) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val textToShare = """
                                    ⚡ Hasil Uji Kecepatan (Andar WiFi Analyzer):
                                    📥 Unduh (Download): ${speedState.downloadMbps} Mbps
                                    📤 Unggah (Upload): ${speedState.uploadMbps} Mbps
                                    ⏱️ Ping: ${speedState.pingMs} ms (Jitter: ${speedState.jitterMs} ms)
                                    📶 Status: Selesai diuji dengan Andar WiFi Analyzer
                                """.trimIndent()

                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Hasil Uji Kecepatan"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("share_speed_result_button")
                        ) {
                            Text("Bagikan Hasil Pengujian", color = AccentCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Performance Verdict (Streaming, Gaming, Video Call)
        if (speedState.phase == SpeedTestPhase.FINISHED || speedState.downloadMbps > 0) {
            item {
                Text(
                    text = "Kemampuan Koneksi Anda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            item {
                OutlinedCard(
                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VerdictRow(
                            icon = Icons.Default.Tv,
                            title = "Streaming Video 4K Ultra HD",
                            rating = if (speedState.downloadMbps >= 25) "Sangat Lancar" else if (speedState.downloadMbps >= 10) "Full HD 1080p" else "Standar SD",
                            color = if (speedState.downloadMbps >= 25) SignalGreen else SignalYellow
                        )
                        VerdictRow(
                            icon = Icons.Default.Gamepad,
                            title = "Game Online Kompetitif",
                            rating = if (speedState.pingMs < 30) "Luar Biasa (<30ms)" else if (speedState.pingMs < 60) "Baik (<60ms)" else "Berpotensi Lag",
                            color = if (speedState.pingMs < 30) SignalGreen else if (speedState.pingMs < 60) AccentCyan else SignalOrange
                        )
                        VerdictRow(
                            icon = Icons.Default.VideoCall,
                            title = "Video Call & Zoom HD",
                            rating = if (speedState.uploadMbps >= 5 && speedState.downloadMbps >= 10) "Kualitas Jernih & Stabil" else "Cukup",
                            color = SignalGreen
                        )
                    }
                }
            }
        }

        // Test History Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Riwayat Pengujian (${historyList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (historyList.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Riwayat",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        if (historyList.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada riwayat pengujian tersimpan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else {
            items(historyList) { item ->
                HistoryItem(item)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "$title ($unit)",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun VerdictRow(
    icon: ImageVector,
    title: String,
    rating: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        Text(
            text = rating,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun HistoryItem(item: SpeedTestEntity) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(item.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = item.ssid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "↓ %.1f Mbps".format(item.downloadMbps),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "↑ %.1f Mbps".format(item.uploadMbps),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB388FF),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%.0f ms".format(item.pingMs),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Ping",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}
