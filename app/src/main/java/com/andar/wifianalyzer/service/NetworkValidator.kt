package com.andar.wifianalyzer.service

import com.andar.wifianalyzer.model.ChannelRating
import com.andar.wifianalyzer.model.WifiAccessPoint
import com.andar.wifianalyzer.model.WifiBand
import kotlin.math.abs

data class SubnetInfo(
    val localIp: String,
    val prefixLength: Int,
    val subnetMask: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val cidrNotation: String,
    val usableHostIps: List<String>
)

data class TargetValidationResult(
    val isValid: Boolean,
    val sanitizedTarget: String = "",
    val errorMessage: String? = null
)

object NetworkValidator {

    const val MAX_LAN_SCAN_HOSTS = 254
    const val MAX_SUBNET_HOSTS = 254

    private val HOSTNAME_REGEX = Regex(
        "^(?=.{1,253}\$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\$"
    )

    private val DANGEROUS_CHARS_REGEX = Regex("[\\s/;\\\\|&`\$<>\"'()\\[\\]{}?@#%]")

    fun frequencyToBandAndChannel(freq: Int): Pair<WifiBand, Int> {
        return when {
            freq == 2484 -> WifiBand.BAND_2_4_GHZ to 14
            freq in 2412..2472 -> WifiBand.BAND_2_4_GHZ to ((freq - 2407) / 5)
            freq in 5170..5885 -> WifiBand.BAND_5_GHZ to ((freq - 5000) / 5)
            freq == 5935 -> WifiBand.BAND_6_GHZ to 2
            freq in 5955..7115 -> WifiBand.BAND_6_GHZ to ((freq - 5950) / 5)
            else -> WifiBand.UNKNOWN to 0
        }
    }

