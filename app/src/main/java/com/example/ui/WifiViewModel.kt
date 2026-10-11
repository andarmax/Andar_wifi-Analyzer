package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SpeedTestDatabase
import com.example.data.SpeedTestEntity
import com.example.model.ChannelRating
import com.example.model.ConnectedWifiInfo
import com.example.model.DnsPingStatus
import com.example.model.DnsServerPingItem
import com.example.model.LanDevice
import com.example.model.PingResult
import com.example.model.PortScanResult
import com.example.model.SpeedTestState
import com.example.model.WifiAccessPoint
import com.example.model.WifiBand
import com.example.service.NetworkDiagnosticsEngine
import com.example.service.SpeedTestEngine
import com.example.service.WifiScannerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MainNavTab(val title: String) {
    DASHBOARD("Ikhtisar"),
    RADAR("Radar"),
    CHANNELS("Saluran"),
    SPEED_TEST("Kecepatan"),
    TOOLS("Diagnostik")
}

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SpeedTestDatabase.getDatabase(application)
    private val speedDao = db.speedTestDao()

    private val scannerEngine = WifiScannerEngine(application)
    private val speedEngine = SpeedTestEngine(speedDao)
    private val diagnosticsEngine = NetworkDiagnosticsEngine()

    // Navigation
    private val _currentTab = MutableStateFlow(MainNavTab.DASHBOARD)
    val currentTab: StateFlow<MainNavTab> = _currentTab.asStateFlow()

    // Connected WiFi Info
    private val _connectedInfo = MutableStateFlow(ConnectedWifiInfo())
    val connectedInfo: StateFlow<ConnectedWifiInfo> = _connectedInfo.asStateFlow()

    // Access Points
    private val _accessPoints = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    val accessPoints: StateFlow<List<WifiAccessPoint>> = _accessPoints.asStateFlow()

    // Channel Analyzer Band
    private val _selectedBand = MutableStateFlow(WifiBand.BAND_2_4_GHZ)
    val selectedBand: StateFlow<WifiBand> = _selectedBand.asStateFlow()

    // Channel Ratings
    private val _channelRatings = MutableStateFlow<List<ChannelRating>>(emptyList())
    val channelRatings: StateFlow<List<ChannelRating>> = _channelRatings.asStateFlow()

    // Signal History (dBm over time for graph)
    private val _signalHistory = MutableStateFlow<List<Int>>(listOf(-55, -54, -56, -55, -53, -55))
    val signalHistory: StateFlow<List<Int>> = _signalHistory.asStateFlow()

    // Speed Test
    val speedTestState: StateFlow<SpeedTestState> = speedEngine.state

    // Speed Test History from Room
    val speedHistory: StateFlow<List<SpeedTestEntity>> = speedDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // LAN Scanner
    private val _lanDevices = MutableStateFlow<List<LanDevice>>(emptyList())
    val lanDevices: StateFlow<List<LanDevice>> = _lanDevices.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    // Ping Tool
    private val _pingResult = MutableStateFlow(PingResult())
    val pingResult: StateFlow<PingResult> = _pingResult.asStateFlow()

    // Port Scanner
    private val _portScanResults = MutableStateFlow<List<PortScanResult>>(emptyList())
    val portScanResults: StateFlow<List<PortScanResult>> = _portScanResults.asStateFlow()

    private val _isScanningPorts = MutableStateFlow(false)
    val isScanningPorts: StateFlow<Boolean> = _isScanningPorts.asStateFlow()

    // DNS Ping Benchmark
    private val _dnsServerList = MutableStateFlow<List<DnsServerPingItem>>(emptyList())
    val dnsServerList: StateFlow<List<DnsServerPingItem>> = _dnsServerList.asStateFlow()

    private val _isTestingDns = MutableStateFlow(false)
    val isTestingDns: StateFlow<Boolean> = _isTestingDns.asStateFlow()

    private var signalMonitorJob: Job? = null
    private var pingJob: Job? = null
    private var dnsJob: Job? = null

    init {
        _dnsServerList.value = diagnosticsEngine.getDefaultDnsList(_connectedInfo.value.gateway)
        refreshAll()
        startSignalMonitoring()
        fetchPublicIp()
    }

    fun selectTab(tab: MainNavTab) {
        _currentTab.value = tab
    }

    fun setSelectedBand(band: WifiBand) {
        _selectedBand.value = band
        updateRatings()
    }

    fun refreshAll() {
        viewModelScope.launch {
            val info = scannerEngine.getConnectedWifiInfo()
            _connectedInfo.value = info

            val aps = scannerEngine.scanAccessPoints()
            _accessPoints.value = aps

            updateRatings()
        }
    }

    private fun updateRatings() {
        val ratings = scannerEngine.calculateChannelRatings(
            band = _selectedBand.value,
            accessPoints = _accessPoints.value,
            currentChannel = _connectedInfo.value.channel
        )
        _channelRatings.value = ratings
    }

    private fun startSignalMonitoring() {
        signalMonitorJob?.cancel()
        signalMonitorJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                val current = scannerEngine.getConnectedWifiInfo()
                _connectedInfo.value = _connectedInfo.value.copy(
                    rssi = current.rssi,
                    signalPercent = current.signalPercent,
                    linkSpeedMbps = current.linkSpeedMbps
                )

                val history = _signalHistory.value.toMutableList()
                history.add(current.rssi)
                if (history.size > 25) history.removeAt(0)
                _signalHistory.value = history
            }
        }
    }

    private fun fetchPublicIp() {
        viewModelScope.launch {
            val (ip, isp) = diagnosticsEngine.fetchPublicIpAndIsp()
            _connectedInfo.value = _connectedInfo.value.copy(
                publicIp = ip,
                ispName = isp
            )
        }
    }

    fun startSpeedTest() {
        viewModelScope.launch {
            speedEngine.startSpeedTest(_connectedInfo.value.ssid)
        }
    }

    fun resetSpeedTest() {
        speedEngine.reset()
    }

    fun clearSpeedHistory() {
        viewModelScope.launch {
            speedDao.clearAll()
        }
    }

    fun startLanScan() {
        viewModelScope.launch {
            _isScanningLan.value = true
            val devices = diagnosticsEngine.scanLocalSubnet(
                localIp = _connectedInfo.value.ipAddress,
                gatewayIp = _connectedInfo.value.gateway
            )
            _lanDevices.value = devices
            _isScanningLan.value = false
        }
    }

    fun runPing(host: String) {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            diagnosticsEngine.executePing(host).collect {
                _pingResult.value = it
            }
        }
    }

    fun scanPorts(targetIp: String) {
        viewModelScope.launch {
            _isScanningPorts.value = true
            val results = diagnosticsEngine.scanPorts(targetIp)
            _portScanResults.value = results
            _isScanningPorts.value = false
        }
    }

    fun startDnsBenchmark() {
        dnsJob?.cancel()
        dnsJob = viewModelScope.launch {
            _isTestingDns.value = true
            val currentServers = if (_dnsServerList.value.isEmpty()) {
                diagnosticsEngine.getDefaultDnsList(_connectedInfo.value.gateway)
            } else {
                _dnsServerList.value
            }
            diagnosticsEngine.benchmarkDnsList(currentServers).collect { updatedList ->
                _dnsServerList.value = updatedList
            }
            _isTestingDns.value = false
        }
    }

    fun pingSingleDns(server: DnsServerPingItem) {
        viewModelScope.launch {
            val list = _dnsServerList.value.toMutableList()
            val index = list.indexOfFirst { it.id == server.id }
            if (index != -1) {
                list[index] = list[index].copy(status = DnsPingStatus.TESTING)
                _dnsServerList.value = list

                val updated = diagnosticsEngine.pingDnsServer(server)
                val newList = _dnsServerList.value.toMutableList()
                val newIndex = newList.indexOfFirst { it.id == server.id }
                if (newIndex != -1) {
                    newList[newIndex] = updated

                    // Re-evaluate fastest if completed
                    val valid = newList.filter { it.status == DnsPingStatus.COMPLETED && it.latencyMs > 0 }
                    val minLat = valid.minOfOrNull { it.latencyMs }
                    _dnsServerList.value = newList.map {
                        it.copy(isFastest = (minLat != null && it.latencyMs == minLat))
                    }
                }
            }
        }
    }
}
