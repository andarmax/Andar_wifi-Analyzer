package com.andar.wifianalyzer.ui.screens

import android.content.Intent
import androidx.core.net.toUri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andar.wifianalyzer.model.ConnectedWifiInfo
import com.andar.wifianalyzer.model.WifiScanState
import com.andar.wifianalyzer.model.WifiScanStatus
import com.andar.wifianalyzer.service.NetworkValidator
import com.andar.wifianalyzer.ui.MainNavTab
import com.andar.wifianalyzer.ui.components.SignalMeterGauge
import com.andar.wifianalyzer.ui.theme.AccentCyan
import com.andar.wifianalyzer.ui.theme.CyberBluePrimary
import com.andar.wifianalyzer.ui.theme.SignalGreen
import com.andar.wifianalyzer.ui.theme.SignalOrange
import com.andar.wifianalyzer.ui.theme.SignalRed
import com.andar.wifianalyzer.ui.theme.SignalYellow

@Composable
fun DashboardScreen(
    connectedInfo: ConnectedWifiInfo,
    wifiScanState: WifiScanState,
    isFetchingPublicIp: Boolean,
    onRefresh: () -> Unit,
    onRefreshPublicIp: () -> Unit,
    onNavigateTab: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isValidRouterGateway = NetworkValidator.isPrivateRfc1918Ipv4(connectedInfo.gateway)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
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
                        text = "Status WiFi Aktif",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Pemantauan sinyal & parameter jaringan nyata",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_dashboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang",
                        tint = AccentCyan
                    )
                }
            }
        }

        // System / Scan Status Banner when attention is needed
        if (wifiScanState.status != WifiScanStatus.SUCCESS && wifiScanState.status != WifiScanStatus.IDLE) {
            item {
                ScanStatusBanner(wifiScanState = wifiScanState)
            }
        }

        // Signal Gauge & Primary Connection Card
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
                    SignalMeterGauge(
                        rssi = connectedInfo.rssi,
                        signalPercent = connectedInfo.signalPercent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (connectedInfo.isConnected) SignalGreen else SignalRed,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = connectedInfo.ssid,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BSSID: ${connectedInfo.bssid} • Keamanan: ${connectedInfo.security}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Quick Stats Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickStatBadge(
                            label = "Kecepatan Link",
                            value = if (connectedInfo.linkSpeedMbps > 0) "${connectedInfo.linkSpeedMbps} Mbps" else "Tidak tersedia",
                            color = AccentCyan
                        )
                        QuickStatBadge(
                            label = "Frekuensi",
                            value = if (connectedInfo.frequency > 0) "${connectedInfo.frequency} MHz" else "Tidak tersedia",
                            color = Color(0xFFB388FF)
                        )
                        QuickStatBadge(
                            label = "Saluran / Band",
                            value = if (connectedInfo.channel > 0) "Ch ${connectedInfo.channel} (${connectedInfo.band.displayName})" else "Tidak tersedia",
                            color = SignalGreen
                        )
                        QuickStatBadge(
                            label = "Standar",
                            value = connectedInfo.wifiStandard.take(14),
                            color = Color(0xFFFFD600)
                        )
                    }
                }
            }
        }

        // Quick Action Shortcuts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onNavigateTab(MainNavTab.SPEED_TEST) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_speed_test")
                ) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kecepatan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = { onNavigateTab(MainNavTab.RADAR) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF132A46),
                        contentColor = AccentCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_radar")
                ) {
                    Icon(imageVector = Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Radar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = { onNavigateTab(MainNavTab.CHANNELS) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1B283E),
                        contentColor = AccentCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_channel_analyzer")
                ) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Saluran", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Detailed Network Configuration
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rincian Konfigurasi Jaringan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isFetchingPublicIp) "Memeriksa IP Publik..." else "Ketuk ikon muat ulang untuk cek IP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        item {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(icon = Icons.Default.Router, title = "IP Gateway / Router", value = connectedInfo.gateway)
                    InfoRow(icon = Icons.Default.Wifi, title = "IP Lokal Perangkat", value = connectedInfo.ipAddress)
                    InfoRow(
                        icon = Icons.Default.NetworkCheck,
                        title = "Subnet Mask",
                        value = if (connectedInfo.prefixLength > 0) "${connectedInfo.subnetMask} (/${connectedInfo.prefixLength})" else connectedInfo.subnetMask
                    )
                    InfoRow(icon = Icons.Default.Dns, title = "DNS Server", value = "${connectedInfo.dns1} / ${connectedInfo.dns2}")
                    InfoRow(
                        icon = Icons.Default.Public,
                        title = "IP Publik",
                        value = if (isFetchingPublicIp) "Memeriksa..." else connectedInfo.publicIp
                    )
                    InfoRow(
                        icon = Icons.Default.Public,
                        title = "Penyedia Jasa (ISP)",
                        value = if (isFetchingPublicIp) "Memeriksa..." else connectedInfo.ispName,
                        isLast = true
                    )

                    val context = LocalContext.current
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isValidRouterGateway) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, "http://${connectedInfo.gateway}".toUri())
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            enabled = isValidRouterGateway,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_router_admin_button")
                        ) {
                            Icon(imageVector = Icons.Default.Router, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isValidRouterGateway) "Admin Router (${connectedInfo.gateway})" else "Gateway Tidak Tersedia",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onRefreshPublicIp,
                            enabled = !isFetchingPublicIp,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("refresh_public_ip_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Perbarui IP Publik", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cek IP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ScanStatusBanner(wifiScanState: WifiScanState) {
    val bannerColor = when (wifiScanState.status) {
        WifiScanStatus.WIFI_DISABLED,
        WifiScanStatus.PERMISSION_DENIED,
        WifiScanStatus.LOCATION_DISABLED,
        WifiScanStatus.ERROR -> SignalOrange
        WifiScanStatus.THROTTLED_CACHE -> SignalYellow
        WifiScanStatus.EMPTY -> AccentCyan
        else -> AccentCyan
    }

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF172033)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_scan_status_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (wifiScanState.status == WifiScanStatus.EMPTY || wifiScanState.status == WifiScanStatus.SCANNING) {
                    Icons.Default.Info
                } else {
                    Icons.Default.Warning
                },
                contentDescription = null,
                tint = bannerColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = wifiScanState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun QuickStatBadge(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 12.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1E293B))
        )
    }
}