    fun calculateChannelRatings(
        band: WifiBand,
        accessPoints: List<WifiAccessPoint>,
        currentChannel: Int = 0
    ): List<ChannelRating> {
        val ratings = mutableListOf<ChannelRating>()

        when (band) {
            WifiBand.BAND_2_4_GHZ -> {
                val apOn24 = accessPoints.filter { it.band == WifiBand.BAND_2_4_GHZ }
                val hasCh14 = apOn24.any { it.channel == 14 } || currentChannel == 14
                val hasCh12Or13 = apOn24.any { it.channel in 12..13 } || currentChannel in 12..13
                val channels24 = if (hasCh14) (1..14).toList() else (1..13).toList()

                val primaryNonOverlapping = if (hasCh12Or13) setOf(1, 6, 11, 13) else setOf(1, 6, 11)

                for (ch in channels24) {
                    val freq = if (ch == 14) 2484 else 2407 + (ch * 5)
                    val coChannel = apOn24.count { it.channel == ch }
                    // In 2.4 GHz (5 MHz channel spacing, 20 MHz OFDM width), channels within ±3 (< 20 MHz spacing) overlap
                    val adjacent = apOn24.count { other ->
                        val maxOverlapDistance = if (other.channelWidth >= 40) 6 else 3
                        other.channel != ch && abs(other.channel - ch) <= maxOverlapDistance
                    }

                    var interferenceScore = 0.0
                    for (ap in apOn24) {
                        val chDiff = abs(ap.channel - ch)
                        val maxOverlap = if (ap.channelWidth >= 40) 6 else 3
                        if (chDiff <= maxOverlap) {
                            val rssiWeight = ((ap.rssi + 100).coerceIn(5, 70)) / 35.0
                            val overlapFactor = if (chDiff == 0) 2.0 else (1.0 - (chDiff.toDouble() / (maxOverlap + 1)))
                            interferenceScore += rssiWeight * overlapFactor
                        }
                    }

                    val isNonOverlappingStandard = ch in primaryNonOverlapping
                    val effectiveLoad = interferenceScore + if (isNonOverlappingStandard) 0.0 else 0.75

                    val stars = when {
                        effectiveLoad <= 0.2 -> 5
                        effectiveLoad <= 1.8 -> 4
                        effectiveLoad <= 3.8 -> 3
                        effectiveLoad <= 6.0 -> 2
                        else -> 1
                    }

                    val statusLabel = when (stars) {
                        5 -> if (isNonOverlappingStandard) "Sangat Direkomendasikan" else "Bersih"
                        4 -> "Bagus & Minim Gangguan"
                        3 -> "Cukup Ada Interferensi"
                        2 -> "Padat & Tumpang Tindih"
                        else -> "Sangat Padat (Hindari)"
                    }

                    ratings.add(
                        ChannelRating(
                            channel = ch,
                            band = band,
                            frequency = freq,
                            stars = stars,
                            apCount = coChannel,
                            coChannelApCount = coChannel,
                            adjacentApCount = adjacent,
                            overlappingApCount = coChannel + adjacent,
                            statusLabel = statusLabel,
                            isCurrentChannel = ch == currentChannel,
                            isRecommendedNonOverlapping = isNonOverlappingStandard,
                            isNonOverlapping = isNonOverlappingStandard,
                            interferenceScore = interferenceScore
                        )
                    )
                }
            }

            WifiBand.BAND_5_GHZ -> {
                val baseChannels5G = listOf(
                    36, 40, 44, 48, 52, 56, 60, 64,
                    100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144,
                    149, 153, 157, 161, 165
                )
                val apOn5G = accessPoints.filter { it.band == WifiBand.BAND_5_GHZ }
                val extraDetected = apOn5G.map { it.channel }.filter { it > 0 && it !in baseChannels5G }
                val channels5G = (baseChannels5G + extraDetected).distinct().sorted()

                for (ch in channels5G) {
                    val freq = 5000 + (ch * 5)
                    val coChannel = apOn5G.count { it.channel == ch }
                    val adjacent = apOn5G.count { other ->
                        val halfSpanMhz = (other.channelWidth / 2) + 10
                        other.channel != ch && abs(other.frequency - freq) <= halfSpanMhz
                    }

                    val totalImpact = (coChannel * 2) + adjacent
                    val stars = when {
                        totalImpact == 0 -> 5
                        totalImpact == 1 -> 4
                        totalImpact <= 3 -> 3
                        totalImpact <= 5 -> 2
                        else -> 1
                    }

                    val statusLabel = when (stars) {
                        5 -> "Optimal & Bebas Gangguan"
                        4 -> "Bagus"
                        3 -> "Sedikit Penggunaan"
                        2 -> "Cukup Padat"
                        else -> "Padat"
                    }

                    val isNonOverlapping = ch in listOf(36, 40, 44, 48, 149, 153, 157, 161)
                    ratings.add(
                        ChannelRating(
                            channel = ch,
                            band = band,
                            frequency = freq,
                            stars = stars,
                            apCount = coChannel,
                            coChannelApCount = coChannel,
                            adjacentApCount = adjacent,
                            overlappingApCount = coChannel + adjacent,
                            statusLabel = statusLabel,
                            isCurrentChannel = ch == currentChannel,
                            isRecommendedNonOverlapping = isNonOverlapping,
                            isNonOverlapping = isNonOverlapping,
                            interferenceScore = totalImpact.toDouble()
                        )
                    )
                }
            }

            WifiBand.BAND_6_GHZ -> {
                val pscChannels6G = listOf(5, 21, 37, 53, 69, 85, 101, 117, 133, 149, 165, 181, 197, 213, 229)
                val apOn6G = accessPoints.filter { it.band == WifiBand.BAND_6_GHZ }
                val extraDetected = apOn6G.map { it.channel }.filter { it > 0 && it !in pscChannels6G }
                val channels6G = (pscChannels6G + extraDetected).distinct().sorted()

                for (ch in channels6G) {
                    val freq = if (ch == 2) 5935 else 5950 + (ch * 5)
                    val coChannel = apOn6G.count { it.channel == ch }
                    val adjacent = apOn6G.count { other ->
                        val halfSpanMhz = (other.channelWidth / 2) + 10
                        other.channel != ch && abs(other.frequency - freq) <= halfSpanMhz
                    }

                    val totalImpact = (coChannel * 2) + adjacent
                    val stars = when {
                        totalImpact == 0 -> 5
                        totalImpact == 1 -> 4
                        totalImpact <= 3 -> 3
                        totalImpact <= 5 -> 2
                        else -> 1
                    }

                    val statusLabel = when (stars) {
                        5 -> "PSC 6 GHz Optimal & Bersih"
                        4 -> "Bagus"
                        3 -> "Terpakai Sebagian"
                        2 -> "Cukup Padat"
                        else -> "Padat"
                    }

                    val isPsc = ch in pscChannels6G
                    ratings.add(
                        ChannelRating(
                            channel = ch,
                            band = band,
                            frequency = freq,
                            stars = stars,
                            apCount = coChannel,
                            coChannelApCount = coChannel,
                            adjacentApCount = adjacent,
                            overlappingApCount = coChannel + adjacent,
                            statusLabel = statusLabel,
                            isCurrentChannel = ch == currentChannel,
                            isRecommendedNonOverlapping = isPsc,
                            isNonOverlapping = isPsc,
                            interferenceScore = totalImpact.toDouble()
                        )
                    )
                }
            }

            WifiBand.UNKNOWN -> Unit
        }

        return ratings
    }

