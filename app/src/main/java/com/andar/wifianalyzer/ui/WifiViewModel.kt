package com.andar.wifianalyzer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andar.wifianalyzer.data.SpeedTestDao
import com.andar.wifianalyzer.data.SpeedTestDatabase
import com.andar.wifianalyzer.data.SpeedTestEntity
import com.andar.wifianalyzer.model.ChannelRating
import com.andar.wifianalyzer.model.ConnectedWifiInfo
import com.andar.wifianalyzer.model.DiagnosticTaskStatus
import com.andar.wifianalyzer.model.DnsPingStatus
import com.andar.wifianalyzer.model.DnsServerPingItem
import com.andar.wifianalyzer.model.LanDevice
import com.andar.wifianalyzer.model.LanScanState
import com.andar.wifianalyzer.model.PingResult
import com.andar.wifianalyzer.model.PingStatus
import com.andar.wifianalyzer.model.PortScanResult
import com.andar.wifianalyzer.model.PortScanState
import com.andar.wifianalyzer.model.PublicIpStatus
import com.andar.wifianalyzer.model.SpeedTestState
import com.andar.wifianalyzer.model.WifiAccessPoint
import com.andar.wifianalyzer.model.WifiBand
import com.andar.wifianalyzer.model.WifiScanState
import com.andar.wifianalyzer.model.WifiScanStatus
import com.andar.wifianalyzer.service.NetworkDiagnosticsEngine
import com.andar.wifianalyzer.service.NetworkValidator
import com.andar.wifianalyzer.service.SpeedTestEngine
import com.andar.wifianalyzer.service.WifiScannerEngine
import kotlinx.coroutines.CancellationException
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

