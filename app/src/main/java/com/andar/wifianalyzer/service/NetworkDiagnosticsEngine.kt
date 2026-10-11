package com.andar.wifianalyzer.service

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

data class PublicIpLookupResult(
    val ip: String,
    val isp: String,
    val status: PublicIpStatus
)

data class ProbeResult(
    val success: Boolean,
    val durationMs: Float = 0f,
    val methodUsed: String = "TCP",
    val errorType: DnsPingStatus = DnsPingStatus.FAILED,
    val errorDetail: String? = null
)

open class NetworkDiagnosticsEngine(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val hostProbeOverride: (suspend (ip: String, port: Int, timeoutMs: Int) -> ProbeResult)? = null
) {

    companion object {
        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }

    open suspend fun fetchPublicIpAndIsp(): PublicIpLookupResult = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val req = Request.Builder()
                .url("https://ipinfo.io/json")
                .header("User-Agent", SpeedTestEngine.USER_AGENT)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotEmpty()) {
                        val json = JSONObject(body)
                        val ip = json.optString("ip", "").trim()
                        val org = json.optString("org", "").trim()
                        val city = json.optString("city", "").trim()
                        if (ip.isNotEmpty()) {
                            val ispLabel = when {
                                org.isNotEmpty() && city.isNotEmpty() -> "$org ($city)"
                                org.isNotEmpty() -> org
                                city.isNotEmpty() -> city
                                else -> "Tidak tersedia"
                            }
                            return@withContext PublicIpLookupResult(
                                ip = ip,
                                isp = ispLabel,
                                status = PublicIpStatus.SUCCESS
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Try fallback HTTPS endpoint for public IP only
        }

        try {
            currentCoroutineContext().ensureActive()
            val req2 = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("User-Agent", SpeedTestEngine.USER_AGENT)
                .build()
            httpClient.newCall(req2).execute().use { resp2 ->
                if (resp2.isSuccessful) {
                    val body2 = resp2.body?.string().orEmpty()
                    if (body2.isNotEmpty()) {
                        val json2 = JSONObject(body2)
                        val ip = json2.optString("ip", "").trim()
                        if (ip.isNotEmpty()) {
                            return@withContext PublicIpLookupResult(
                                ip = ip,
                                isp = "Tidak tersedia",
                                status = PublicIpStatus.SUCCESS
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            return@withContext PublicIpLookupResult(
                ip = "Offline / Tidak tersedia",
                isp = "Tidak tersedia",
                status = PublicIpStatus.OFFLINE
            )
        } catch (_: Exception) {
            return@withContext PublicIpLookupResult(
                ip = "Tidak tersedia",
                isp = "Tidak tersedia",
                status = PublicIpStatus.ERROR
            )
        }

        PublicIpLookupResult(
            ip = "Tidak tersedia",
            isp = "Tidak tersedia",
            status = PublicIpStatus.ERROR
        )
    }

    fun scanLocalSubnetFlow(
        localIp: String,
        prefixLength: Int,
        gatewayIp: String
    ): Flow<LanScanState> = flow {
        if (!NetworkValidator.isValidIpv4(localIp) || !NetworkValidator.isPrivateRfc1918Ipv4(localIp)) {
            emit(
                LanScanState(
                    status = DiagnosticTaskStatus.ERROR,
                    devices = emptyList(),
                    statusMessage = "Tidak terhubung ke subnet IPv4 privat (LAN/WiFi). Sambungkan ke WiFi terlebih dahulu.",
                    errorMessage = "IP lokal tidak valid untuk pemindaian subnet: $localIp"
                )
            )
            return@flow
        }

        val effectivePrefix = if (prefixLength in 8..30) prefixLength else 24
        val subnetInfo = NetworkValidator.calculateSubnetInfo(localIp, effectivePrefix)
        if (subnetInfo == null) {
            emit(
                LanScanState(
                    status = DiagnosticTaskStatus.ERROR,
                    devices = emptyList(),
                    statusMessage = "Gagal menghitung rentang subnet lokal.",
                    errorMessage = "Konfigurasi subnet tidak valid."
                )
            )
            return@flow
        }

        val arpTable = readArpCacheSafe()
        val foundDevices = mutableListOf<LanDevice>()

        // 1. Always include this local Android device truthfully
        foundDevices.add(
            LanDevice(
                ip = localIp,
                mac = "Tidak tersedia (Privasi Android)",
                hostname = "Perangkat Ini (Android)",
                pingMs = 0,
                isGateway = false,
                isLocalDevice = true,
                deviceType = "Smartphone / Klien Aktif",
                vendor = "Tidak tersedia",
                detectionMethod = "Antarmuka Lokal"
            )
        )

        // 2. Probe gateway if valid IPv4
        if (NetworkValidator.isValidIpv4(gatewayIp) && gatewayIp != localIp) {
            val gwDevice = probeLanHost(gatewayIp, isGateway = true, arpTable = arpTable)
            if (gwDevice != null) {
                foundDevices.add(gwDevice)
            }
        }

        val targetHosts = subnetInfo.usableHostIps.filter { it != localIp && it != gatewayIp }
        val totalToScan = targetHosts.size
        val scannedCounter = AtomicInteger(0)

        emit(
            LanScanState(
                status = DiagnosticTaskStatus.RUNNING,
                devices = foundDevices.sortedBy { NetworkValidator.ipv4ToInt(it.ip) ?: 0 },
                scannedCount = 0,
                totalHosts = totalToScan,
                subnetCidr = subnetInfo.cidrNotation,
                statusMessage = "Memindai subnet ${subnetInfo.cidrNotation} (0/$totalToScan host)..."
            )
        )

        val semaphore = Semaphore(24)
        // Process in batches so UI receives smooth progress updates and cancellation is responsive
        val chunks = targetHosts.chunked(24)
        for (chunk in chunks) {
            currentCoroutineContext().ensureActive()
            val discoveredInChunk = withContext(Dispatchers.IO) {
                chunk.map { ip ->
                    async {
                        semaphore.withPermit {
                            currentCoroutineContext().ensureActive()
                            val result = probeLanHost(ip, isGateway = false, arpTable = arpTable)
                            scannedCounter.incrementAndGet()
                            result
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (discoveredInChunk.isNotEmpty()) {
                foundDevices.addAll(discoveredInChunk)
            }

            val done = scannedCounter.get()
            val sortedCurrent = foundDevices.sortedBy { NetworkValidator.ipv4ToInt(it.ip) ?: 0 }
            emit(
                LanScanState(
                    status = DiagnosticTaskStatus.RUNNING,
                    devices = sortedCurrent,
                    scannedCount = done,
                    totalHosts = totalToScan,
                    subnetCidr = subnetInfo.cidrNotation,
                    statusMessage = "Memindai ${subnetInfo.cidrNotation} ($done/$totalToScan host) • ${sortedCurrent.size} perangkat merespons"
                )
            )
        }

        val finalSorted = foundDevices.sortedBy { NetworkValidator.ipv4ToInt(it.ip) ?: 0 }
        emit(
            LanScanState(
                status = if (finalSorted.isEmpty()) DiagnosticTaskStatus.EMPTY else DiagnosticTaskStatus.SUCCESS,
                devices = finalSorted,
                scannedCount = totalToScan,
                totalHosts = totalToScan,
                subnetCidr = subnetInfo.cidrNotation,
                statusMessage = "Pemindaian selesai pada ${subnetInfo.cidrNotation}. Terdeteksi ${finalSorted.size} perangkat aktif (catatan: perangkat dengan firewall ketat mungkin tidak merespons tanpa akses root)."
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun scanLocalSubnet(
        localIp: String,
        gatewayIp: String,
        prefixLength: Int = 24
    ): List<LanDevice> = withContext(Dispatchers.IO) {
        var lastList = emptyList<LanDevice>()
        scanLocalSubnetFlow(localIp, prefixLength, gatewayIp).collect { state ->
            lastList = state.devices
        }
        lastList
    }

    private suspend fun probeLanHost(
        ip: String,
        isGateway: Boolean,
        arpTable: Map<String, String>
    ): LanDevice? {
        currentCoroutineContext().ensureActive()
        val startTime = System.nanoTime()

        // 1. Custom override for unit tests
        if (hostProbeOverride != null) {
            val res = hostProbeOverride.invoke(ip, 80, 250)
            return if (res.success) {
                LanDevice(
                    ip = ip,
                    mac = arpTable[ip] ?: "Tidak tersedia",
                    hostname = if (isGateway) "Gateway / Router" else "Tidak tersedia",
                    pingMs = res.durationMs.toLong().coerceAtLeast(1L),
                    isGateway = isGateway,
                    isLocalDevice = false,
                    deviceType = if (isGateway) "Router / Gateway" else "Perangkat Jaringan",
                    vendor = "Tidak tersedia",
                    detectionMethod = res.methodUsed
                )
            } else null
        }

        // 2. Try InetAddress.isReachable (ICMP / TCP Echo port 7)
        try {
            val inet = InetAddress.getByName(ip)
            if (inet.isReachable(280)) {
                val elapsedMs = ((System.nanoTime() - startTime) / 1_000_000L).coerceAtLeast(1L)
                val resolvedHost = resolveLocalHostnameSafe(inet, ip, isGateway)
                return LanDevice(
                    ip = ip,
                    mac = arpTable[ip] ?: "Tidak tersedia",
                    hostname = resolvedHost,
                    pingMs = elapsedMs,
                    isGateway = isGateway,
                    isLocalDevice = false,
                    deviceType = if (isGateway) "Router / Gateway" else "Perangkat Jaringan",
                    vendor = "Tidak tersedia",
                    detectionMethod = "ICMP/Echo"
                )
            }
        } catch (_: Exception) {}

        // 3. Probe common TCP ports with short timeout (only open connection or verified ARP entry)
        val probePorts = if (isGateway) listOf(53, 80, 443, 8080) else listOf(80, 443, 22, 445, 8080, 53)
        for (port in probePorts) {
            currentCoroutineContext().ensureActive()
            val portStart = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 180)
                }
                val elapsedMs = ((System.nanoTime() - portStart) / 1_000_000L).coerceAtLeast(1L)
                val inet = try { InetAddress.getByName(ip) } catch (_: Exception) { null }
                val resolvedHost = resolveLocalHostnameSafe(inet, ip, isGateway)
                return LanDevice(
                    ip = ip,
                    mac = arpTable[ip] ?: "Tidak tersedia",
                    hostname = resolvedHost,
                    pingMs = elapsedMs,
                    isGateway = isGateway,
                    isLocalDevice = false,
                    deviceType = inferDeviceTypeFromPort(port, isGateway),
                    vendor = "Tidak tersedia",
                    detectionMethod = "TCP Port $port"
                )
            } catch (_: ConnectException) {
                // Only accept TCP RST if verified in OS ARP table with a valid non-zero MAC
                val arpMac = arpTable[ip]
                if (!arpMac.isNullOrBlank()) {
                    val elapsedMs = ((System.nanoTime() - portStart) / 1_000_000L).coerceAtLeast(1L)
                    val inet = try { InetAddress.getByName(ip) } catch (_: Exception) { null }
                    val resolvedHost = resolveLocalHostnameSafe(inet, ip, isGateway)
                    return LanDevice(
                        ip = ip,
                        mac = arpMac,
                        hostname = resolvedHost,
                        pingMs = elapsedMs,
                        isGateway = isGateway,
                        isLocalDevice = false,
                        deviceType = if (isGateway) "Router / Gateway" else "Perangkat Jaringan",
                        vendor = "Tidak tersedia",
                        detectionMethod = "ARP + TCP RST"
                    )
                }
            } catch (_: Exception) {
                // Timeout or unreachable
            }
        }

        return null
    }

    private fun inferDeviceTypeFromPort(port: Int, isGateway: Boolean): String {
        if (isGateway) return "Router / Gateway"
        return when (port) {
            53 -> "DNS / Router"
            22 -> "Server / Komputer (SSH)"
            445 -> "Komputer / NAS (SMB)"
            80, 443, 8080 -> "Perangkat Web / IoT"
            else -> "Perangkat Jaringan"
        }
    }

    private fun resolveLocalHostnameSafe(inet: InetAddress?, ip: String, isGateway: Boolean): String {
        if (inet != null) {
            try {
                val host = inet.canonicalHostName?.trim()
                if (!host.isNullOrEmpty() && host != ip) {
                    return host
                }
            } catch (_: Exception) {}
        }
        return if (isGateway) "Gateway / Router" else "Tidak tersedia"
    }

    private fun readArpCacheSafe(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val arpFile = File("/proc/net/arp")
            if (arpFile.exists() && arpFile.canRead()) {
                arpFile.useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val ip = parts[0]
                            val mac = parts[3].uppercase()
                            if (NetworkValidator.isValidIpv4(ip) &&
                                mac != "00:00:00:00:00:00" &&
                                mac.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}\$"))
                            ) {
                                map[ip] = mac
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Android 10+ restricts /proc/net/arp access for non-system apps
        }
        return map
    }

    fun executePing(targetHost: String, count: Int = 5): Flow<PingResult> = flow {
        val validation = NetworkValidator.validatePingTarget(targetHost)
        if (!validation.isValid) {
            val errMsg = validation.errorMessage ?: "Target tidak valid."
            emit(
                PingResult(
                    host = targetHost.trim(),
                    ip = "Tidak tersedia",
                    methodLabel = "Validasi Gagal",
                    sent = 0,
                    received = 0,
                    lossPercent = 0,
                    logLines = listOf("Error: $errMsg"),
                    isRunning = false,
                    status = PingStatus.FAILED,
                    errorMessage = errMsg
                )
            )
            return@flow
        }

        val hostToPing = validation.sanitizedTarget
        val logs = mutableListOf<String>()
        var resolvedIp = hostToPing

        try {
            if (hostProbeOverride == null) {
                val address = withContext(Dispatchers.IO) { InetAddress.getByName(hostToPing) }
                resolvedIp = address.hostAddress ?: hostToPing
            }
            logs.add("MENGUJI $hostToPing ($resolvedIp): $count probe (ICMP/TCP Reachability)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Gagal me-resolve host '$hostToPing': ${e.localizedMessage ?: "DNS tidak ditemukan"}"
            logs.add(msg)
            emit(
                PingResult(
                    host = hostToPing,
                    ip = "Tidak tersedia",
                    methodLabel = "DNS Gagal",
                    sent = 0,
                    received = 0,
                    lossPercent = 100,
                    logLines = logs.toList(),
                    isRunning = false,
                    status = PingStatus.FAILED,
                    errorMessage = msg
                )
            )
            return@flow
        }

        emit(
            PingResult(
                host = hostToPing,
                ip = resolvedIp,
                methodLabel = "Menguji...",
                sent = 0,
                received = 0,
                lossPercent = 0,
                logLines = logs.toList(),
                isRunning = true,
                status = PingStatus.RUNNING
            )
        )

        var sentCount = 0
        var receivedCount = 0
        var minMs = Float.MAX_VALUE
        var maxMs = 0f
        var totalMs = 0f
        var lastMethodUsed = "TCP Latency"

        val totalProbes = count.coerceIn(1, 20)
        for (seq in 1..totalProbes) {
            currentCoroutineContext().ensureActive()
            sentCount++

            val probe = probeSingleTarget(resolvedIp, timeoutMs = 1200)
            if (probe.success) {
                receivedCount++
                lastMethodUsed = probe.methodUsed
                val rtt = probe.durationMs.coerceAtLeast(0.1f)
                minMs = min(minMs, rtt)
                maxMs = max(maxMs, rtt)
                totalMs += rtt
                val timeStr = "%.1f".format(rtt)
                logs.add("Respons dari $resolvedIp: seq=$seq metode=${probe.methodUsed} waktu=$timeStr ms")
            } else {
                val reason = probe.errorDetail ?: "Request timeout / host tidak dapat dijangkau"
                logs.add("Gagal dari $resolvedIp: seq=$seq ($reason)")
            }

            val avgMs = if (receivedCount > 0) totalMs / receivedCount else 0f
            val lossPercent = NetworkValidator.calculatePacketLossPercent(sentCount, receivedCount)

            emit(
                PingResult(
                    host = hostToPing,
                    ip = resolvedIp,
                    methodLabel = lastMethodUsed,
                    sent = sentCount,
                    received = receivedCount,
                    lossPercent = lossPercent,
                    minMs = if (receivedCount == 0 || minMs == Float.MAX_VALUE) 0f else minMs,
                    avgMs = avgMs,
                    maxMs = if (receivedCount == 0) 0f else maxMs,
                    logLines = logs.toList(),
                    isRunning = seq < totalProbes,
                    status = if (seq < totalProbes) PingStatus.RUNNING else if (receivedCount > 0) PingStatus.COMPLETED else PingStatus.FAILED,
                    errorMessage = if (seq == totalProbes && receivedCount == 0) "Seluruh paket gagal (100% packet loss). Host tidak merespons." else null
                )
            )

            if (seq < totalProbes) {
                delay(500)
            }
        }

        val finalAvg = if (receivedCount > 0) totalMs / receivedCount else 0f
        val finalLoss = NetworkValidator.calculatePacketLossPercent(sentCount, receivedCount)
        val finalMin = if (receivedCount == 0 || minMs == Float.MAX_VALUE) 0f else minMs
        val finalMax = if (receivedCount == 0) 0f else maxMs

        logs.add("--- Statistik $hostToPing ($lastMethodUsed) ---")
        logs.add("$sentCount dikirim, $receivedCount diterima, $finalLoss% packet loss")
        if (receivedCount > 0) {
            logs.add("Waktu min/avg/max = %.1f/%.1f/%.1f ms".format(finalMin, finalAvg, finalMax))
        } else {
            logs.add("Host tidak dapat dijangkau (100% loss).")
        }

        emit(
            PingResult(
                host = hostToPing,
                ip = resolvedIp,
                methodLabel = lastMethodUsed,
                sent = sentCount,
                received = receivedCount,
                lossPercent = finalLoss,
                minMs = finalMin,
                avgMs = finalAvg,
                maxMs = finalMax,
                logLines = logs.toList(),
                isRunning = false,
                status = if (receivedCount > 0) PingStatus.COMPLETED else PingStatus.FAILED,
                errorMessage = if (receivedCount == 0) "Target $hostToPing tidak merespons (100% packet loss)." else null
            )
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun probeSingleTarget(ip: String, timeoutMs: Int): ProbeResult {
        if (hostProbeOverride != null) {
            return hostProbeOverride.invoke(ip, 443, timeoutMs)
        }

        // 1. Try TCP handshake on common service ports (443, 80, 53) for accurate user-space TCP latency
        val candidatePorts = listOf(443, 80, 53)
        var lastError: Exception? = null
        for (port in candidatePorts) {
            currentCoroutineContext().ensureActive()
            val startTime = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                }
                val durationMs = (System.nanoTime() - startTime) / 1_000_000f
                return ProbeResult(
                    success = true,
                    durationMs = durationMs,
                    methodUsed = "TCP Latency (port $port)",
                    errorType = DnsPingStatus.COMPLETED
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 2. Fallback to InetAddress.isReachable (ICMP / Echo)
        val icmpStart = System.nanoTime()
        try {
            val inet = InetAddress.getByName(ip)
            if (inet.isReachable(timeoutMs)) {
                val durationMs = (System.nanoTime() - icmpStart) / 1_000_000f
                return ProbeResult(
                    success = true,
                    durationMs = durationMs,
                    methodUsed = "ICMP/Echo Reachability",
                    errorType = DnsPingStatus.COMPLETED
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }

        val (status, detail) = classifyProbeFailure(lastError)
        return ProbeResult(
            success = false,
            durationMs = 0f,
            methodUsed = "TCP/ICMP",
            errorType = status,
            errorDetail = detail
        )
    }

    suspend fun scanPortsWithValidation(
        rawTargetIp: String,
        localIp: String? = null,
        prefixLength: Int = 0
    ): PortScanState = withContext(Dispatchers.IO) {
        val validation = NetworkValidator.validatePortScanTarget(rawTargetIp, localIp, prefixLength)
        if (!validation.isValid) {
            return@withContext PortScanState(
                status = DiagnosticTaskStatus.ERROR,
                targetIp = rawTargetIp.trim(),
                results = emptyList(),
                statusMessage = validation.errorMessage ?: "Target ditolak.",
                errorMessage = validation.errorMessage
            )
        }

        val targetIp = validation.sanitizedTarget
        val results = scanPortsInternal(targetIp)
        val openCount = results.count { it.isOpen }
        PortScanState(
            status = DiagnosticTaskStatus.SUCCESS,
            targetIp = targetIp,
            results = results,
            statusMessage = "Pemindaian selesai pada $targetIp: $openCount dari ${results.size} port terbuka.",
            errorMessage = null
        )
    }

    suspend fun scanPorts(targetIp: String): List<PortScanResult> = withContext(Dispatchers.IO) {
        val validation = NetworkValidator.validatePortScanTarget(targetIp)
        if (!validation.isValid) {
            return@withContext emptyList()
        }
        scanPortsInternal(validation.sanitizedTarget)
    }

    private suspend fun scanPortsInternal(targetIp: String): List<PortScanResult> = withContext(Dispatchers.IO) {
        val commonPorts = listOf(
            21 to "FTP",
            22 to "SSH",
            23 to "Telnet",
            53 to "DNS",
            80 to "HTTP (Web)",
            443 to "HTTPS (Secure Web)",
            445 to "SMB / Samba",
            8080 to "HTTP Alternate / Proxy",
            8443 to "HTTPS Alternate",
            3306 to "MySQL Database",
            5000 to "UPnP / Media Server"
        )

        val semaphore = Semaphore(6)
        commonPorts.map { (port, service) ->
            async {
                semaphore.withPermit {
                    currentCoroutineContext().ensureActive()
                    var isOpen = false
                    var latency: Long? = null
                    val startNs = System.nanoTime()
                    try {
                        if (hostProbeOverride != null) {
                            val overrideRes = hostProbeOverride.invoke(targetIp, port, 300)
                            isOpen = overrideRes.success
                            if (isOpen) latency = overrideRes.durationMs.toLong()
                        } else {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(targetIp, port), 300)
                            }
                            isOpen = true
                            latency = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        isOpen = false
                    }
                    PortScanResult(port, service, isOpen, latency)
                }
            }
        }.awaitAll()
    }

    fun getDefaultDnsList(gatewayIp: String): List<DnsServerPingItem> {
        val list = mutableListOf(
            DnsServerPingItem(
                id = "cloudflare",
                name = "Cloudflare DNS",
                primaryIp = "1.1.1.1",
                secondaryIp = "1.0.0.1",
                description = "Resolver publik anycast global Cloudflare"
            ),
            DnsServerPingItem(
                id = "google",
                name = "Google Public DNS",
                primaryIp = "8.8.8.8",
                secondaryIp = "8.8.4.4",
                description = "Resolver publik global Google"
            )
        )

        if (NetworkValidator.isValidIpv4(gatewayIp)) {
            list.add(
                DnsServerPingItem(
                    id = "router",
                    name = "Gateway / Router Lokal",
                    primaryIp = gatewayIp,
                    description = "DNS forwarder bawaan router WiFi lokal"
                )
            )
        }

        list.addAll(
            listOf(
                DnsServerPingItem(
                    id = "quad9",
                    name = "Quad9 DNS",
                    primaryIp = "9.9.9.9",
                    secondaryIp = "149.112.112.112",
                    description = "Resolver publik dengan pemfilteran domain berbahaya"
                ),
                DnsServerPingItem(
                    id = "opendns",
                    name = "OpenDNS (Cisco)",
                    primaryIp = "208.67.222.222",
                    secondaryIp = "208.67.220.220",
                    description = "Resolver DNS publik Cisco OpenDNS"
                ),
                DnsServerPingItem(
                    id = "adguard",
                    name = "AdGuard DNS",
                    primaryIp = "94.140.14.14",
                    secondaryIp = "94.140.15.15",
                    description = "Resolver DNS publik AdGuard"
                ),
                DnsServerPingItem(
                    id = "cleanbrowsing",
                    name = "CleanBrowsing",
                    primaryIp = "185.228.168.9",
                    secondaryIp = "185.228.169.9",
                    description = "Resolver DNS dengan filter keamanan"
                ),
                DnsServerPingItem(
                    id = "controld",
                    name = "Control D",
                    primaryIp = "76.76.2.0",
                    secondaryIp = "76.76.10.0",
                    description = "Resolver DNS anycast Control D"
                )
            )
        )

        return list
    }

    suspend fun pingDnsServer(dnsItem: DnsServerPingItem, probes: Int = 3): DnsServerPingItem = withContext(Dispatchers.IO) {
        val totalProbes = probes.coerceIn(1, 10)
        val latencies = mutableListOf<Float>()
        var lastFailureStatus = DnsPingStatus.FAILED
        var lastFailureDetail = "Gagal menghubungi server DNS"

        for (i in 1..totalProbes) {
            currentCoroutineContext().ensureActive()
            val probe = probeDnsEndpoint(dnsItem.primaryIp, timeoutMs = 1200)
            if (probe.success) {
                latencies.add(probe.durationMs.coerceAtLeast(0.1f))
            } else {
                lastFailureStatus = probe.errorType
                lastFailureDetail = probe.errorDetail ?: lastFailureDetail
            }
            if (i < totalProbes) {
                delay(40)
            }
        }

        val lossPercent = NetworkValidator.calculatePacketLossPercent(totalProbes, latencies.size)

        if (latencies.isEmpty()) {
            dnsItem.copy(
                latencyMs = -1f,
                minMs = 0f,
                maxMs = 0f,
                packetLossPercent = 100,
                status = lastFailureStatus,
                statusDetail = lastFailureDetail,
                errorMessage = lastFailureDetail,
                isFastest = false
            )
        } else {
            val avg = ((latencies.average() * 10).toInt() / 10f).coerceAtLeast(0.1f)
            val minVal = ((latencies.minOrNull() ?: avg) * 10).toInt() / 10f
            val maxVal = ((latencies.maxOrNull() ?: avg) * 10).toInt() / 10f
            dnsItem.copy(
                latencyMs = avg,
                minMs = minVal,
                maxMs = maxVal,
                packetLossPercent = lossPercent,
                status = DnsPingStatus.COMPLETED,
                statusDetail = if (lossPercent > 0) "Loss $lossPercent%" else "TCP Port 53 OK",
                errorMessage = null
            )
        }
    }

    private suspend fun probeDnsEndpoint(ip: String, timeoutMs: Int): ProbeResult {
        if (hostProbeOverride != null) {
            return hostProbeOverride.invoke(ip, 53, timeoutMs)
        }

        val startTime = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 53), timeoutMs)
            }
            val durationMs = (System.nanoTime() - startTime) / 1_000_000f
            ProbeResult(
                success = true,
                durationMs = durationMs,
                methodUsed = "TCP/53",
                errorType = DnsPingStatus.COMPLETED
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val (status, detail) = classifyProbeFailure(e)
            ProbeResult(
                success = false,
                durationMs = 0f,
                methodUsed = "TCP/53",
                errorType = status,
                errorDetail = detail
            )
        }
    }

    private fun classifyProbeFailure(e: Exception?): Pair<DnsPingStatus, String> {
        return when (e) {
            is SocketTimeoutException -> DnsPingStatus.TIMEOUT to "Timeout"
            is NoRouteToHostException -> DnsPingStatus.UNREACHABLE to "Tidak terjangkau"
            is ConnectException -> DnsPingStatus.UNREACHABLE to "Koneksi ditolak"
            is UnknownHostException -> DnsPingStatus.OFFLINE to "Offline / DNS gagal"
            else -> {
                val msg = e?.message?.lowercase().orEmpty()
                when {
                    msg.contains("network is unreachable") || msg.contains("enetunreach") ->
                        DnsPingStatus.OFFLINE to "Jaringan offline"
                    msg.contains("timed out") ->
                        DnsPingStatus.TIMEOUT to "Timeout"
                    else ->
                        DnsPingStatus.FAILED to "Gagal"
                }
            }
        }
    }

    fun benchmarkDnsList(servers: List<DnsServerPingItem>): Flow<List<DnsServerPingItem>> = flow {
        val currentList = servers.map {
            it.copy(
                status = DnsPingStatus.IDLE,
                statusDetail = "Menunggu...",
                isFastest = false
            )
        }.toMutableList()
        emit(currentList.toList())

        for (index in currentList.indices) {
            currentCoroutineContext().ensureActive()
            currentList[index] = currentList[index].copy(
                status = DnsPingStatus.TESTING,
                statusDetail = "Menguji TCP/53..."
            )
            emit(currentList.toList())

            val tested = pingDnsServer(currentList[index])
            currentList[index] = tested
            emit(currentList.toList())
        }

        val validServers = currentList.filter { it.status == DnsPingStatus.COMPLETED && it.latencyMs > 0f }
        val minLatency = validServers.minOfOrNull { it.latencyMs }

        val finalList = currentList.map { item ->
            if (minLatency != null && item.status == DnsPingStatus.COMPLETED && item.latencyMs == minLatency) {
                item.copy(isFastest = true)
            } else {
                item.copy(isFastest = false)
            }
        }.sortedWith(
            compareBy<DnsServerPingItem> { it.status != DnsPingStatus.COMPLETED }
                .thenBy { if (it.latencyMs > 0f) it.latencyMs else Float.MAX_VALUE }
        )

        emit(finalList)
    }.flowOn(Dispatchers.IO)
}