    fun selectBestRecommendedChannel(ratings: List<ChannelRating>): ChannelRating? {
        if (ratings.isEmpty()) return null
        return ratings.sortedWith(
            compareByDescending<ChannelRating> { it.stars }
                .thenByDescending { it.isNonOverlapping || it.isRecommendedNonOverlapping }
                .thenBy { it.overlappingApCount }
                .thenBy { it.interferenceScore }
                .thenBy { it.channel }
        ).firstOrNull()
    }

    fun parseIpv4Octets(ip: String): IntArray? {
        val trimmed = ip.trim()
        val parts = trimmed.split(".")
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0..3) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            if (part.length > 1 && part.startsWith("0")) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets[i] = value
        }
        return octets
    }

    fun isValidIpv4(ip: String): Boolean = parseIpv4Octets(ip) != null

    fun ipv4ToInt(ip: String): Int? {
        val o = parseIpv4Octets(ip) ?: return null
        return (o[0] shl 24) or (o[1] shl 16) or (o[2] shl 8) or o[3]
    }

    fun intToIpv4(value: Int): String {
        return "%d.%d.%d.%d".format(
            (value ushr 24) and 0xFF,
            (value ushr 16) and 0xFF,
            (value ushr 8) and 0xFF,
            value and 0xFF
        )
    }

    fun isLoopbackAddress(target: String): Boolean {
        val clean = target.trim().lowercase()
        if (clean == "localhost") return true
        val octets = parseIpv4Octets(clean) ?: return false
        return octets[0] == 127
    }

    fun isPrivateRfc1918Ipv4(ip: String): Boolean {
        val octets = parseIpv4Octets(ip) ?: return false
        val first = octets[0]
        val second = octets[1]
        return when {
            first == 10 -> true // 10.0.0.0/8
            first == 172 && second in 16..31 -> true // 172.16.0.0/12
            first == 192 && second == 168 -> true // 192.168.0.0/16
            else -> false
        }
    }

    fun isMulticastIpv4(ip: String): Boolean {
        val octets = parseIpv4Octets(ip) ?: return false
        return octets[0] in 224..239
    }

    fun isBroadcastOrReservedIpv4(ip: String, subnetInfo: SubnetInfo? = null): Boolean {
        val octets = parseIpv4Octets(ip) ?: return false
        if (octets[0] == 0) return true // 0.0.0.0/8
        if (octets[0] >= 240) return true // 240.0.0.0/4 & 255.255.255.255
        if (octets[3] == 255 || octets[3] == 0) {
            if (subnetInfo == null || subnetInfo.prefixLength >= 24) return true
        }
        if (subnetInfo != null) {
            val normalized = octets.joinToString(".")
            if (normalized == subnetInfo.broadcastAddress || normalized == subnetInfo.networkAddress) {
                return true
            }
        }
        return false
    }

    fun isValidHostname(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || trimmed.length > 253) return false
        if (DANGEROUS_CHARS_REGEX.containsMatchIn(trimmed)) return false
        if (trimmed.all { it.isDigit() || it == '.' }) {
            return isValidIpv4(trimmed)
        }
        return HOSTNAME_REGEX.matches(trimmed)
    }

    fun prefixLengthToSubnetMask(prefixLength: Int): String {
        if (prefixLength !in 1..32) return "Tidak tersedia"
        val maskInt = if (prefixLength == 32) -1 else (-1 shl (32 - prefixLength))
        return intToIpv4(maskInt)
    }

    fun isIpInSameSubnet(targetIp: String, localIp: String, prefixLength: Int): Boolean {
        if (prefixLength !in 1..32) return false
        val targetInt = ipv4ToInt(targetIp) ?: return false
        val localInt = ipv4ToInt(localIp) ?: return false
        val maskInt = if (prefixLength == 32) -1 else (-1 shl (32 - prefixLength))
        return (targetInt and maskInt) == (localInt and maskInt)
    }

    fun validatePortScanTarget(
        rawInput: String,
        localIp: String? = null,
        prefixLength: Int = 0
    ): TargetValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Target IP tidak boleh kosong."
            )
        }

        if (trimmed.contains("://") || trimmed.startsWith("http", ignoreCase = true)) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Skema URL (http/https) tidak diizinkan. Masukkan alamat IPv4 lokal."
            )
        }

        if (DANGEROUS_CHARS_REGEX.containsMatchIn(trimmed) || trimmed.contains(":")) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Format target tidak valid atau mengandung karakter terlarang."
            )
        }

        if (isLoopbackAddress(trimmed)) {
            val resolved = if (trimmed.equals("localhost", ignoreCase = true)) "127.0.0.1" else trimmed
            return TargetValidationResult(isValid = true, sanitizedTarget = resolved)
        }

        if (!isValidIpv4(trimmed)) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Pemindai port hanya mengizinkan alamat IPv4 lokal (RFC1918) atau localhost."
            )
        }

        if (isMulticastIpv4(trimmed)) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Alamat multicast (224.0.0.0/4) tidak diizinkan untuk pemindaian port."
            )
        }

        val subnet = if (!localIp.isNullOrBlank() && isValidIpv4(localIp) && prefixLength in 8..30) {
            calculateSubnetInfo(localIp, prefixLength)
        } else null

        if (isBroadcastOrReservedIpv4(trimmed, subnet)) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Alamat broadcast atau network address tidak diizinkan."
            )
        }

        if (!isPrivateRfc1918Ipv4(trimmed)) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Pemindaian port terhadap IP publik ditolak. Hanya diizinkan untuk jaringan privat (RFC1918) milik sendiri."
            )
        }

        if (subnet != null && !isIpInSameSubnet(trimmed, subnet.localIp, subnet.prefixLength)) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Target berada di luar subnet jaringan aktif (${subnet.cidrNotation})."
            )
        }

        return TargetValidationResult(isValid = true, sanitizedTarget = trimmed)
    }

    fun validatePingTarget(rawInput: String): TargetValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Host atau alamat IP tidak boleh kosong."
            )
        }

        if (trimmed.contains("://")) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Masukkan nama domain atau alamat IPv4 tanpa http:// atau https://."
            )
        }

        if (DANGEROUS_CHARS_REGEX.containsMatchIn(trimmed) || trimmed.contains(":")) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Host mengandung karakter tidak valid."
            )
        }

        if (isValidIpv4(trimmed)) {
            if (isMulticastIpv4(trimmed)) {
                return TargetValidationResult(
                    isValid = false,
                    errorMessage = "Ping ke alamat multicast tidak diizinkan."
                )
            }
            if (isBroadcastOrReservedIpv4(trimmed)) {
                return TargetValidationResult(
                    isValid = false,
                    errorMessage = "Ping ke alamat broadcast/reserved tidak diizinkan."
                )
            }
            return TargetValidationResult(isValid = true, sanitizedTarget = trimmed)
        }

        if (!isValidHostname(trimmed)) {
            return TargetValidationResult(
                isValid = false,
                errorMessage = "Format hostname atau alamat IPv4 tidak valid."
            )
        }

        return TargetValidationResult(isValid = true, sanitizedTarget = trimmed)
    }

    fun calculateSubnetInfo(
        localIp: String,
        prefixLength: Int,
        maxHosts: Int = MAX_LAN_SCAN_HOSTS
    ): SubnetInfo? {
        val ipInt = ipv4ToInt(localIp) ?: return null
        if (!isPrivateRfc1918Ipv4(localIp)) return null
        val validPrefix = if (prefixLength in 8..30) prefixLength else 24

        val maskInt = -1 shl (32 - validPrefix)
        val networkInt = ipInt and maskInt
        val hostBits = 32 - validPrefix
        val totalAddresses = 1L shl hostBits
        val broadcastInt = networkInt or maskInt.inv()

        val networkAddress = intToIpv4(networkInt)
        val broadcastAddress = intToIpv4(broadcastInt)
        val subnetMask = intToIpv4(maskInt)
        val cidr = "$networkAddress/$validPrefix"

        val usableHosts = mutableListOf<String>()
        val totalUsable = (totalAddresses - 2).coerceAtLeast(0L)

        if (totalUsable <= maxHosts) {
            for (offset in 1..totalUsable.toInt()) {
                val candidateInt = networkInt + offset
                if (candidateInt != broadcastInt) {
                    usableHosts.add(intToIpv4(candidateInt))
                }
            }
        } else {
            // For large subnets (e.g. /16 or /20), scan the /24 window around localIp to stay within maxHosts
            val base24Mask = -1 shl 8
            val base24Net = ipInt and base24Mask
            for (offset in 1..maxHosts.coerceAtMost(254)) {
                val candidateInt = base24Net + offset
                if (candidateInt != networkInt && candidateInt != broadcastInt) {
                    usableHosts.add(intToIpv4(candidateInt))
                }
            }
        }

        return SubnetInfo(
            localIp = localIp,
            prefixLength = validPrefix,
            subnetMask = subnetMask,
            networkAddress = networkAddress,
            broadcastAddress = broadcastAddress,
            cidrNotation = cidr,
            usableHostIps = usableHosts
        )
    }

    fun calculatePacketLossPercent(sent: Int, received: Int): Int {
        if (sent <= 0) return 0
        val clampedReceived = received.coerceIn(0, sent)
        return (((sent - clampedReceived) * 100.0) / sent.toDouble()).toInt()
    }
}
