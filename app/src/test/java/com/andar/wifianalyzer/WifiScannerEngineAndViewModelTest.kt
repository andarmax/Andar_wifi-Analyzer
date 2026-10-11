package com.andar.wifianalyzer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.andar.wifianalyzer.model.ChannelRating
import com.andar.wifianalyzer.model.ConnectedWifiInfo
import com.andar.wifianalyzer.model.PublicIpStatus
import com.andar.wifianalyzer.model.WifiBand
import com.andar.wifianalyzer.model.WifiScanState
import com.andar.wifianalyzer.model.WifiScanStatus
import com.andar.wifianalyzer.service.NetworkDiagnosticsEngine
import com.andar.wifianalyzer.service.PublicIpLookupResult
import com.andar.wifianalyzer.service.WifiScannerEngine
import com.andar.wifianalyzer.ui.MainNavTab
import com.andar.wifianalyzer.ui.WifiViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WifiScannerEngineAndViewModelTest {

    @Test
    fun `WifiScannerEngine returns truthful disconnected state and empty scan list when WiFi is inactive`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scanner = WifiScannerEngine(context)

        val connectedInfo = scanner.getConnectedWifiInfo()
        assertFalse(connectedInfo.isConnected)
        assertEquals("Belum diperiksa", connectedInfo.publicIp)
        assertEquals("Tidak tersedia", connectedInfo.ispName)

        val cachedAps = scanner.scanNearbyNetworks()
        assertTrue(cachedAps.isEmpty())
    }

    @Test
    fun `WifiScannerEngine computes accurate signal percentage and FSPL distance from RSSI and frequency`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scanner = WifiScannerEngine(context)

        // Signal percentage boundaries and linear mapping
        assertEquals(0, scanner.calculateSignalPercent(-100))
        assertEquals(0, scanner.calculateSignalPercent(-115))
        assertEquals(50, scanner.calculateSignalPercent(-75))
        assertEquals(100, scanner.calculateSignalPercent(-50))
        assertEquals(100, scanner.calculateSignalPercent(-35))

        // Distance calculation (Free Space Path Loss)
        val distClose = scanner.calculateDistance(rssi = -50, freqMhz = 2412)
        val distFar = scanner.calculateDistance(rssi = -80, freqMhz = 2412)
        assertTrue(distClose > 0.0)
        assertTrue(distFar > distClose)
        assertEquals(0.0, scanner.calculateDistance(rssi = -127, freqMhz = 2412), 0.001)
        assertEquals(0.0, scanner.calculateDistance(rssi = -60, freqMhz = 0), 0.001)
    }

    @Test
    fun `WifiViewModel initializes with truthful empty signal history and supports tab navigation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WifiViewModel(app, autoStartMonitoring = false)

        // Signal history must not be pre-populated with fake dBm numbers
        assertTrue(viewModel.signalHistory.value.isEmpty())

        // Default tab is DASHBOARD and can switch to all tabs
        assertEquals(MainNavTab.DASHBOARD, viewModel.currentTab.value)
        viewModel.selectTab(MainNavTab.RADAR)
        assertEquals(MainNavTab.RADAR, viewModel.currentTab.value)
        viewModel.selectTab(MainNavTab.CHANNELS)
        assertEquals(MainNavTab.CHANNELS, viewModel.currentTab.value)
        viewModel.selectTab(MainNavTab.SPEED_TEST)
        assertEquals(MainNavTab.SPEED_TEST, viewModel.currentTab.value)
        viewModel.selectTab(MainNavTab.TOOLS)
        assertEquals(MainNavTab.TOOLS, viewModel.currentTab.value)

        // Band selection supports 2.4 GHz, 5 GHz, and 6 GHz
        viewModel.setSelectedBand(WifiBand.BAND_6_GHZ)
        assertEquals(WifiBand.BAND_6_GHZ, viewModel.selectedBand.value)

        // Best channel recommendation prioritizes non-overlapping channels
        val sampleRatings = listOf(
            ChannelRating(channel = 2, band = WifiBand.BAND_2_4_GHZ, apCount = 0, overlappingApCount = 0, stars = 5, statusLabel = "Sangat Baik", isNonOverlapping = false, interferenceScore = 0.0),
            ChannelRating(channel = 6, band = WifiBand.BAND_2_4_GHZ, apCount = 0, overlappingApCount = 0, stars = 5, statusLabel = "Sangat Baik (Non-Overlapping)", isNonOverlapping = true, interferenceScore = 0.0)
        )
        val recommended = viewModel.getBestChannelRecommendation(sampleRatings)
        assertNotNull(recommended)
        assertEquals(6, recommended?.channel)
    }

    @Test
    fun `WifiViewModel preserves publicIp and ispName during regular WiFi refresh on same SSID`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var publicIpFetchCount = 0

        val fakeScanner = object : WifiScannerEngine(app) {
            override fun getConnectedWifiInfo(): ConnectedWifiInfo {
                return ConnectedWifiInfo(
                    ssid = "LabTestNet_5G",
                    bssid = "AA:BB:CC:11:22:33",
                    rssi = -58,
                    signalPercent = 84,
                    frequency = 5180,
                    channel = 36,
                    band = WifiBand.BAND_5_GHZ,
                    ipAddress = "192.168.10.25",
                    gateway = "192.168.10.1",
                    subnetMask = "255.255.255.0",
                    prefixLength = 24,
                    isConnected = true
                )
            }

            override suspend fun scanAccessPointsWithState(): WifiScanState {
                return WifiScanState(status = WifiScanStatus.EMPTY, accessPoints = emptyList())
            }
        }

        val fakeDiagnostics = object : NetworkDiagnosticsEngine() {
            override suspend fun fetchPublicIpAndIsp(): PublicIpLookupResult {
                publicIpFetchCount++
                return PublicIpLookupResult(
                    ip = "203.0.113.45",
                    isp = "AS64500 ExampleLab ISP",
                    status = PublicIpStatus.SUCCESS
                )
            }
        }

        val viewModel = WifiViewModel(
            application = app,
            customScannerEngine = fakeScanner,
            customDiagnosticsEngine = fakeDiagnostics,
            autoStartMonitoring = false
        )

        // First refresh with forcePublicIpRefresh = true fetches and populates publicIp & ispName
        viewModel.refreshAll(forcePublicIpRefresh = true)
        ShadowLooper.idleMainLooper()
        Thread.sleep(100)
        ShadowLooper.idleMainLooper()

        assertEquals("203.0.113.45", viewModel.connectedInfo.value.publicIp)
        assertEquals("AS64500 ExampleLab ISP", viewModel.connectedInfo.value.ispName)
        assertEquals(PublicIpStatus.SUCCESS, viewModel.connectedInfo.value.publicIpStatus)
        assertEquals(1, publicIpFetchCount)

        // Subsequent regular WiFi refresh (forcePublicIpRefresh = false) preserves publicIp without overwriting or re-fetching
        viewModel.refreshAll(forcePublicIpRefresh = false)
        ShadowLooper.idleMainLooper()
        Thread.sleep(100)
        ShadowLooper.idleMainLooper()

        assertEquals("203.0.113.45", viewModel.connectedInfo.value.publicIp)
        assertEquals("AS64500 ExampleLab ISP", viewModel.connectedInfo.value.ispName)
        assertEquals(1, publicIpFetchCount)
    }
}
