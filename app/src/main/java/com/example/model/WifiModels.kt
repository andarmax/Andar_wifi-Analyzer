package com.example.model

enum class WifiBand(val displayName: String, val freqRange: String) {
    BAND_2_4_GHZ("2.4 GHz", "2412 - 2484 MHz"),
    BAND_5_GHZ("5 GHz", "5170 - 5825 MHz"),
    BAND_6_GHZ("6 GHz", "5925 - 7125 MHz")
}

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int, // dBm (e.g. -50)
    val frequency: Int, // MHz
    val channel: Int,
    val band: WifiBand,
    val channelWidth: Int = 20, // MHz
    val security: String = "WPA2-PSK",
    val isConnected: Boolean = false,
    val distanceMeters: Double = 3.5,
    val signalPercent: Int = 75
)

data class ConnectedWifiInfo(
    val ssid: String = "WiFi Jaringan",
    val bssid: String = "AA:BB:CC:DD:EE:FF",
    val rssi: Int = -55,
    val signalPercent: Int = 85,
    val frequency: Int = 5180,
    val channel: Int = 36,
    val band: WifiBand = WifiBand.BAND_5_GHZ,
    val linkSpeedMbps: Int = 433,
    val rxLinkSpeedMbps: Int = 433,
    val txLinkSpeedMbps: Int = 300,
    val ipAddress: String = "192.168.1.105",
    val gateway: String = "192.168.1.1",
    val subnetMask: String = "255.255.255.0",
    val dns1: String = "8.8.8.8",
    val dns2: String = "1.1.1.1",
    val publicIp: String = "182.253.112.44",
    val ispName: String = "Telkom Indonesia / Indihome",
    val wifiStandard: String = "Wi-Fi 5 (802.11ac)",
    val securityType: String = "WPA2-Personal",
    val isConnected: Boolean = true
)

data class ChannelRating(
    val channel: Int,
    val band: WifiBand,
    val frequency: Int,
    val stars: Int, // 1 to 5
    val apCount: Int,
    val coChannelApCount: Int,
    val adjacentApCount: Int,
    val statusLabel: String, // "Sangat Direkomendasikan", "Bagus", "Cukup", "Padat"
    val isCurrentChannel: Boolean = false
)

enum class SpeedTestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    FINISHED,
    ERROR
}

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val progress: Float = 0.0f,
    val currentTransferMbps: Double = 0.0,
    val statusMessage: String = "Siap memulai tes",
    val speedHistory: List<Float> = emptyList(),
    val errorMessage: String? = null
)

data class LanDevice(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val pingMs: Long = 0,
    val isGateway: Boolean = false,
    val isLocalDevice: Boolean = false,
    val deviceType: String = "Perangkat",
    val vendor: String? = null
)

data class PingResult(
    val host: String = "8.8.8.8",
    val ip: String = "8.8.8.8",
    val sent: Int = 0,
    val received: Int = 0,
    val lossPercent: Int = 0,
    val minMs: Float = 0f,
    val avgMs: Float = 0f,
    val maxMs: Float = 0f,
    val logLines: List<String> = emptyList(),
    val isRunning: Boolean = false
)

data class PortScanResult(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean
)

enum class DnsPingStatus {
    IDLE,
    TESTING,
    COMPLETED,
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
    val isFastest: Boolean = false
)