class WifiViewModel(
    application: Application,
    private val customDao: SpeedTestDao? = null,
    private val customScannerEngine: WifiScannerEngine? = null,
    private val customSpeedEngine: SpeedTestEngine? = null,
    private val customDiagnosticsEngine: NetworkDiagnosticsEngine? = null,
    autoStartMonitoring: Boolean = true
) : AndroidViewModel(application) {

    // Default single-argument constructor required by ViewModelProvider.AndroidViewModelFactory
    constructor(application: Application) : this(
        application = application,
        customDao = null,
        customScannerEngine = null,
        customSpeedEngine = null,
        customDiagnosticsEngine = null,
        autoStartMonitoring = true
    )

    private val speedDao: SpeedTestDao = customDao ?: SpeedTestDatabase.getDatabase(application).speedTestDao()
    private val scannerEngine: WifiScannerEngine = customScannerEngine ?: WifiScannerEngine(application)
    private val speedEngine: SpeedTestEngine = customSpeedEngine ?: SpeedTestEngine(speedDao)
    private val diagnosticsEngine: NetworkDiagnosticsEngine = customDiagnosticsEngine ?: NetworkDiagnosticsEngine()

    // Navigation
    private val _currentTab = MutableStateFlow(MainNavTab.DASHBOARD)
    val currentTab: StateFlow<MainNavTab> = _currentTab.asStateFlow()

    // Connected WiFi Info
    private val _connectedInfo = MutableStateFlow(ConnectedWifiInfo())
    val connectedInfo: StateFlow<ConnectedWifiInfo> = _connectedInfo.asStateFlow()

    // WiFi Scan State & Access Points
    private val _wifiScanState = MutableStateFlow(WifiScanState())
    val wifiScanState: StateFlow<WifiScanState> = _wifiScanState.asStateFlow()

    private val _accessPoints = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    val accessPoints: StateFlow<List<WifiAccessPoint>> = _accessPoints.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Channel Analyzer Band
    private val _selectedBand = MutableStateFlow(WifiBand.BAND_2_4_GHZ)
    val selectedBand: StateFlow<WifiBand> = _selectedBand.asStateFlow()

    // Channel Ratings & Best Recommendation
    private val _channelRatings = MutableStateFlow<List<ChannelRating>>(emptyList())
    val channelRatings: StateFlow<List<ChannelRating>> = _channelRatings.asStateFlow()

    private val _bestChannel = MutableStateFlow<ChannelRating?>(null)
    val bestChannel: StateFlow<ChannelRating?> = _bestChannel.asStateFlow()

    // Real-time Signal History (only real connected RSSI readings, never pre-populated with fake values)
    private val _signalHistory = MutableStateFlow<List<Int>>(emptyList())
    val signalHistory: StateFlow<List<Int>> = _signalHistory.asStateFlow()

    // Speed Test
    val speedTestState: StateFlow<SpeedTestState> = speedEngine.state

    // Speed Test History from Room
    val speedHistory: StateFlow<List<SpeedTestEntity>> = speedDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // LAN Scanner State
    private val _lanScanState = MutableStateFlow(LanScanState())
    val lanScanState: StateFlow<LanScanState> = _lanScanState.asStateFlow()

    private val _lanDevices = MutableStateFlow<List<LanDevice>>(emptyList())
    val lanDevices: StateFlow<List<LanDevice>> = _lanDevices.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    // Ping Tool
    private val _pingResult = MutableStateFlow(PingResult())
    val pingResult: StateFlow<PingResult> = _pingResult.asStateFlow()

    // Port Scanner State
    private val _portScanState = MutableStateFlow(PortScanState())
    val portScanState: StateFlow<PortScanState> = _portScanState.asStateFlow()

    private val _portScanResults = MutableStateFlow<List<PortScanResult>>(emptyList())
    val portScanResults: StateFlow<List<PortScanResult>> = _portScanResults.asStateFlow()

    private val _isScanningPorts = MutableStateFlow(false)
    val isScanningPorts: StateFlow<Boolean> = _isScanningPorts.asStateFlow()

    // DNS Ping Benchmark
    private val _dnsServerList = MutableStateFlow<List<DnsServerPingItem>>(emptyList())
    val dnsServerList: StateFlow<List<DnsServerPingItem>> = _dnsServerList.asStateFlow()

    private val _isTestingDns = MutableStateFlow(false)
    val isTestingDns: StateFlow<Boolean> = _isTestingDns.asStateFlow()

    private val _isFetchingPublicIp = MutableStateFlow(false)
    val isFetchingPublicIp: StateFlow<Boolean> = _isFetchingPublicIp.asStateFlow()

    private var refreshJob: Job? = null
    private var signalMonitorJob: Job? = null
    private var publicIpJob: Job? = null
    private var speedTestJob: Job? = null
    private var lanScanJob: Job? = null
    private var pingJob: Job? = null
    private var portScanJob: Job? = null
    private var dnsJob: Job? = null

    init {
        _dnsServerList.value = diagnosticsEngine.getDefaultDnsList(_connectedInfo.value.gateway)
        if (autoStartMonitoring) {
            refreshAll(forcePublicIpRefresh = true)
            startSignalMonitoring()
        }
    }

    fun getRequiredPermissions(): List<String> = scannerEngine.getRequiredPermissions()

    fun hasRequiredPermissions(): Boolean = scannerEngine.hasRequiredPermissions()

    fun selectTab(tab: MainNavTab) {
        _currentTab.value = tab
    }

    fun setSelectedBand(band: WifiBand) {
        _selectedBand.value = band
        updateRatings()
    }

    fun getBestChannelRecommendation(ratings: List<ChannelRating>): ChannelRating? {
        return scannerEngine.selectBestRecommendedChannel(ratings)
    }

    fun refreshAll(forcePublicIpRefresh: Boolean = false, includePublicIp: Boolean = forcePublicIpRefresh) {
        if (refreshJob?.isActive == true) return
        val shouldRefreshPublicIp = forcePublicIpRefresh || includePublicIp

        refreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val previous = _connectedInfo.value
                val freshInfo = scannerEngine.getConnectedWifiInfo()

                // Preserve valid Public IP & ISP across WiFi refreshes if still connected to the same SSID
                val preservedPublicIp = if (
                    freshInfo.isConnected &&
                    previous.publicIpStatus == PublicIpStatus.SUCCESS &&
                    previous.ssid == freshInfo.ssid &&
                    !shouldRefreshPublicIp
                ) {
                    previous.publicIp
                } else if (!freshInfo.isConnected) {
                    "Tidak tersedia (Offline)"
                } else {
                    previous.publicIp.takeIf { previous.publicIpStatus == PublicIpStatus.SUCCESS } ?: "Memeriksa..."
                }

                val preservedIsp = if (
                    freshInfo.isConnected &&
                    previous.publicIpStatus == PublicIpStatus.SUCCESS &&
                    previous.ssid == freshInfo.ssid
                ) {
                    previous.ispName
                } else if (!freshInfo.isConnected) {
                    "Tidak tersedia"
                } else {
                    previous.ispName
                }

                val preservedStatus = if (!freshInfo.isConnected) {
                    PublicIpStatus.OFFLINE
                } else {
                    previous.publicIpStatus
                }

                _connectedInfo.value = freshInfo.copy(
                    publicIp = preservedPublicIp,
                    ispName = preservedIsp,
                    publicIpStatus = preservedStatus
                )

                if (freshInfo.isConnected && freshInfo.rssi in -126..-1) {
                    appendSignalSample(freshInfo.rssi)
                }

                // Synchronize gateway DNS item in DNS benchmark list
                syncGatewayInDnsList(freshInfo.gateway)

                if (freshInfo.isConnected &&
                    (shouldRefreshPublicIp || previous.publicIpStatus != PublicIpStatus.SUCCESS || previous.ssid != freshInfo.ssid)
                ) {
                    fetchPublicIp()
                }

                _wifiScanState.value = _wifiScanState.value.copy(
                    status = WifiScanStatus.SCANNING,
                    statusMessage = "Memindai spektrum saluran WiFi..."
                )

                val scanState = scannerEngine.scanAccessPointsWithState()
                _wifiScanState.value = scanState
                _accessPoints.value = scanState.accessPoints

                updateRatings()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun syncGatewayInDnsList(gatewayIp: String) {
        val current = _dnsServerList.value
        if (current.isEmpty()) {
            _dnsServerList.value = diagnosticsEngine.getDefaultDnsList(gatewayIp)
            return
        }

        if (NetworkValidator.isValidIpv4(gatewayIp)) {
            val routerIndex = current.indexOfFirst { it.id == "router" }
            val updated = current.toMutableList()
            if (routerIndex >= 0) {
                if (updated[routerIndex].primaryIp != gatewayIp) {
                    updated[routerIndex] = updated[routerIndex].copy(
                        primaryIp = gatewayIp,
                        status = DnsPingStatus.IDLE,
                        latencyMs = 0f,
                        statusDetail = "Ketuk uji"
                    )
                }
            } else {
                updated.add(
                    minOf(2, updated.size),
                    DnsServerPingItem(
                        id = "router",
                        name = "Gateway / Router Lokal",
                        primaryIp = gatewayIp,
                        description = "DNS forwarder bawaan router WiFi lokal"
                    )
                )
            }
            _dnsServerList.value = updated
        } else {
            _dnsServerList.value = current.filterNot { it.id == "router" }
        }
    }

    private fun updateRatings() {
        val ratings = scannerEngine.calculateChannelRatings(
            band = _selectedBand.value,
            accessPoints = _accessPoints.value,
            currentChannel = _connectedInfo.value.channel
        )
        _channelRatings.value = ratings
        _bestChannel.value = scannerEngine.selectBestRecommendedChannel(ratings)
    }

    private fun appendSignalSample(rssi: Int) {
        if (rssi !in -126..-1) return
        val history = _signalHistory.value.toMutableList()
        history.add(rssi)
        if (history.size > 30) history.removeAt(0)
        _signalHistory.value = history
    }

    private fun startSignalMonitoring() {
        signalMonitorJob?.cancel()
        signalMonitorJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                val current = scannerEngine.getConnectedWifiInfo()
                val prev = _connectedInfo.value
                _connectedInfo.value = current.copy(
                    publicIp = prev.publicIp,
                    ispName = prev.ispName,
                    publicIpStatus = prev.publicIpStatus
                )

                if (current.isConnected && current.rssi in -126..-1) {
                    appendSignalSample(current.rssi)
                }
            }
        }
    }

    fun fetchPublicIpAndIsp() = fetchPublicIp()

    fun fetchPublicIp() {
        if (publicIpJob?.isActive == true) return
        publicIpJob = viewModelScope.launch {
            _isFetchingPublicIp.value = true
            try {
                _connectedInfo.value = _connectedInfo.value.copy(
                    publicIp = "Memeriksa...",
                    publicIpStatus = PublicIpStatus.LOADING
                )
                val result = diagnosticsEngine.fetchPublicIpAndIsp()
                _connectedInfo.value = _connectedInfo.value.copy(
                    publicIp = result.ip,
                    ispName = result.isp,
                    publicIpStatus = result.status
                )
            } finally {
                _isFetchingPublicIp.value = false
            }
        }
    }

    fun startSpeedTest() {
        if (speedTestJob?.isActive == true) return
        speedTestJob = viewModelScope.launch {
            speedEngine.startSpeedTest(_connectedInfo.value.ssid)
        }
    }

    fun cancelSpeedTest() {
        if (speedTestJob?.isActive == true) {
            speedTestJob?.cancel()
            speedTestJob = null
            speedEngine.markCancelled()
        }
    }

    fun resetSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
        speedEngine.reset()
    }

    fun clearSpeedHistory() {
        viewModelScope.launch {
            speedDao.clearAll()
        }
    }

    fun startLanScan() {
        if (lanScanJob?.isActive == true) return
        lanScanJob = viewModelScope.launch {
            _isScanningLan.value = true
            try {
                val info = _connectedInfo.value
                diagnosticsEngine.scanLocalSubnetFlow(
                    localIp = info.ipAddress,
                    prefixLength = info.prefixLength,
                    gatewayIp = info.gateway
                ).collect { state ->
                    _lanScanState.value = state
                    _lanDevices.value = state.devices
                }
            } catch (_: CancellationException) {
                _lanScanState.value = _lanScanState.value.copy(
                    status = DiagnosticTaskStatus.CANCELLED,
                    statusMessage = "Pemindaian LAN dibatalkan oleh pengguna."
                )
            } finally {
                _isScanningLan.value = false
            }
        }
    }

    fun cancelLanScan() {
        if (lanScanJob?.isActive == true) {
            lanScanJob?.cancel()
            lanScanJob = null
            _isScanningLan.value = false
            _lanScanState.value = _lanScanState.value.copy(
                status = DiagnosticTaskStatus.CANCELLED,
                statusMessage = "Pemindaian LAN dihentikan."
            )
        }
    }

    fun runPing(host: String) {
        if (pingJob?.isActive == true) return
        pingJob = viewModelScope.launch {
            try {
                diagnosticsEngine.executePing(host).collect {
                    _pingResult.value = it
                }
            } catch (_: CancellationException) {
                _pingResult.value = _pingResult.value.copy(
                    isRunning = false,
                    status = PingStatus.CANCELLED,
                    logLines = _pingResult.value.logLines + "Pengujian ping dihentikan oleh pengguna."
                )
            }
        }
    }

    fun cancelPing() {
        if (pingJob?.isActive == true) {
            pingJob?.cancel()
            pingJob = null
            _pingResult.value = _pingResult.value.copy(
                isRunning = false,
                status = PingStatus.CANCELLED,
                logLines = _pingResult.value.logLines + "Pengujian ping dihentikan."
            )
        }
    }

    fun scanPorts(targetIp: String) {
        if (portScanJob?.isActive == true) return
        portScanJob = viewModelScope.launch {
            _isScanningPorts.value = true
            _portScanState.value = PortScanState(
                status = DiagnosticTaskStatus.RUNNING,
                targetIp = targetIp.trim(),
                results = emptyList(),
                statusMessage = "Memeriksa port TCP pada ${targetIp.trim()}..."
            )
            try {
                val info = _connectedInfo.value
                val state = diagnosticsEngine.scanPortsWithValidation(
                    rawTargetIp = targetIp,
                    localIp = info.ipAddress,
                    prefixLength = info.prefixLength
                )
                _portScanState.value = state
                _portScanResults.value = state.results
            } catch (_: CancellationException) {
                _portScanState.value = _portScanState.value.copy(
                    status = DiagnosticTaskStatus.CANCELLED,
                    statusMessage = "Pemindaian port dibatalkan."
                )
            } finally {
                _isScanningPorts.value = false
            }
        }
    }

    fun cancelPortScan() {
        if (portScanJob?.isActive == true) {
            portScanJob?.cancel()
            portScanJob = null
            _isScanningPorts.value = false
            _portScanState.value = _portScanState.value.copy(
                status = DiagnosticTaskStatus.CANCELLED,
                statusMessage = "Pemindaian port dihentikan."
            )
        }
    }

    fun startDnsBenchmark() {
        if (dnsJob?.isActive == true) return
        dnsJob = viewModelScope.launch {
            _isTestingDns.value = true
            try {
                val currentServers = if (_dnsServerList.value.isEmpty()) {
                    diagnosticsEngine.getDefaultDnsList(_connectedInfo.value.gateway)
                } else {
                    _dnsServerList.value
                }
                diagnosticsEngine.benchmarkDnsList(currentServers).collect { updatedList ->
                    _dnsServerList.value = updatedList
                }
            } catch (_: CancellationException) {
                _dnsServerList.value = _dnsServerList.value.map {
                    if (it.status == DnsPingStatus.TESTING) {
                        it.copy(status = DnsPingStatus.IDLE, statusDetail = "Dibatalkan")
                    } else it
                }
            } finally {
                _isTestingDns.value = false
            }
        }
    }

    fun cancelDnsBenchmark() {
        if (dnsJob?.isActive == true) {
            dnsJob?.cancel()
            dnsJob = null
            _isTestingDns.value = false
        }
    }

    fun pingSingleDns(server: DnsServerPingItem) {
        if (_isTestingDns.value) return
        val existingItem = _dnsServerList.value.firstOrNull { it.id == server.id }
        if (existingItem?.status == DnsPingStatus.TESTING) return

        viewModelScope.launch {
            val list = _dnsServerList.value.toMutableList()
            val index = list.indexOfFirst { it.id == server.id }
            if (index != -1) {
                list[index] = list[index].copy(
                    status = DnsPingStatus.TESTING,
                    statusDetail = "Menguji TCP/53..."
                )
                _dnsServerList.value = list

                val updated = diagnosticsEngine.pingDnsServer(server)
                val newList = _dnsServerList.value.toMutableList()
                val newIndex = newList.indexOfFirst { it.id == server.id }
                if (newIndex != -1) {
                    newList[newIndex] = updated

                    val valid = newList.filter { it.status == DnsPingStatus.COMPLETED && it.latencyMs > 0f }
                    val minLat = valid.minOfOrNull { it.latencyMs }
                    _dnsServerList.value = newList.map {
                        it.copy(
                            isFastest = (minLat != null && it.status == DnsPingStatus.COMPLETED && it.latencyMs == minLat)
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        signalMonitorJob?.cancel()
        publicIpJob?.cancel()
        speedTestJob?.cancel()
        lanScanJob?.cancel()
        pingJob?.cancel()
        portScanJob?.cancel()
        dnsJob?.cancel()
    }
}
