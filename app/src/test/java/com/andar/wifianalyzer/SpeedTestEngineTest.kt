package com.andar.wifianalyzer

import com.andar.wifianalyzer.data.SpeedTestDao
import com.andar.wifianalyzer.data.SpeedTestEntity
import com.andar.wifianalyzer.model.SpeedTestErrorType
import com.andar.wifianalyzer.model.SpeedTestPhase
import com.andar.wifianalyzer.service.SpeedTestEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private class FakeSpeedTestDao : SpeedTestDao {
    val records = mutableListOf<SpeedTestEntity>()
    private val flow = MutableStateFlow<List<SpeedTestEntity>>(emptyList())

    override fun getAllHistory(): Flow<List<SpeedTestEntity>> = flow

    override suspend fun insertRecord(record: SpeedTestEntity): Long {
        records.add(record)
        flow.value = records.toList()
        return records.size.toLong()
    }

    override suspend fun clearHistory() {
        records.clear()
        flow.value = emptyList()
    }
}

class SpeedTestEngineTest {

    @Test
    fun `startSpeedTest enters ERROR state and does NOT save to Room when offline or DNS fails`() = runTest {
        val dao = FakeSpeedTestDao()
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw UnknownHostException("No internet connection")
            })
            .build()

        val engine = SpeedTestEngine(dao, failingClient)
        engine.startSpeedTest("MyHomeWiFi")

        val state = engine.state.value
        assertEquals(SpeedTestPhase.ERROR, state.phase)
        assertEquals(SpeedTestErrorType.DNS_FAILURE, state.errorType)
        assertEquals(0.0, state.downloadMbps, 0.001)
        assertEquals(0.0, state.uploadMbps, 0.001)
        assertTrue("Failed speed test must never be saved to Room", dao.records.isEmpty())
    }

    @Test
    fun `startSpeedTest enters ERROR state on upload failure and does NOT count failed upload bytes`() = runTest {
        val dao = FakeSpeedTestDao()
        val payload = ByteArray(128 * 1024) { 0x42 }
        val uploadFailingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("__up")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val engine = SpeedTestEngine(dao, uploadFailingClient)
        engine.startSpeedTest("MyHomeWiFi")

        val state = engine.state.value
        assertEquals(SpeedTestPhase.ERROR, state.phase)
        assertEquals(SpeedTestErrorType.HTTP_ERROR, state.errorType)
        assertEquals(0.0, state.uploadMbps, 0.001)
        assertTrue("Incomplete speed test must not be saved to Room", dao.records.isEmpty())
    }

    @Test
    fun `startSpeedTest completes and saves to Room only when ping, download, and upload succeed`() = runTest {
        val dao = FakeSpeedTestDao()
        val downloadPayload = ByteArray(256 * 1024) { 0x33 }
        val successClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(downloadPayload.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            })
            .build()

        val engine = SpeedTestEngine(dao, successClient)
        engine.startSpeedTest("LabWiFi_5G")

        val state = engine.state.value
        assertEquals(SpeedTestPhase.FINISHED, state.phase)
        assertEquals(SpeedTestErrorType.NONE, state.errorType)
        assertTrue(state.downloadMbps > 0.0)
        assertTrue(state.uploadMbps > 0.0)
        assertEquals(1, dao.records.size)
        assertEquals("LabWiFi_5G", dao.records.first().ssid)
    }

    @Test
    fun `startSpeedTest classifies SocketTimeoutException as TIMEOUT`() = runTest {
        val dao = FakeSpeedTestDao()
        val timeoutClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw SocketTimeoutException("Read timed out")
            })
            .build()

        val engine = SpeedTestEngine(dao, timeoutClient)
        engine.startSpeedTest("LabWiFi_5G")

        val state = engine.state.value
        assertEquals(SpeedTestPhase.ERROR, state.phase)
        assertEquals(SpeedTestErrorType.TIMEOUT, state.errorType)
        assertTrue(dao.records.isEmpty())
    }
}
