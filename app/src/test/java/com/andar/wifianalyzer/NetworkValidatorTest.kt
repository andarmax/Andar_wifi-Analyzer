package com.andar.wifianalyzer

import com.andar.wifianalyzer.model.WifiAccessPoint
import com.andar.wifianalyzer.model.WifiBand
import com.andar.wifianalyzer.service.NetworkValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkValidatorTest {

    @Test
    fun `frequencyToBandAndChannel maps 2_4 GHz, 5 GHz, and 6 GHz accurately`() {
        // 2.4 GHz
        assertEquals(WifiBand.BAND_2_4_GHZ to 1, NetworkValidator.frequencyToBandAndChannel(2412))
        assertEquals(WifiBand.BAND_2_4_GHZ to 6, NetworkValidator.frequencyToBandAndChannel(2437))
        assertEquals(WifiBand.BAND_2_4_GHZ to 11, NetworkValidator.frequencyToBandAndChannel(2462))
        assertEquals(WifiBand.BAND_2_4_GHZ to 13, NetworkValidator.frequencyToBandAndChannel(2472))
        assertEquals(WifiBand.BAND_2_4_GHZ to 14, NetworkValidator.frequencyToBandAndChannel(2484))

        // 5 GHz
        assertEquals(WifiBand.BAND_5_GHZ to 36, NetworkValidator.frequencyToBandAndChannel(5180))
        assertEquals(WifiBand.BAND_5_GHZ to 44, NetworkValidator.frequencyToBandAndChannel(5220))
        assertEquals(WifiBand.BAND_5_GHZ to 149, NetworkValidator.frequencyToBandAndChannel(5745))
        assertEquals(WifiBand.BAND_5_GHZ to 165, NetworkValidator.frequencyToBandAndChannel(5825))

        // 6 GHz (WiFi 6E / WiFi 7)
        assertEquals(WifiBand.BAND_6_GHZ to 1, NetworkValidator.frequencyToBandAndChannel(5955))
        assertEquals(WifiBand.BAND_6_GHZ to 5, NetworkValidator.frequencyToBandAndChannel(5975))
        assertEquals(WifiBand.BAND_6_GHZ to 37, NetworkValidator.frequencyToBandAndChannel(6135))
        assertEquals(WifiBand.BAND_6_GHZ to 233, NetworkValidator.frequencyToBandAndChannel(7115))

        // Invalid / 0 frequency
        assertEquals(WifiBand.UNKNOWN to 0, NetworkValidator.frequencyToBandAndChannel(0))
    }

    @Test
    fun `calculateChannelRatings accounts for 2_4 GHz adjacent channel overlap and prioritizes non-overlapping`() {
        val congestedCh1Aps = listOf(
            WifiAccessPoint(
                ssid = "Office_AP_1",
                bssid = "AA:BB:CC:DD:EE:01",
                rssi = -45,
                frequency = 2412,
                channel = 1,
                band = WifiBand.BAND_2_4_GHZ,
                channelWidth = 20,
                security = "WPA2-PSK",
                standard = "WiFi 6 (802.11ax)"
            ),
            WifiAccessPoint(
                ssid = "Office_AP_2",
                bssid = "AA:BB:CC:DD:EE:02",
                rssi = -50,
                frequency = 2417,
                channel = 2,
                band = WifiBand.BAND_2_4_GHZ,
                channelWidth = 20,
                security = "WPA2-PSK",
                standard = "WiFi 6 (802.11ax)"
            )
        )

        val ratings24 = NetworkValidator.calculateChannelRatings(WifiBand.BAND_2_4_GHZ, congestedCh1Aps)
        val ch1 = ratings24.first { it.channel == 1 }
        val ch3 = ratings24.first { it.channel == 3 }
        val ch6 = ratings24.first { it.channel == 6 }
        val ch11 = ratings24.first { it.channel == 11 }

        // Channel 3 is within 4 channels of Ch 1 and Ch 2, so it suffers adjacent-channel overlap
        assertTrue(ch3.overlappingApCount >= 2)
        assertTrue(ch1.interferenceScore > ch6.interferenceScore)
        // Channel 6 and 11 have zero overlap with Ch 1 and Ch 2
        assertEquals(0, ch6.overlappingApCount)
        assertEquals(5, ch6.stars)
        assertEquals(5, ch11.stars)
        assertTrue(ch11.isNonOverlapping)

        // 6 GHz ratings support
        val ratings6 = NetworkValidator.calculateChannelRatings(WifiBand.BAND_6_GHZ, emptyList())
        assertTrue(ratings6.isNotEmpty())
        assertTrue(ratings6.all { it.band == WifiBand.BAND_6_GHZ && it.stars == 5 })
    }

    @Test
    fun `validatePortScanTarget accepts only localhost and private RFC1918 IPs within subnet`() {
        // Allowed private and loopback addresses
        assertTrue(NetworkValidator.validatePortScanTarget("192.168.1.1").isValid)
        assertTrue(NetworkValidator.validatePortScanTarget("10.0.0.1").isValid)
        assertTrue(NetworkValidator.validatePortScanTarget("172.16.5.10").isValid)
        assertTrue(NetworkValidator.validatePortScanTarget("127.0.0.1").isValid)
        assertTrue(NetworkValidator.validatePortScanTarget("localhost").isValid)

        // Rejected public IPs
        val publicGoogle = NetworkValidator.validatePortScanTarget("8.8.8.8")
        assertFalse(publicGoogle.isValid)
        val publicCloudflare = NetworkValidator.validatePortScanTarget("1.1.1.1")
        assertFalse(publicCloudflare.isValid)

        // Rejected URLs, empty strings, broadcast, multicast, dangerous characters
        assertFalse(NetworkValidator.validatePortScanTarget("").isValid)
        assertFalse(NetworkValidator.validatePortScanTarget("http://192.168.1.1").isValid)
        assertFalse(NetworkValidator.validatePortScanTarget("https://example.com").isValid)
        assertFalse(NetworkValidator.validatePortScanTarget("255.255.255.255").isValid)
        assertFalse(NetworkValidator.validatePortScanTarget("224.0.0.1").isValid)
        assertFalse(NetworkValidator.validatePortScanTarget("192.168.1.1; rm -rf").isValid)

        // Subnet boundary enforcement when localIp and prefixLength are active
        val sameSubnet = NetworkValidator.validatePortScanTarget("192.168.1.50", localIp = "192.168.1.10", prefixLength = 24)
        assertTrue(sameSubnet.isValid)

        val differentSubnet = NetworkValidator.validatePortScanTarget("192.168.2.50", localIp = "192.168.1.10", prefixLength = 24)
        assertFalse(differentSubnet.isValid)

        val subnetBroadcast = NetworkValidator.validatePortScanTarget("192.168.1.255", localIp = "192.168.1.10", prefixLength = 24)
        assertFalse(subnetBroadcast.isValid)
    }

    @Test
    fun `validatePingTarget validates hostnames and rejects URLs or injection characters`() {
        assertTrue(NetworkValidator.validatePingTarget("8.8.8.8").isValid)
        assertTrue(NetworkValidator.validatePingTarget("one.one.one.one").isValid)
        assertTrue(NetworkValidator.validatePingTarget("localhost").isValid)

        assertFalse(NetworkValidator.validatePingTarget("").isValid)
        assertFalse(NetworkValidator.validatePingTarget("https://8.8.8.8").isValid)
        assertFalse(NetworkValidator.validatePingTarget("8.8.8.8 && ls").isValid)
        assertFalse(NetworkValidator.validatePingTarget("255.255.255.255").isValid)
    }

    @Test
    fun `calculateSubnetInfo computes accurate host ranges for different prefix lengths`() {
        val subnet24 = NetworkValidator.calculateSubnetInfo("192.168.10.15", 24)
        assertNotNull(subnet24)
        assertEquals(254, subnet24!!.usableHostIps.size)
        assertEquals("192.168.10.1", subnet24.usableHostIps.first())
        assertEquals("192.168.10.254", subnet24.usableHostIps.last())

        val subnet25 = NetworkValidator.calculateSubnetInfo("192.168.10.15", 25)
        assertNotNull(subnet25)
        assertEquals(126, subnet25!!.usableHostIps.size)
        assertEquals("192.168.10.1", subnet25.usableHostIps.first())
        assertEquals("192.168.10.126", subnet25.usableHostIps.last())

        val subnet30 = NetworkValidator.calculateSubnetInfo("10.0.0.1", 30)
        assertNotNull(subnet30)
        assertEquals(2, subnet30!!.usableHostIps.size)
        assertEquals(listOf("10.0.0.1", "10.0.0.2"), subnet30.usableHostIps)

        // Large /16 subnet is safely capped at MAX_SUBNET_HOSTS (254) centered around local IP
        val subnet16 = NetworkValidator.calculateSubnetInfo("172.16.5.20", 16)
        assertNotNull(subnet16)
        assertEquals(NetworkValidator.MAX_SUBNET_HOSTS, subnet16!!.usableHostIps.size)
        assertTrue(subnet16.usableHostIps.contains("172.16.5.20"))

        assertNull(NetworkValidator.calculateSubnetInfo("invalid_ip", 24))
    }

    @Test
    fun `calculatePacketLossPercent computes exact packet loss without fake zeroing`() {
        assertEquals(0, NetworkValidator.calculatePacketLossPercent(sent = 5, received = 5))
        assertEquals(40, NetworkValidator.calculatePacketLossPercent(sent = 5, received = 3))
        assertEquals(100, NetworkValidator.calculatePacketLossPercent(sent = 5, received = 0))
    }
}
