package com.andar.wifianalyzer.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andar.wifianalyzer.model.ConnectedWifiInfo
import com.andar.wifianalyzer.model.WifiAccessPoint
import com.andar.wifianalyzer.model.WifiBand
import com.andar.wifianalyzer.model.WifiScanState
import com.andar.wifianalyzer.model.WifiScanStatus
import com.andar.wifianalyzer.ui.components.SignalRadarPolarChart
import com.andar.wifianalyzer.ui.theme.AccentCyan
import com.andar.wifianalyzer.ui.theme.CyberBluePrimary
import com.andar.wifianalyzer.ui.theme.SignalGreen
import com.andar.wifianalyzer.ui.theme.SignalOrange
import com.andar.wifianalyzer.ui.theme.SignalRed
import com.andar.wifianalyzer.ui.theme.SignalYellow

@Composable
fun SignalRadarScreen(
    accessPoints: List<WifiAccessPoint>,
    connectedInfo: ConnectedWifiInfo,
    wifiScanState: WifiScanState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBandFilter by remember { mutableStateOf<WifiBand?>(null) } // null = All
    var selectedApBssid by remember(connectedInfo.bssid) {
        mutableStateOf(connectedInfo.bssid.takeIf { connectedInfo.isConnected })
    }
    var isSweeping by remember { mutableStateOf(true) }

    val filteredAps = remember(accessPoints, selectedBandFilter) {
        if (selectedBandFilter == null) accessPoints
        else accessPoints.filter { it.band == selectedBandFilter }
    }

    val selectedAp = remember(filteredAps, accessPoints, selectedApBssid) {
        filteredAps.firstOrNull { it.bssid == selectedApBssid }
            ?: accessPoints.firstOrNull { it.bssid == selectedApBssid }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("signal_radar_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Radar Sinyal WiFi",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Visualisasi polar chart kekuatan sinyal nyata (dBm)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row {
                    IconButton(
                        onClick = { isSweeping = !isSweeping },
                        modifier = Modifier.testTag("toggle_sweep_button")
                    ) {
                        Icon(
                            imageVector = if (isSweeping) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isSweeping) "Jeda Radar" else "Mulai Radar",
                            tint = AccentCyan
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_radar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Pindai Ulang",
                            tint = AccentCyan
                        )
                    }
                }
            }
        }

        if (wifiScanState.status != WifiScanStatus.SUCCESS) {
            item {
                ScanStatusBanner(wifiScanState = wifiScanState)
            }
        }

        // Band Filter Chips (including 6 GHz)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )

                FilterChip(
                    selected = selectedBandFilter == null,
                    onClick = { selectedBandFilter = null },
                    label = { Text("Semua (${accessPoints.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBluePrimary.copy(alpha = 0.35f),
                        selectedLabelColor = AccentCyan
                    ),
                    modifier = Modifier.testTag("radar_filter_all")
                )

                FilterChip(
                    selected = selectedBandFilter == WifiBand.BAND_2_4_GHZ,
                    onClick = { selectedBandFilter = WifiBand.BAND_2_4_GHZ },
                    label = { Text("2.4 GHz", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBluePrimary.copy(alpha = 0.35f),
                        selectedLabelColor = AccentCyan
                    ),
                    modifier = Modifier.testTag("radar_filter_2_4")
                )

                FilterChip(
                    selected = selectedBandFilter == WifiBand.BAND_5_GHZ,
                    onClick = { selectedBandFilter = WifiBand.BAND_5_GHZ },
                    label = { Text("5 GHz", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBluePrimary.copy(alpha = 0.35f),
                        selectedLabelColor = AccentCyan
                    ),
                    modifier = Modifier.testTag("radar_filter_5")
                )

                FilterChip(
                    selected = selectedBandFilter == WifiBand.BAND_6_GHZ,
                    onClick = { selectedBandFilter = WifiBand.BAND_6_GHZ },
                    label = { Text("6 GHz", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBluePrimary.copy(alpha = 0.35f),
                        selectedLabelColor = AccentCyan
                    ),
                    modifier = Modifier.testTag("radar_filter_6")
                )
            }
        }

        // Polar Chart Radar Container
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SignalRadarPolarChart(
                        accessPoints = filteredAps,
                        selectedApBssid = selectedApBssid,
                        onSelectAp = { selectedApBssid = it.bssid },
                        isSweeping = isSweeping
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Radar Range Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RadarRangeBadge(label = "Tengah (< -40 dBm)", desc = "Sangat Dekat", color = SignalGreen)
                        RadarRangeBadge(label = "Tengah-Luar (-60 dBm)", desc = "Kuat", color = AccentCyan)
                        RadarRangeBadge(label = "Pinggir (> -80 dBm)", desc = "Jauh/Lemah", color = SignalOrange)
                    }
                }
            }
        }

        // Selected Access Point Highlight Card
        if (selectedAp != null) {
            item {
                SelectedApDetailCard(
                    ap = selectedAp,
                    onDeselect = { selectedApBssid = null }
                )
            }
        }

        // List of Mapped Networks
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jaringan Pada Radar (${filteredAps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Ketuk untuk fokus di radar",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        if (filteredAps.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = wifiScanState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        } else {
            items(filteredAps, key = { it.bssid }) { ap ->
                RadarNetworkListItem(
                    ap = ap,
                    isSelected = ap.bssid == selectedApBssid,
                    onClick = {
                        selectedApBssid = if (selectedApBssid == ap.bssid) null else ap.bssid
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RadarRangeBadge(
    label: String,
    desc: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF64748B),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun SelectedApDetailCard(
    ap: WifiAccessPoint,
    onDeselect: () -> Unit
) {
    val signalColor = when {
        ap.rssi >= -55 -> SignalGreen
        ap.rssi >= -67 -> AccentCyan
        ap.rssi >= -75 -> SignalYellow
        ap.rssi >= -85 -> SignalOrange
        else -> SignalRed
    }

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0F2238)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(signalColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ap.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (ap.isConnected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(SignalGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Tersambung",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${ap.rssi} dBm",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = signalColor,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDeselect,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("deselect_ap_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup fokus AP",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
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
                    text = "Band: ${ap.band.displayName} • Ch ${ap.channel} (${ap.frequency} MHz)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
                Text(
                    text = "Perkiraan FSPL: ~${ap.distanceMeters} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "BSSID: ${ap.bssid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${ap.security} • ${ap.standard}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun RadarNetworkListItem(
    ap: WifiAccessPoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val signalColor = when {
        ap.rssi >= -55 -> SignalGreen
        ap.rssi >= -67 -> AccentCyan
        ap.rssi >= -75 -> SignalYellow
        ap.rssi >= -85 -> SignalOrange
        else -> SignalRed
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E2D4A) else Color(0xFF131D2E)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("radar_ap_item_${ap.bssid.take(6)}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isSelected) AccentCyan.copy(alpha = 0.2f) else Color(0xFF0F172A),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = signalColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = ap.ssid,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Ch ${ap.channel} (${ap.band.displayName}) • ~${ap.distanceMeters} m",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${ap.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = signalColor,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${ap.signalPercent}% Sinyal",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
