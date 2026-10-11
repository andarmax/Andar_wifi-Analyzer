package com.andar.wifianalyzer

import com.andar.wifianalyzer.model.DiagnosticTaskStatus
import com.andar.wifianalyzer.model.DnsPingStatus
import com.andar.wifianalyzer.model.DnsServerPingItem
import com.andar.wifianalyzer.model.PingStatus
import com.andar.wifianalyzer.service.NetworkDiagnosticsEngine
import com.andar.wifianalyzer.service.ProbeResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticsEngineTest {

    @Test
    fun `executePing never increments received on failure and reports 100 percent packet loss when unreachable`() = runTest {
        val failingEngine = NetworkDiagnosticsEngine(
            hostProbeOverride = { _, _, _ ->
                ProbeResult(
                    success = false,
                    durationMs = 0f,
                    methodUsed = "TCP",
                    errorType = DnsPingStatus.TIMEOUT,
                    errorDetail = "SocketTimeoutException"
                )
            }
        )

        val emissions = failingEngine.executePing("8.8.8.8", count = 3).toList()
        val finalResult = emissions.last()

        assertEquals(3, finalResult.sent)
        assertEquals(0, finalResult.received)
        assertEquals(100, finalResult.lossPercent)
        assertEquals(0f, finalResult.minMs, 0.01f)
        assertEquals(0f, finalResult.avgMs, 0.01f)
        assertEquals(PingStatus.FAILED, finalResult.status)
    }

    @Test
    fun `executePing calculates accurate min avg max and packet loss for partial responses`() = runTest {
        var callIndex = 0
        val mixedEngine = NetworkDiagnosticsEngine(
            hostProbeOverride = { _, _, _ ->
                callIndex++
                when (callIndex) {
                    1 -> ProbeResult(success = true, durationMs = 10f, methodUsed = "TCP Latency (port 443)")
                    2 -> ProbeResult(success = false, durationMs = 0f, errorType = DnsPingStatus.TIMEOUT)
                    3 -> ProbeResult(success = true, durationMs = 30f, methodUsed = "TCP Latency (port 443)")
                    else -> ProbeResult(success = false, durationMs = 0f, errorType = DnsPingStatus.TIMEOUT)
                }
            }
        )

        val finalResult = mixedEngine.executePing("1.1.1.1", count = 4).toList().last()
        assertEquals(4, finalResult.sent)
        assertEquals(2, finalResult.received)
        assertEquals(50, finalResult.lossPercent)
        assertEquals(10f, finalResult.minMs, 0.01f)
        assertEquals(20f, finalResult.avgMs, 0.01f)
        assertEquals(30f, finalResult.maxMs, 0.01f)
        assertEquals(PingStatus.COMPLETED, finalResult.status)
        assertTrue(finalResult.methodLabel.contains("TCP Latency"))
    }

    @Test
    fun `pingDnsServer distinguishes COMPLETED, TIMEOUT, UNREACHABLE, and OFFLINE states`() = runTest {
        val sampleDns = DnsServerPingItem(
            id = "cloudflare",
            name = "Cloudflare DNS",
            primaryIp = "1.1.1.1",
            description = "Test DNS"
        )

        val successEngine = NetworkDiagnosticsEngine(
            hostProbeOverride = { _, _, _ ->
                ProbeResult(success = true, durationMs = 14.5f, errorType = DnsPingStatus.COMPLETED)
            }
        )
        val completed = successEngine.pingDnsServer(sampleDns, probes = 2)
        assertEquals(DnsPingStatus.COMPLETED, completed.status)
        assertEquals(14.5f, completed.latencyMs, 0.2f)

        val timeoutEngine = NetworkDiagnosticsEngine(
            hostProbeOverride = { _, _, _ ->
                ProbeResult(success = false, errorType = DnsPingStatus.TIMEOUT, errorDetail = "Timeout")
            }
        )
        val timedOut = timeoutEngine.pingDnsServer(sampleDns, probes = 2)
        assertEquals(DnsPingStatus.TIMEOUT, timedOut.status)
        assertEquals(-1f, timedOut.latencyMs, 0.01f)

        val unreachableEngine = NetworkDiagnosticsEngine(
            hostProbeOverride = { _, _, _ ->
                ProbeResult(success = false, errorType = DnsPingStatus.UNREACHABLE, errorDetail = "Unreachable")
            }
        )
        val unreachable = unreachableEngine.pingDnsServer(sampleDns, probes = 2)
        assertEquals(DnsPingStatus.UNREACHABLE, unreachable.status)

        val offlineEngine = NetworkDiagnosticsEngine(
            hostProbeOverride = { _, _, _ ->
                ProbeResult(success = false, errorType = DnsPingStatus.OFFLINE, errorDetail = "Offline")
            }
        )
        val offline = offlineEngine.pingDnsServer(sampleDns, probes = 2)
        assertEquals(DnsPingStatus.OFFLINE, offline.status)
    }

    @Test
    fun `scanPortsWithValidation rejects public IP and scans valid private IP`() = runTest {
        val engine = NetworkDiagnosticsEngine(
            hostProbeOverride = { _, port, _ ->
                ProbeResult(success = (port == 80 || port == 443), durationMs = 5f)
            }
        )

        val rejectedPublic = engine.scanPortsWithValidation("8.8.8.8")
        assertEquals(DiagnosticTaskStatus.ERROR, rejectedPublic.status)
        assertTrue(rejectedPublic.results.isEmpty())

        val validPrivate = engine.scanPortsWithValidation("192.168.1.1", localIp = "192.168.1.10", prefixLength = 24)
        assertEquals(DiagnosticTaskStatus.SUCCESS, validPrivate.status)
        assertEquals(2, validPrivate.results.count { it.isOpen })
    }

    @Test
    fun `scanLocalSubnetFlow returns only responding hosts and never injects fictional devices`() = runTest {
        val engine = NetworkDiagnosticsEngine(
            hostProbeOverride = { ip, _, _ ->
                ProbeResult(
                    success = (ip == "192.168.1.1" || ip == "192.168.1.2"),
                    durationMs = 4f,
                    methodUsed = "TCP Port 80"
                )
            }
        )

        val states = engine.scanLocalSubnetFlow(
            localIp = "192.168.1.1",
            prefixLength = 30, // Usable hosts: 192.168.1.1 and 192.168.1.2
            gatewayIp = "192.168.1.1"
        ).toList()

        val finalState = states.last()
        assertEquals(DiagnosticTaskStatus.SUCCESS, finalState.status)
        assertEquals(2, finalState.devices.size)
        assertEquals(listOf("192.168.1.1", "192.168.1.2"), finalState.devices.map { it.ip })
        assertEquals("Perangkat Ini (Android)", finalState.devices.first { it.ip == "192.168.1.1" }.hostname)
        assertEquals("Tidak tersedia", finalState.devices.first { it.ip == "192.168.1.2" }.hostname)
    }
}
