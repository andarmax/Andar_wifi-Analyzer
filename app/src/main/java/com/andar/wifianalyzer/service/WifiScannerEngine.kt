package com.andar.wifianalyzer.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.andar.wifianalyzer.model.ChannelRating
import com.andar.wifianalyzer.model.ConnectedWifiInfo
import com.andar.wifianalyzer.model.WifiAccessPoint
import com.andar.wifianalyzer.model.WifiBand
import com.andar.wifianalyzer.model.WifiScanState
import com.andar.wifianalyzer.model.WifiScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class ExtractedNetworkConfig(
    val ipAddress: String = "Tidak tersedia",
    val prefixLength: Int = 0,
    val subnetMask: String = "Tidak tersedia",
    val gateway: String = "Tidak tersedia",
    val dns1: String = "Tidak tersedia",
    val dns2: String = "Tidak tersedia"
)

open class WifiScannerEngine(private val context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun getRequiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return perms
    }

    fun hasRequiredPermissions(): Boolean = hasRequiredWifiPermissions()

    fun hasRequiredWifiPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearbyWifi = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            (hasFineLocation || hasCoarseLocation) && hasNearbyWifi
        } else {
            hasFineLocation || hasCoarseLocation
        }
    }

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    fun isLocationServiceEnabled(): Boolean {
        val lm = locationManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Exception) {
            false
        }
    }

    open fun getConnectedWifiInfo(): ConnectedWifiInfo {
        val wifiEnabled = isWifiEnabled()
        val locationEnabled = isLocationServiceEnabled()
        val permissionGranted = hasRequiredWifiPermissions()

        val activeNetwork = connectivityManager?.activeNetwork
        val linkProps = activeNetwork?.let { connectivityManager?.getLinkProperties(it) }
        val netCaps = activeNetwork?.let { connectivityManager?.getNetworkCapabilities(it) }

        val transportWifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            netCaps?.transportInfo as? WifiInfo
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val info: WifiInfo? = transportWifiInfo ?: try {
            wifiManager?.connectionInfo
        } catch (_: SecurityException) {
            null
        }

        val isWifiConnected = netCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            (info != null && info.networkId != -1 && info.rssi > -127)

        val netConfig = extractNetworkParams(linkProps, info)

        if (!wifiEnabled) {
            return ConnectedWifiInfo(
                ssid = "WiFi Nonaktif",
                isConnected = false,
                isWifiEnabled = false,
                isLocationEnabled = locationEnabled,
                hasPermission = permissionGranted,
                statusMessage = "WiFi perangkat sedang dimatikan. Aktifkan WiFi untuk menganalisis jaringan."
            )
        }

        if (!isWifiConnected) {
            return ConnectedWifiInfo(
                ssid = "Tidak terhubung ke WiFi",
                ipAddress = netConfig.ipAddress,
                gateway = netConfig.gateway,
                subnetMask = netConfig.subnetMask,
                prefixLength = netConfig.prefixLength,
                dns1 = netConfig.dns1,
                dns2 = netConfig.dns2,
                isConnected = false,
                isWifiEnabled = true,
                isLocationEnabled = locationEnabled,
                hasPermission = permissionGranted,
                statusMessage = "Perangkat tidak sedang terhubung ke jaringan WiFi mana pun."
            )
        }

        val rawSsid = info?.ssid?.trim().orEmpty()
        val unquotedSsid = if (rawSsid.length >= 2 && rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) {
            rawSsid.substring(1, rawSsid.length - 1)
        } else {
            rawSsid
        }

        val cleanSsid = when {
            unquotedSsid.isEmpty() || unquotedSsid.equals("<unknown ssid>", ignoreCase = true) -> {
                when {
                    !permissionGranted -> "Izin Lokasi Diperlukan"
                    !locationEnabled -> "Lokasi Sistem Nonaktif"
                    else -> "Tidak tersedia (Tersembunyi/Terbatas)"
                }
            }
            else -> unquotedSsid
        }

        val rawBssid = info?.bssid?.trim().orEmpty()
        val cleanBssid = if (
            rawBssid.isEmpty() ||
            rawBssid == "02:00:00:00:00:00" ||
            rawBssid == "00:00:00:00:00:00"
        ) {
            "Tidak tersedia"
        } else {
            rawBssid.uppercase()
        }

        val rssi = info?.rssi?.takeIf { it in -126..-1 } ?: -127
        val signalPercent = if (rssi in -126..-1) calculateSignalPercent(rssi) else 0
        val freq = info?.frequency?.takeIf { it > 0 } ?: 0
        val channel = if (freq > 0) freqToChannel(freq) else 0
        val band = if (freq > 0) freqToBand(freq) else WifiBand.UNKNOWN

        val linkSpeed = info?.linkSpeed?.takeIf { it > 0 } ?: 0
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info?.rxLinkSpeedMbps?.takeIf { it > 0 } ?: linkSpeed
        } else linkSpeed
        val txSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info?.txLinkSpeedMbps?.takeIf { it > 0 } ?: linkSpeed
        } else linkSpeed

        val standard = resolveWifiStandard(info)
        val security = resolveConnectedSecurity(info, cleanBssid, cleanSsid)

        val statusMsg = when {
            !permissionGranted -> "Izin lokasi/WiFi belum diberikan. SSID dan BSSID tidak dapat dibaca oleh sistem Android."
            !locationEnabled -> "Layanan Lokasi (GPS) sistem mati. Aktifkan Lokasi agar Android mengizinkan pembacaan SSID & pemindaian."
            else -> "Terhubung ke WiFi aktif"
        }

        return ConnectedWifiInfo(
            ssid = cleanSsid,
            bssid = cleanBssid,
            rssi = rssi,
            signalPercent = signalPercent,
            frequency = freq,
            channel = channel,
            band = band,
            linkSpeedMbps = linkSpeed,
            rxLinkSpeedMbps = rxSpeed,
            txLinkSpeedMbps = txSpeed,
            ipAddress = netConfig.ipAddress,
            gateway = netConfig.gateway,
            subnetMask = netConfig.subnetMask,
            prefixLength = netConfig.prefixLength,
            dns1 = netConfig.dns1,
            dns2 = netConfig.dns2,
            wifiStandard = standard,
            securityType = security,
            isConnected = true,
            isWifiEnabled = true,
            isLocationEnabled = locationEnabled,
            hasPermission = permissionGranted,
            statusMessage = statusMsg
        )
    }

    open suspend fun scanAccessPointsWithState(): WifiScanState = withContext(Dispatchers.IO) {
        if (!isWifiEnabled()) {
            return@withContext WifiScanState(
                status = WifiScanStatus.WIFI_DISABLED,
                accessPoints = emptyList(),
                statusMessage = "WiFi perangkat sedang mati. Nyalakan WiFi untuk memindai saluran di sekitar."
            )
        }

        if (!hasRequiredWifiPermissions()) {
            return@withContext WifiScanState(
                status = WifiScanStatus.PERMISSION_DENIED,
                accessPoints = emptyList(),
                statusMessage = "Izin Lokasi / Perangkat WiFi Terdekat ditolak. Berikan izin untuk memindai jaringan sekitar."
            )
        }

        if (!isLocationServiceEnabled()) {
            return@withContext WifiScanState(
                status = WifiScanStatus.LOCATION_DISABLED,
                accessPoints = emptyList(),
                statusMessage = "Layanan Lokasi (GPS) Android sedang nonaktif. Android mewajibkan Lokasi aktif untuk memindai AP sekitar."
            )
        }

        val manager = wifiManager ?: return@withContext WifiScanState(
            status = WifiScanStatus.ERROR,
            accessPoints = emptyList(),
            statusMessage = "Layanan WifiManager tidak tersedia pada perangkat ini."
        )

        var scanTriggeredOk = false
        var broadcastUpdated: Boolean? = null

        try {
            broadcastUpdated = withTimeoutOrNull(3500L) {
                awaitScanBroadcast(manager) { triggered ->
                    scanTriggeredOk = triggered
                }
            }
        } catch (_: SecurityException) {
            return@withContext WifiScanState(
                status = WifiScanStatus.PERMISSION_DENIED,
                accessPoints = emptyList(),
                statusMessage = "Akses pemindaian WiFi ditolak oleh sistem operasi."
            )
        } catch (_: Exception) {
            // Fall through to read available scanResults cache
        }

        val rawResults: List<ScanResult> = try {
            manager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            return@withContext WifiScanState(
                status = WifiScanStatus.PERMISSION_DENIED,
                accessPoints = emptyList(),
                statusMessage = "Izin pembacaan hasil pindaian WiFi ditolak."
            )
        } catch (_: Exception) {
            emptyList()
        }

        val connectedInfo = getConnectedWifiInfo()
        val parsedList = rawResults.mapNotNull { sr ->
            val freq = sr.frequency
            if (freq <= 0) return@mapNotNull null
            @Suppress("DEPRECATION")
            val rawSsid = sr.SSID?.trim().orEmpty()
            val ssidName = if (rawSsid.isEmpty()) "<SSID Tersembunyi>" else rawSsid
            val bssidStr = sr.BSSID?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "Tidak tersedia"
            val channel = freqToChannel(freq)
            val band = freqToBand(freq)
            val isConn = connectedInfo.isConnected && (
                (connectedInfo.bssid != "Tidak tersedia" && bssidStr.equals(connectedInfo.bssid, ignoreCase = true)) ||
                    (connectedInfo.bssid == "Tidak tersedia" && ssidName == connectedInfo.ssid)
                )
            val dist = calculateDistance(sr.level, freq)
            val width = resolveChannelWidthMhz(sr)
            val sec = parseScanResultCapabilities(sr.capabilities)
            val std = resolveScanResultStandard(sr)

            WifiAccessPoint(
                ssid = ssidName,
                bssid = bssidStr,
                rssi = sr.level,
                frequency = freq,
                channel = channel,
                band = band,
                channelWidth = width,
                security = sec,
                standard = std,
                isConnected = isConn,
                distanceMeters = dist,
                signalPercent = calculateSignalPercent(sr.level)
            )
        }.sortedByDescending { it.rssi }

        val isThrottledOrCached = !scanTriggeredOk || broadcastUpdated == false

        if (parsedList.isEmpty()) {
            val emptyMsg = if (isThrottledOrCached) {
                "Pemindaian dibatasi oleh Android (scan throttling) dan belum ada cache hasil pindaian yang tersedia."
            } else {
                "Tidak ada jaringan WiFi yang terdeteksi di sekitar perangkat."
            }
            return@withContext WifiScanState(
                status = WifiScanStatus.EMPTY,
                accessPoints = emptyList(),
                statusMessage = emptyMsg,
                isCachedResult = isThrottledOrCached,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }

        val status = if (isThrottledOrCached) WifiScanStatus.THROTTLED_CACHE else WifiScanStatus.SUCCESS
        val msg = if (isThrottledOrCached) {
            "Menampilkan ${parsedList.size} jaringan dari cache sistem (Android membatasi frekuensi scan aktif)."
        } else {
            "Berhasil memindai ${parsedList.size} jaringan WiFi di sekitar."
        }

        WifiScanState(
            status = status,
            accessPoints = parsedList,
            statusMessage = msg,
            isCachedResult = isThrottledOrCached,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    suspend fun scanAccessPoints(): List<WifiAccessPoint> {
        return scanAccessPointsWithState().accessPoints
    }

    fun scanNearbyNetworks(): List<WifiAccessPoint> {
        if (!isWifiEnabled() || !hasRequiredWifiPermissions() || !isLocationServiceEnabled()) {
            return emptyList()
        }
        val manager = wifiManager ?: return emptyList()
        val rawResults: List<ScanResult> = try {
            manager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val connectedInfo = getConnectedWifiInfo()
        return rawResults.mapNotNull { sr ->
            val freq = sr.frequency
            if (freq <= 0) return@mapNotNull null
            @Suppress("DEPRECATION")
            val rawSsid = sr.SSID?.trim().orEmpty()
            val ssidName = if (rawSsid.isEmpty()) "<SSID Tersembunyi>" else rawSsid
            val bssidStr = sr.BSSID?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "Tidak tersedia"
            val channel = freqToChannel(freq)
            val band = freqToBand(freq)
            val isConn = connectedInfo.isConnected && (
                (connectedInfo.bssid != "Tidak tersedia" && bssidStr.equals(connectedInfo.bssid, ignoreCase = true)) ||
                    (connectedInfo.bssid == "Tidak tersedia" && ssidName == connectedInfo.ssid)
                )
            WifiAccessPoint(
                ssid = ssidName,
                bssid = bssidStr,
                rssi = sr.level,
                frequency = freq,
                channel = channel,
                band = band,
                channelWidth = resolveChannelWidthMhz(sr),
                security = parseScanResultCapabilities(sr.capabilities),
                standard = resolveScanResultStandard(sr),
                isConnected = isConn,
                distanceMeters = calculateDistance(sr.level, freq),
                signalPercent = calculateSignalPercent(sr.level)
            )
        }.sortedByDescending { it.rssi }
    }

    private suspend fun awaitScanBroadcast(
        manager: WifiManager,
        onTriggered: (Boolean) -> Unit
    ): Boolean = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    appContext.unregisterReceiver(this)
                } catch (_: Exception) {}
                val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (cont.isActive) {
                    cont.resume(updated)
                }
            }
        }

        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }

            cont.invokeOnCancellation {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: Exception) {}
            }

            @Suppress("DEPRECATION")
            val started = manager.startScan()
            onTriggered(started)
            if (!started) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: Exception) {}
                if (cont.isActive) {
                    cont.resume(false)
                }
            }
        } catch (_: Exception) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            onTriggered(false)
            if (cont.isActive) {
                cont.resume(false)
            }
        }
    }

    fun calculateChannelRatings(
        band: WifiBand,
        accessPoints: List<WifiAccessPoint>,
        currentChannel: Int = 0
    ): List<ChannelRating> {
        return NetworkValidator.calculateChannelRatings(
            band = band,
            accessPoints = accessPoints,
            currentChannel = currentChannel
        )
    }

    fun selectBestRecommendedChannel(ratings: List<ChannelRating>): ChannelRating? {
        return NetworkValidator.selectBestRecommendedChannel(ratings)
    }

    fun extractNetworkParams(linkProps: LinkProperties?, wifiInfo: WifiInfo? = null): ExtractedNetworkConfig {
        var ipAddress = "Tidak tersedia"
        var prefixLength = 0
        var subnetMask = "Tidak tersedia"
        var gateway = "Tidak tersedia"
        var dns1 = "Tidak tersedia"
        var dns2 = "Tidak tersedia"

        // 1. Extract IPv4 LinkAddress and prefixLength from LinkProperties
        val ipv4LinkAddr = linkProps?.linkAddresses?.firstOrNull { linkAddr ->
            val addr = linkAddr.address
            addr is Inet4Address && !addr.isLoopbackAddress
        }

        if (ipv4LinkAddr != null) {
            ipAddress = ipv4LinkAddr.address.hostAddress ?: "Tidak tersedia"
            prefixLength = ipv4LinkAddr.prefixLength
            subnetMask = NetworkValidator.prefixLengthToSubnetMask(prefixLength)
        } else {
            @Suppress("DEPRECATION")
            val rawIpInt = wifiInfo?.ipAddress ?: 0
            val formattedWifiIp = formatIpAddress(rawIpInt)
            if (formattedWifiIp != "0.0.0.0") {
                ipAddress = formattedWifiIp
            } else {
                val fallbackLocal = getLocalIpV4AndPrefix()
                if (fallbackLocal != null) {
                    ipAddress = fallbackLocal.first
                    prefixLength = fallbackLocal.second
                    subnetMask = NetworkValidator.prefixLengthToSubnetMask(prefixLength)
                }
            }
        }

        if (ipAddress != "Tidak tersedia" && prefixLength == 0) {
            val ifacePrefix = getPrefixLengthForIp(ipAddress)
            if (ifacePrefix in 1..32) {
                prefixLength = ifacePrefix
                subnetMask = NetworkValidator.prefixLengthToSubnetMask(prefixLength)
            }
        }

        // 2. Extract default IPv4 Gateway from LinkProperties routes
        linkProps?.routes?.forEach { route ->
            val gw = route.gateway
            if (route.isDefaultRoute && gw is Inet4Address && !gw.isLoopbackAddress) {
                val gwHost = gw.hostAddress
                if (!gwHost.isNullOrBlank() && gwHost != "0.0.0.0") {
                    gateway = gwHost
                }
            }
        }

        // 3. Extract DNS servers from LinkProperties
        val dnsServers = linkProps?.dnsServers
            ?.mapNotNull { it.hostAddress?.takeIf { addr -> addr.isNotBlank() } }
            .orEmpty()

        if (dnsServers.isNotEmpty()) {
            dns1 = dnsServers.getOrNull(0) ?: "Tidak tersedia"
            dns2 = dnsServers.getOrNull(1) ?: "Tidak tersedia"
        }

        return ExtractedNetworkConfig(
            ipAddress = ipAddress,
            prefixLength = prefixLength,
            subnetMask = subnetMask,
            gateway = gateway,
            dns1 = dns1,
            dns2 = dns2
        )
    }

    private fun resolveWifiStandard(info: WifiInfo?): String {
        if (info == null) return "Tidak tersedia"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return mapStandardIntToString(info.wifiStandard)
        }
        return "Tidak tersedia"
    }

    private fun resolveScanResultStandard(sr: ScanResult): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return mapStandardIntToString(sr.wifiStandard)
        }
        return "Tidak tersedia"
    }

    fun mapStandardIntToString(standard: Int): String {
        return when (standard) {
            8 -> "Wi-Fi 7 (802.11be)" // ScanResult.WIFI_STANDARD_11BE (API 33+)
            ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6/6E (802.11ax)"
            ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
            ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
            7 -> "802.11ad (WiGig)" // ScanResult.WIFI_STANDARD_11AD
            ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g (Legacy)"
            else -> "Tidak tersedia"
        }
    }

    private fun resolveConnectedSecurity(info: WifiInfo?, bssid: String, ssid: String): String {
        if (info == null) return "Tidak tersedia"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val secLabel = when (info.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_OPEN -> "Open (Tanpa Sandi)"
                WifiInfo.SECURITY_TYPE_WEP -> "WEP (Tidak Aman)"
                WifiInfo.SECURITY_TYPE_PSK -> "WPA2-Personal (PSK)"
                WifiInfo.SECURITY_TYPE_EAP -> "WPA2-Enterprise (EAP)"
                WifiInfo.SECURITY_TYPE_SAE -> "WPA3-Personal (SAE)"
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise 192-bit"
                WifiInfo.SECURITY_TYPE_OWE -> "Enhanced Open (OWE)"
                WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI-PSK"
                WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-CERT"
                else -> null
            }
            if (secLabel != null) return secLabel
        }

        // Fallback: check matching ScanResult capabilities if permissions allow
        if (!hasRequiredWifiPermissions()) return "Tidak tersedia"
        return try {
            val scanMatch = wifiManager?.scanResults?.firstOrNull { sr ->
                (bssid != "Tidak tersedia" && sr.BSSID.equals(bssid, ignoreCase = true)) ||
                    (@Suppress("DEPRECATION") sr.SSID == ssid)
            }
            if (scanMatch != null) {
                parseScanResultCapabilities(scanMatch.capabilities)
            } else {
                "Tidak tersedia"
            }
        } catch (_: SecurityException) {
            "Tidak tersedia"
        } catch (_: Exception) {
            "Tidak tersedia"
        }
    }

    fun parseScanResultCapabilities(capabilities: String?): String {
        val caps = capabilities?.uppercase()?.trim().orEmpty()
        if (caps.isEmpty()) return "Tidak tersedia"
        return when {
            caps.contains("WPA3") && caps.contains("WPA2") -> "WPA2/WPA3-Personal"
            caps.contains("SAE") || caps.contains("WPA3-ENTERPRISE") || caps.contains("WPA3") -> {
                if (caps.contains("EAP")) "WPA3-Enterprise" else "WPA3-Personal (SAE)"
            }
            caps.contains("OWE") -> "Enhanced Open (OWE)"
            caps.contains("WPA2") && caps.contains("EAP") -> "WPA2-Enterprise"
            caps.contains("WPA2") -> "WPA2-Personal (PSK)"
            caps.contains("WPA") -> "WPA-Personal"
            caps.contains("WEP") -> "WEP (Legacy)"
            caps == "[ESS]" || caps == "[IBSS]" || (!caps.contains("WPA") && !caps.contains("WEP") && !caps.contains("SAE") && !caps.contains("RSN")) -> "Open (Tanpa Sandi)"
            else -> capabilities?.takeIf { it.isNotBlank() } ?: "Tidak tersedia"
        }
    }

    private fun resolveChannelWidthMhz(sr: ScanResult): Int {
        return when (sr.channelWidth) {
            ScanResult.CHANNEL_WIDTH_40MHZ -> 40
            ScanResult.CHANNEL_WIDTH_80MHZ -> 80
            ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
            ScanResult.CHANNEL_WIDTH_320MHZ -> 320
            else -> 20
        }
    }

    fun formatIpAddress(ip: Int): String {
        return if (ip == 0) "0.0.0.0"
        else {
            val byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ip)
            val bytes = byteBuffer.array()
            "%d.%d.%d.%d".format(
                bytes[0].toInt() and 0xFF,
                bytes[1].toInt() and 0xFF,
                bytes[2].toInt() and 0xFF,
                bytes[3].toInt() and 0xFF
            )
        }
    }

    private fun getLocalIpV4AndPrefix(): Pair<String, Int>? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val addr = ifaceAddr.address
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddr = addr.hostAddress
                        if (!hostAddr.isNullOrBlank()) {
                            return Pair(hostAddr, ifaceAddr.networkPrefixLength.toInt())
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getPrefixLengthForIp(ip: String): Int {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return 0
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                for (ifaceAddr in iface.interfaceAddresses) {
                    if (ifaceAddr.address.hostAddress == ip) {
                        return ifaceAddr.networkPrefixLength.toInt()
                    }
                }
            }
        } catch (_: Exception) {}
        return 0
    }

    fun freqToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2407) / 5
            freq in 5170..5885 -> (freq - 5000) / 5
            freq == 5935 -> 2
            freq in 5955..7115 -> (freq - 5950) / 5
            else -> 0
        }
    }

    fun freqToBand(freq: Int): WifiBand {
        return when {
            freq in 2400..2500 -> WifiBand.BAND_2_4_GHZ
            freq in 4900..5895 -> WifiBand.BAND_5_GHZ
            freq >= 5925 -> WifiBand.BAND_6_GHZ
            else -> WifiBand.BAND_2_4_GHZ
        }
    }

    fun calculateSignalPercent(rssi: Int): Int {
        if (rssi <= -100) return 0
        if (rssi >= -50) return 100
        return ((rssi + 100) * 2).coerceIn(0, 100)
    }

    fun calculateDistance(rssi: Int, freqMhz: Int): Double {
        if (freqMhz <= 0 || rssi <= -127) return 0.0
        val exp = (27.55 - (20 * log10(freqMhz.toDouble())) + abs(rssi)) / 20.0
        val dist = 10.0.pow(exp)
        return (dist * 10.0).roundToInt() / 10.0
    }
}
