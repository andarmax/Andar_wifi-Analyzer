package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainNavTab
import com.example.ui.WifiViewModel
import com.example.ui.screens.ChannelAnalyzerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.LanScannerScreen
import com.example.ui.screens.SignalRadarScreen
import com.example.ui.screens.SpeedTestScreen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CyberBluePrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SignalGreen

class MainActivity : ComponentActivity() {

    private val viewModel: WifiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: WifiViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val connectedInfo by viewModel.connectedInfo.collectAsStateWithLifecycle()
    val accessPoints by viewModel.accessPoints.collectAsStateWithLifecycle()
    val selectedBand by viewModel.selectedBand.collectAsStateWithLifecycle()
    val channelRatings by viewModel.channelRatings.collectAsStateWithLifecycle()
    val signalHistory by viewModel.signalHistory.collectAsStateWithLifecycle()
    val speedState by viewModel.speedTestState.collectAsStateWithLifecycle()
    val speedHistoryList by viewModel.speedHistory.collectAsStateWithLifecycle()
    val lanDevices by viewModel.lanDevices.collectAsStateWithLifecycle()
    val isScanningLan by viewModel.isScanningLan.collectAsStateWithLifecycle()
    val pingResult by viewModel.pingResult.collectAsStateWithLifecycle()
    val portScanResults by viewModel.portScanResults.collectAsStateWithLifecycle()
    val isScanningPorts by viewModel.isScanningPorts.collectAsStateWithLifecycle()
    val dnsServerList by viewModel.dnsServerList.collectAsStateWithLifecycle()
    val isTestingDns by viewModel.isTestingDns.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Request permissions for WiFi scanning
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val anyDenied = results.values.any { !it }
        if (anyDenied) {
            showPermissionRationale = true
        }
        viewModel.refreshAll()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Izin Lokasi & WiFi Diperlukan") },
            text = {
                Text(
                    "Sistem Android mewajibkan izin Lokasi dan Perangkat Sekitar agar aplikasi dapat memindai frekuensi saluran WiFi dan kekuatan sinyal dBm.\n\nAplikasi ini tidak menyimpan atau mengunggah data lokasi Anda ke server mana pun."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Buka Pengaturan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Nanti Saja")
                }
            }
        )
    }

    // Handle Android system back button when not on dashboard
    BackHandler(enabled = currentTab != MainNavTab.DASHBOARD) {
        viewModel.selectTab(MainNavTab.DASHBOARD)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0B111D),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.andar_logo_icon_1791651458189),
                            contentDescription = "Logo Andar",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Andar WiFi Analyzer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(SignalGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${connectedInfo.ssid} (${connectedInfo.rssi} dBm)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                ),
                modifier = Modifier.testTag("app_top_bar")
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                val navItems = listOf(
                    Triple(MainNavTab.DASHBOARD, Icons.Default.Wifi, "Ikhtisar"),
                    Triple(MainNavTab.RADAR, Icons.Default.Radar, "Radar"),
                    Triple(MainNavTab.CHANNELS, Icons.Default.Tune, "Saluran"),
                    Triple(MainNavTab.SPEED_TEST, Icons.Default.Speed, "Kecepatan"),
                    Triple(MainNavTab.TOOLS, Icons.Default.Build, "Diagnostik")
                )

                navItems.forEach { (tab, icon, label) ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentCyan,
                            selectedTextColor = AccentCyan,
                            indicatorColor = Color(0xFF1E2D4A),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainNavTab.DASHBOARD -> DashboardScreen(
                    connectedInfo = connectedInfo,
                    signalHistory = signalHistory,
                    onRefresh = { viewModel.refreshAll() },
                    onNavigateTab = { viewModel.selectTab(it) }
                )
                MainNavTab.RADAR -> SignalRadarScreen(
                    accessPoints = accessPoints,
                    connectedInfo = connectedInfo,
                    onRefresh = { viewModel.refreshAll() }
                )
                MainNavTab.CHANNELS -> ChannelAnalyzerScreen(
                    selectedBand = selectedBand,
                    accessPoints = accessPoints,
                    channelRatings = channelRatings,
                    currentChannel = connectedInfo.channel,
                    onSelectBand = { viewModel.setSelectedBand(it) },
                    onRefresh = { viewModel.refreshAll() }
                )
                MainNavTab.SPEED_TEST -> SpeedTestScreen(
                    speedState = speedState,
                    historyList = speedHistoryList,
                    onStartTest = { viewModel.startSpeedTest() },
                    onResetTest = { viewModel.resetSpeedTest() },
                    onClearHistory = { viewModel.clearSpeedHistory() }
                )
                MainNavTab.TOOLS -> DiagnosticsScreen(
                    pingResult = pingResult,
                    signalHistory = signalHistory,
                    portScanResults = portScanResults,
                    isScanningPorts = isScanningPorts,
                    gatewayIp = connectedInfo.gateway,
                    dnsServerList = dnsServerList,
                    isTestingDns = isTestingDns,
                    lanDevices = lanDevices,
                    isScanningLan = isScanningLan,
                    onRunPing = { viewModel.runPing(it) },
                    onScanPorts = { viewModel.scanPorts(it) },
                    onStartDnsBenchmark = { viewModel.startDnsBenchmark() },
                    onPingSingleDns = { viewModel.pingSingleDns(it) },
                    onStartLanScan = { viewModel.startLanScan() }
                )
            }
        }
    }
}
