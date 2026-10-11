package com.andar.wifianalyzer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andar.wifianalyzer.model.DnsPingStatus
import com.andar.wifianalyzer.model.DnsServerPingItem
import com.andar.wifianalyzer.model.LanScanState
import com.andar.wifianalyzer.model.PingResult
import com.andar.wifianalyzer.model.PortScanState
import com.andar.wifianalyzer.service.NetworkValidator
import com.andar.wifianalyzer.ui.theme.AccentCyan
import com.andar.wifianalyzer.ui.theme.CyberBluePrimary
import com.andar.wifianalyzer.ui.theme.SignalGreen
import com.andar.wifianalyzer.ui.theme.SignalOrange
import com.andar.wifianalyzer.ui.theme.SignalRed
import com.andar.wifianalyzer.ui.theme.SignalYellow

@Composable
fun DiagnosticsScreen(
    pingResult: PingResult,
    signalHistory: List<Int>,
    portScanState: PortScanState,
    lanScanState: LanScanState,
    gatewayIp: String,
    dnsServerList: List<DnsServerPingItem>,
    isTestingDns: Boolean,
    onRunPing: (String) -> Unit,
    onCancelPing: () -> Unit,
    onScanPorts: (String) -> Unit,
    onCancelPortScan: () -> Unit,
    onStartDnsBenchmark: () -> Unit,
    onCancelDnsBenchmark: () -> Unit,
    onPingSingleDns: (DnsServerPingItem) -> Unit,
    onStartLanScan: () -> Unit,
    onCancelLanScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pingTarget by remember { mutableStateOf("8.8.8.8") }
    var portTarget by remember(gatewayIp) {
        mutableStateOf(if (NetworkValidator.isValidIpv4(gatewayIp)) gatewayIp else "192.168.1.1")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("diagnostics_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Alat Diagnostik Jaringan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Benchmark DNS, TCP Latency, Fluktuasi dBm, Pemindai Port & LAN Lokal",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }

        // Ethical / Defensive Notice Card
        item {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0F2238)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("defensive_authorization_notice")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Pemberitahuan Diagnostik Defensif: Pemindaian port dan perangkat LAN dibatasi hanya untuk jaringan lokal milik sendiri atau lab yang memiliki izin resmi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // DNS PING BENCHMARK SECTION
        item {
            DnsBenchmarkSection(
                dnsServerList = dnsServerList,
                isTestingDns = isTestingDns,
                onStartDnsBenchmark = onStartDnsBenchmark,
                onCancelDnsBenchmark = onCancelDnsBenchmark,
                onPingSingleDns = onPingSingleDns
            )
        }

        // Real-Time Signal Level Timeline
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val lastDbm = signalHistory.lastOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Grafik Fluktuasi Sinyal (Real-Time)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = if (lastDbm != null) "$lastDbm dBm" else "Belum ada sampel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Line Graph Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (signalHistory.isEmpty()) {
                            Text(
                                text = "Hubungkan ke jaringan WiFi untuk merekam riwayat sinyal dBm.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        } else {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val minDbm = -95f
                                val maxDbm = -35f
                                val width = size.width
                                val height = size.height

                                if (signalHistory.size > 1) {
                                    val stepX = width / (signalHistory.size - 1).toFloat()
                                    val path = Path()
                                    signalHistory.forEachIndexed { index, dbm ->
                                        val fraction = ((dbm.toFloat() - minDbm) / (maxDbm - minDbm)).coerceIn(0f, 1f)
                                        val y = height * (1f - fraction)
                                        val x = index * stepX
                                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }

                                    drawPath(
                                        path = path,
                                        color = AccentCyan,
                                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                val currentSample = signalHistory.last()
                                val lastFraction = ((currentSample.toFloat() - minDbm) / (maxDbm - minDbm)).coerceIn(0f, 1f)
                                drawCircle(
                                    color = Color.White,
                                    radius = 4.dp.toPx(),
                                    center = Offset(
                                        if (signalHistory.size > 1) width else width / 2f,
                                        height * (1f - lastFraction)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Bergeraklah di sekitar ruangan untuk memeriksa zona mati (dead spot) sinyal WiFi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        // TCP LATENCY / PING UTILITY SECTION (CUSTOM HOST)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Uji ${pingResult.methodLabel} Host Kustom",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = pingResult.methodLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pingTarget,
                        onValueChange = { pingTarget = it },
                        label = { Text("Host atau Alamat IP") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ping_target_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = buildList {
                            add("8.8.8.8 (Google)")
                            add("1.1.1.1 (Cloudflare)")
                            if (NetworkValidator.isValidIpv4(gatewayIp)) {
                                add("$gatewayIp (Gateway)")
                            }
                        }
                        items(presets) { preset ->
                            val cleanTarget = preset.substringBefore(" ")
                            FilterChip(
                                selected = pingTarget == cleanTarget,
                                onClick = { pingTarget = cleanTarget },
                                label = { Text(preset, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberBluePrimary.copy(alpha = 0.3f),
                                    selectedLabelColor = AccentCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { if (pingResult.isRunning) onCancelPing() else onRunPing(pingTarget) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pingResult.isRunning) SignalRed else CyberBluePrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_ping_button")
                    ) {
                        Icon(
                            imageVector = if (pingResult.isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (pingResult.isRunning) "Batalkan Uji Latency" else "Mulai Uji TCP Latency",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Terminal log console
                    if (pingResult.logLines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(Color(0xFF0A0F1A), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            LazyColumn {
                                items(pingResult.logLines) { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = when {
                                            line.contains("time=") -> SignalGreen
                                            line.contains("Timeout") || line.contains("Gagal") -> SignalRed
                                            else -> Color(0xFF94A3B8)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Paket: ${pingResult.sent} dikirim / ${pingResult.received} diterima (Loss: ${pingResult.lossPercent}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            Text(
                                text = if (pingResult.received > 0) {
                                    "Min/Avg/Max: %.0f/%.1f/%.0f ms".format(pingResult.minMs, pingResult.avgMs, pingResult.maxMs)
                                } else {
                                    "Tidak terjangkau"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (pingResult.received > 0) SignalGreen else SignalRed
                            )
                        }
                    }
                }
            }
        }

        // PORT SCANNER SECTION (LOCAL / PRIVATE IP ONLY)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Pemindai Port Lokal (RFC1918 / Localhost)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Hanya menerima IP privat (10.x, 172.16-31.x, 192.168.x) atau localhost",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = portTarget,
                            onValueChange = { portTarget = it },
                            label = { Text("Target IP Privat / Router") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("port_target_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { if (portScanState.isScanning) onCancelPortScan() else onScanPorts(portTarget) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (portScanState.isScanning) SignalRed else CyberBluePrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("scan_ports_button")
                        ) {
                            if (portScanState.isScanning) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Batalkan", modifier = Modifier.size(18.dp))
                            } else {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Pindai Port", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = portScanState.errorMessage ?: portScanState.statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (portScanState.errorMessage != null) SignalRed else Color(0xFF94A3B8)
                    )

                    if (portScanState.results.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            portScanState.results.forEach { port ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Port ${port.port}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(${port.serviceName})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    if (port.isOpen) SignalGreen else SignalRed,
                                                    CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (port.isOpen) "Terbuka" else "Tertutup",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (port.isOpen) SignalGreen else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // LAN SCANNER SECTION (CONSOLIDATED, NO TRUNCATION)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lan_scanner_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pemindai Perangkat LAN (${lanScanState.devices.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = lanScanState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (lanScanState.errorMessage != null) SignalRed else Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { if (lanScanState.isScanning) onCancelLanScan() else onStartLanScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (lanScanState.isScanning) SignalRed else CyberBluePrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("scan_lan_diag_button")
                        ) {
                            if (lanScanState.isScanning) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Batal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pindai LAN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (lanScanState.isScanning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { lanScanState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = AccentCyan,
                            trackColor = Color(0xFF1E293B)
                        )
                    }

                    if (lanScanState.devices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            lanScanState.devices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (device.isGateway) Color(0xFF132A46) else Color(0xFF0F172A),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (device.isGateway) CyberBluePrimary.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    device.isGateway -> Icons.Default.Router
                                                    device.isLocalDevice -> Icons.Default.PhoneAndroid
                                                    else -> Icons.Default.Computer
                                                },
                                                contentDescription = null,
                                                tint = if (device.isGateway) SignalGreen else AccentCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = device.hostname ?: device.ip,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                if (device.isGateway) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(SignalGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("Gateway", color = SignalGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                } else if (device.isLocalDevice) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(AccentCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("Perangkat Ini", color = AccentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "IP: ${device.ip}${device.vendor?.let { " • $it" } ?: ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF94A3B8),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${device.pingMs} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalGreen,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Catatan: Deteksi tanpa akses root menggunakan ICMP/TCP reachability; perangkat dengan firewall ketat mungkin tidak merespons.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DnsBenchmarkSection(
    dnsServerList: List<DnsServerPingItem>,
    isTestingDns: Boolean,
    onStartDnsBenchmark: () -> Unit,
    onCancelDnsBenchmark: () -> Unit,
    onPingSingleDns: (DnsServerPingItem) -> Unit
) {
    val fastestDns = dnsServerList.firstOrNull {
        it.isFastest && it.status == DnsPingStatus.COMPLETED && it.latencyMs > 0f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dns_benchmark_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(CyberBluePrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Uji Latency DNS Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Ukur TCP latency ke port 53 DNS populer",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = { if (isTestingDns) onCancelDnsBenchmark() else onStartDnsBenchmark() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTestingDns) SignalRed else CyberBluePrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("start_dns_benchmark_button")
                ) {
                    if (isTestingDns) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Batal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tes Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Fastest recommendation banner
            if (fastestDns != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedCard(
                    colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0B2138)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "DNS Tercepat: ${fastestDns.name} (${fastestDns.primaryIp})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SignalGreen
                            )
                            Text(
                                text = "Latency terendah: ${fastestDns.latencyMs} ms • Rekomendasi untuk jaringan Anda saat ini",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DNS Servers List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dnsServerList.forEach { dnsItem ->
                    DnsServerRowItem(
                        dnsItem = dnsItem,
                        onPing = { onPingSingleDns(dnsItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DnsServerRowItem(
    dnsItem: DnsServerPingItem,
    onPing: () -> Unit
) {
    val latencyColor = when {
        dnsItem.latencyMs <= 0f -> Color(0xFF64748B)
        dnsItem.latencyMs < 25f -> SignalGreen
        dnsItem.latencyMs < 50f -> Color(0xFF00E5FF)
        dnsItem.latencyMs < 80f -> SignalYellow
        else -> SignalRed
    }

    val ratingText = when {
        dnsItem.latencyMs <= 0f -> ""
        dnsItem.latencyMs < 25f -> "Sangat Cepat"
        dnsItem.latencyMs < 50f -> "Bagus"
        dnsItem.latencyMs < 80f -> "Sedang"
        else -> "Lambat"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (dnsItem.isFastest) Color(0xFF132B45) else Color(0xFF0F172A),
                RoundedCornerShape(10.dp)
            )
            .clickable { onPing() }
            .padding(12.dp)
            .testTag("dns_item_${dnsItem.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dnsItem.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (dnsItem.isFastest) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFB45309).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Tercepat ⚡",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD600),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dnsItem.primaryIp,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    if (dnsItem.secondaryIp != null) {
                        Text(
                            text = " • ${dnsItem.secondaryIp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dnsItem.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Latency & Action Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (dnsItem.status) {
                    DnsPingStatus.TESTING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Uji...",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                    DnsPingStatus.COMPLETED -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "%.1f ms".format(dnsItem.latencyMs),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = latencyColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            Text(
                                text = ratingText,
                                style = MaterialTheme.typography.labelSmall,
                                color = latencyColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    DnsPingStatus.TIMEOUT -> {
                        Text(
                            text = "Timeout",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DnsPingStatus.UNREACHABLE -> {
                        Text(
                            text = "Unreachable",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DnsPingStatus.OFFLINE -> {
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DnsPingStatus.FAILED -> {
                        Text(
                            text = dnsItem.errorMessage ?: "Gagal",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DnsPingStatus.IDLE -> {
                        Text(
                            text = "Ketuk uji",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onPing,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Uji DNS ini",
                        tint = if (dnsItem.status == DnsPingStatus.TESTING) Color(0xFF334155) else AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
