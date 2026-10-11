package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.WifiBand
import com.example.service.WifiScannerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Andar WiFi Analyzer", appName)
  }

  @Test
  fun `verify channel frequency mapping`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = WifiScannerEngine(context)

    assertEquals(1, engine.freqToChannel(2412))
    assertEquals(6, engine.freqToChannel(2437))
    assertEquals(11, engine.freqToChannel(2462))
    assertEquals(14, engine.freqToChannel(2484))
    assertEquals(36, engine.freqToChannel(5180))
    assertEquals(149, engine.freqToChannel(5745))

    assertEquals(WifiBand.BAND_2_4_GHZ, engine.freqToBand(2412))
    assertEquals(WifiBand.BAND_5_GHZ, engine.freqToBand(5180))

    val score = engine.calculateSignalPercent(-50)
    assertTrue("Signal percent should be high for -50dBm", score >= 90)
  }

  @Test
  fun `verify default DNS servers list`() {
    val diagnosticsEngine = com.example.service.NetworkDiagnosticsEngine()
    val dnsList = diagnosticsEngine.getDefaultDnsList("192.168.1.1")

    assertTrue("DNS list should contain popular DNS servers", dnsList.size >= 6)
    assertTrue("Should include Cloudflare 1.1.1.1", dnsList.any { it.primaryIp == "1.1.1.1" })
    assertTrue("Should include Google 8.8.8.8", dnsList.any { it.primaryIp == "8.8.8.8" })
    assertTrue("Should include Quad9 9.9.9.9", dnsList.any { it.primaryIp == "9.9.9.9" })
  }
}
