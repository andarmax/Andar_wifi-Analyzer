package com.andar.wifianalyzer.model

enum class WifiBand(val displayName: String, val freqRange: String) {
    BAND_2_4_GHZ("2.4 GHz", "2412 - 2484 MHz"),
    BAND_5_GHZ("5 GHz", "5170 - 5885 MHz"),
    BAND_6_GHZ("6 GHz", "5925 - 7125 MHz"),
    UNKNOWN("Tidak diketahui", "-")
}

enum class WifiScanStatus {
    IDLE,
    SCANNING,
    SUCCESS,
    THROTTLED_CACHE,
    EMPTY,
    WIFI_DISABLED,
    LOCATION_DISABLED,
    PERMISSION_DENIED,
    ERROR
}

enum class PublicIpStatus {
    IDLE,
    LOADING,
    SUCCESS,
    OFFLINE,
    ERROR
}

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: WifiBand,
    val channelWidth: Int = 20,
    val security: String = "Tidak tersedia",
    val standard: String = "Tidak tersedia",
    val isConnected: Boolean = false,
    val distanceMeters: Double = 0.0,
    val signalPercent: Int = 0
)

data class WifiScanState(
    val status: WifiScanStatus = WifiScanStatus.IDLE,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val statusMessage: String = "Siap memindai jaringan WiFi sekitar",
    val isCachedResult: Boolean = false,
    val lastUpdatedMs: Long = 0L
)

data class ConnectedWifiInfo(
    val ssid: String = "Tidak terhubung",
    val bssid: String = "Tidak tersedia",
    val rssi: Int = -127,
    val signalPercent: Int = 0,
    val frequency: Int = 0,
    val channel: Int = 0,
    val band: WifiBand = WifiBand.UNKNOWN,
    val linkSpeedMbps: Int = 0,
    val rxLinkSpeedMbps: Int = 0,
    val txLinkSpeedMbps: Int = 0,
    val ipAddress: String = "Tidak tersedia",
    val gateway: String = "Tidak tersedia",
    val subnetMask: String = "Tidak tersedia",
    val prefixLength: Int = 0,
    val dns1: String = "Tidak tersedia",
    val dns2: String = "Tidak tersedia",
    val publicIp: String = "Belum diperiksa",
    val ispName: String = "Tidak tersedia",
    val publicIpStatus: PublicIpStatus = PublicIpStatus.IDLE,
    val wifiStandard: String = "Tidak tersedia",
    val securityType: String = "Tidak tersedia",
    val isConnected: Boolean = false,
    val isWifiEnabled: Boolean = true,
    val isLocationEnabled: Boolean = true,
    val hasPermission: Boolean = true,
    val statusMessage: String = "Memeriksa status WiFi..."
) {
    val security: String
        get() = securityType
}

data class ChannelRating(
    val channel: Int,
    val band: WifiBand,
    val frequency: Int = 0,
    val stars: Int = 5,
    val apCount: Int = 0,
    val coChannelApCount: Int = apCount,
    val adjacentApCount: Int = 0,
    val overlappingApCount: Int = coChannelApCount + adjacentApCount,
    val statusLabel: String = "",
    val isCurrentChannel: Boolean = false,
    val isRecommendedNonOverlapping: Boolean = false,
    val isNonOverlapping: Boolean = isRecommendedNonOverlapping,
    val interferenceScore: Double = 0.0
)

enum class SpeedTestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    FINISHED,
    CANCELLED,
    ERROR
}

enum class SpeedTestErrorType {
    NONE,
    OFFLINE,
    DNS_FAILURE,
    TIMEOUT,
    HTTP_ERROR,
    SERVER_UNAVAILABLE,
    UNKNOWN
}

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val progress: Float = 0.0f,
    val currentTransferMbps: Double = 0.0,
    val statusMessage: String = "Siap memulai tes kecepatan",
    val speedHistory: List<Float> = emptyList(),
    val errorMessage: String? = null,
    val errorType: SpeedTestErrorType = SpeedTestErrorType.NONE
)

enum class DiagnosticTaskStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    EMPTY,
    CANCELLED,
    ERROR
}

data class LanDevice(
    val ip: String,
    val mac: String = "Tidak tersedia",
    val hostname: String = "Tidak tersedia",
    val pingMs: Long = 0,
    val isGateway: Boolean = false,
    val isLocalDevice: Boolean = false,
    val deviceType: String = "Perangkat Jaringan",
    val vendor: String = "Tidak tersedia",
    val detectionMethod: String = "ICMP/TCP"
)

data class LanScanState(
    val status: DiagnosticTaskStatus = DiagnosticTaskStatus.IDLE,
    val devices: List<LanDevice> = emptyList(),
    val scannedCount: Int = 0,
    val totalHosts: Int = 0,
    val subnetCidr: String = "Tidak tersedia",
    val statusMessage: String = "Tekan 'Pindai LAN' untuk mendeteksi perangkat aktif di subnet lokal",
    val errorMessage: String? = null
) {
    val isScanning: Boolean
        get() = status == DiagnosticTaskStatus.RUNNING

    val progress: Float
        get() = if (totalHosts > 0) (scannedCount.toFloat() / totalHosts.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class PingStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class PingResult(
    val host: String = "8.8.8.8",
    val ip: String = "Tidak tersedia",
    val methodLabel: String = "TCP Latency",
    val sent: Int = 0,
    val received: Int = 0,
    val lossPercent: Int = 0,
    val minMs: Float = 0f,
    val avgMs: Float = 0f,
    val maxMs: Float = 0f,
    val logLines: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val status: PingStatus = PingStatus.IDLE,
    val errorMessage: String? = null
)

data class PortScanResult(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val latencyMs: Long? = null
)

data class PortScanState(
    val status: DiagnosticTaskStatus = DiagnosticTaskStatus.IDLE,
    val targetIp: String = "",
    val results: List<PortScanResult> = emptyList(),
    val statusMessage: String = "Hanya mengizinkan pemindaian pada localhost atau IP privat (RFC1918) milik jaringan sendiri",
    val errorMessage: String? = null
) {
    val isScanning: Boolean
        get() = status == DiagnosticTaskStatus.RUNNING
}

enum class DnsPingStatus {
    IDLE,
    TESTING,
    COMPLETED,
    TIMEOUT,
    UNREACHABLE,
    OFFLINE,
    FAILED
}

data class DnsServerPingItem(
    val id: String,
    val name: String,
    val primaryIp: String,
    val secondaryIp: String? = null,
    val description: String,
    val latencyMs: Float = 0f,
    val minMs: Float = 0f,
    val maxMs: Float = 0f,
    val packetLossPercent: Int = 0,
    val status: DnsPingStatus = DnsPingStatus.IDLE,
    val statusDetail: String = "Ketuk uji",
    val errorMessage: String? = null,
    val isFastest: Boolean = false
)
