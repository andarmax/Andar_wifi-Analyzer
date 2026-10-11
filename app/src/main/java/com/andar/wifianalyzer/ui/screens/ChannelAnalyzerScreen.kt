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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andar.wifianalyzer.model.ChannelRating
import com.andar.wifianalyzer.model.WifiAccessPoint
import com.andar.wifianalyzer.model.WifiBand
import com.andar.wifianalyzer.model.WifiScanState
import com.andar.wifianalyzer.model.WifiScanStatus
import com.andar.wifianalyzer.ui.components.ChannelSpectrumGraph
import com.andar.wifianalyzer.ui.theme.AccentCyan
import com.andar.wifianalyzer.ui.theme.CyberBluePrimary
import com.andar.wifianalyzer.ui.theme.SignalGreen
import com.andar.wifianalyzer.ui.theme.SignalOrange
import com.andar.wifianalyzer.ui.theme.SignalRed
import com.andar.wifianalyzer.ui.theme.SignalYellow

@Composable
fun ChannelAnalyzerScreen(
    selectedBand: WifiBand,
    accessPoints: List<WifiAccessPoint>,
    channelRatings: List<ChannelRating>,
    bestChannel: ChannelRating?,
    currentChannel: Int,
    wifiScanState: WifiScanState,
    onSelectBand: (WifiBand) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedApBssid by remember { mutableStateOf<String?>(null) }

    val selectedTabIndex = when (selectedBand) {
        WifiBand.BAND_2_4_GHZ -> 0
        WifiBand.BAND_5_GHZ -> 1
        WifiBand.BAND_6_GHZ -> 2
        WifiBand.UNKNOWN -> 0
    }

    val bandAccessPoints = remember(accessPoints, selectedBand) {
        accessPoints.filter { it.band == selectedBand }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("channel_analyzer_screen"),
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
                        text = "Analisis Saluran WiFi",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Spektrum frekuensi & rekomendasi channel nyata",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_channel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Pindai Ulang",
                        tint = AccentCyan
                    )
                }
            }
        }

        if (wifiScanState.status != WifiScanStatus.SUCCESS) {
            item {
                ScanStatusBanner(wifiScanState = wifiScanState)
            }
        }

        // Band Switcher (2.4 GHz, 5 GHz, 6 GHz)
        item {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF131D2E),
                contentColor = AccentCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("band_selector_tab_row")
            ) {
                Tab(
                    selected = selectedBand == WifiBand.BAND_2_4_GHZ,
                    onClick = { onSelectBand(WifiBand.BAND_2_4_GHZ) },
                    text = {
                        Text(
                            text = "2.4 GHz",
                            fontWeight = if (selectedBand == WifiBand.BAND_2_4_GHZ) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_band_2_4")
                )
                Tab(
                    selected = selectedBand == WifiBand.BAND_5_GHZ,
                    onClick = { onSelectBand(WifiBand.BAND_5_GHZ) },
                    text = {
                        Text(
                            text = "5 GHz",
                            fontWeight = if (selectedBand == WifiBand.BAND_5_GHZ) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_band_5")
                )
                Tab(
                    selected = selectedBand == WifiBand.BAND_6_GHZ,
                    onClick = { onSelectBand(WifiBand.BAND_6_GHZ) },
                    text = {
                        Text(
                            text = "6 GHz",
                            fontWeight = if (selectedBand == WifiBand.BAND_6_GHZ) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_band_6")
                )
            }
        }

        // Spectrum Graph
        item {
            ChannelSpectrumGraph(
                band = selectedBand,
                accessPoints = accessPoints,
                selectedApBssid = selectedApBssid
            )
        }

        // Best Channel Recommendation Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2442)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CyberBluePrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rekomendasi Saluran Terbaik (${selectedBand.displayName}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = if (bestChannel != null) {
                                "Saluran ${bestChannel.channel} (${bestChannel.frequency} MHz • ${bestChannel.statusLabel})"
                            } else {
                                "Belum ada data hasil pemindaian"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (bestChannel != null) SignalGreen else Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (selectedBand) {
                                WifiBand.BAND_2_4_GHZ ->
                                    "Memprioritaskan saluran non-overlapping (1, 6, 11, dan hingga 13/14 jika didukung regulasi perangkat) serta memperhitungkan interferensi co-channel dan adjacent-channel (±4 saluran)."
                                WifiBand.BAND_5_GHZ ->
                                    "Saluran 5 GHz menyediakan kanal non-overlapping dengan interferensi lebih rendah untuk kecepatan tinggi."
                                WifiBand.BAND_6_GHZ ->
                                    "Saluran 6 GHz (WiFi 6E/7 PSC) menawarkan spektrum bersih berkapasitas tinggi untuk perangkat yang mendukung."
                                WifiBand.UNKNOWN ->
                                    "Pilih pita frekuensi untuk melihat analisis spektrum."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Channel Ratings Section
        item {
            Text(
                text = "Peringkat & Kepadatan Saluran (${selectedBand.displayName})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        items(channelRatings, key = { "${it.band.name}_${it.channel}" }) { rating ->
            ChannelRatingCard(rating = rating, currentChannel = currentChannel)
        }

        // Access Points List Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Jaringan Terdeteksi (${bandAccessPoints.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (bandAccessPoints.isEmpty()) {
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
                            text = "Tidak ada jaringan terdeteksi pada pita ${selectedBand.displayName}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        } else {
            items(bandAccessPoints, key = { it.bssid }) { ap ->
                AccessPointItem(
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
private fun ChannelRatingCard(
    rating: ChannelRating,
    currentChannel: Int
) {
    val isCurrent = rating.channel == currentChannel

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isCurrent) Color(0xFF132A46) else Color(0xFF0F172A)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isCurrent) CyberBluePrimary else Color(0xFF1E293B),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${rating.channel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ch ${rating.channel} (${rating.frequency} MHz)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(SignalGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Aktif",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        if (rating.isNonOverlapping && rating.band == WifiBand.BAND_2_4_GHZ) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(AccentCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Non-Overlap",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentCyan,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "${rating.statusLabel} • ${rating.apCount} AP di saluran (${rating.overlappingApCount} memengaruhi)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            // Star rating
            Row {
                for (s in 1..5) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (s <= rating.stars) Color(0xFFFFD600) else Color(0xFF334155),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessPointItem(
    ap: WifiAccessPoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val signalColor = when {
        ap.rssi >= -55 -> SignalGreen
        ap.rssi >= -67 -> Color(0xFF00E5FF)
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
            .testTag("ap_item_${ap.channel}_${ap.ssid.take(6)}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = signalColor,
                        modifier = Modifier.size(20.dp)
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

                Text(
                    text = "${ap.rssi} dBm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = signalColor,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tech specs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ch ${ap.channel} • ${ap.frequency} MHz • ${ap.channelWidth} MHz",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "Perkiraan: ~${ap.distanceMeters} m",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MAC: ${ap.bssid}",
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
