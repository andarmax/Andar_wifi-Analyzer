package com.andar.wifianalyzer.service

import com.andar.wifianalyzer.data.SpeedTestDao
import com.andar.wifianalyzer.data.SpeedTestEntity
import com.andar.wifianalyzer.model.SpeedTestErrorType
import com.andar.wifianalyzer.model.SpeedTestPhase
import com.andar.wifianalyzer.model.SpeedTestState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class SpeedTestEngine(
    private val speedTestDao: SpeedTestDao,
    private val client: OkHttpClient = defaultOkHttpClient()
) {

    companion object {
        const val USER_AGENT = "AndarWiFiAnalyzer/1.0 (Android; NetworkDiagnostics)"
        private const val MAX_DOWNLOAD_DURATION_SEC = 6.0
        private const val MAX_DOWNLOAD_BYTES = 25L * 1024L * 1024L

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }

    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    @Volatile
    private var activeCall: Call? = null

    private val pingEndpoints = listOf(
        "https://speed.cloudflare.com/__down?bytes=0",
        "https://1.1.1.1/cdn-cgi/trace",
        "https://www.google.com/generate_204"
    )

    private val downloadEndpoints = listOf(
        "https://speed.cloudflare.com/__down?bytes=15000000",
        "https://speed.cloudflare.com/__down?bytes=10000000",
        "https://proof.ovh.net/files/10Mb.dat"
    )

    private val uploadEndpoint = "https://speed.cloudflare.com/__up"

    suspend fun startSpeedTest(ssid: String) = withContext(Dispatchers.IO) {
        val historySamples = mutableListOf<Float>()
        var lastException: Exception? = null

        try {
            currentCoroutineContext().ensureActive()

            // PHASE 1: PING & JITTER
            _state.value = SpeedTestState(
                phase = SpeedTestPhase.PING,
                statusMessage = "Mengukur latency (HTTPS RTT) & jitter...",
                progress = 0.05f
            )

            val pingResults = mutableListOf<Double>()
            val totalProbes = 5
            for (i in 0 until totalProbes) {
                currentCoroutineContext().ensureActive()
                val url = pingEndpoints[i % pingEndpoints.size]
                val startTime = System.nanoTime()
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Cache-Control", "no-cache")
                        .get()
                        .build()

                    val call = client.newCall(req)
                    activeCall = call
                    call.execute().use { resp ->
                        resp.body?.close()
                        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
                        if (resp.isSuccessful || resp.code == 204) {
                            pingResults.add(durationMs)
                        } else {
                            lastException = IOException("HTTP ${resp.code} dari server uji latency")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                } finally {
                    activeCall = null
                }

                if (pingResults.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        progress = 0.05f + ((i + 1) * 0.03f),
                        pingMs = ((pingResults.average() * 10).toInt() / 10.0)
                    )
                }

                if (i < totalProbes - 1) {
                    delay(100)
                }
            }

            if (pingResults.isEmpty()) {
                val (errType, errMsg) = classifyNetworkError(
                    lastException,
                    "Gagal mengukur latency: tidak ada respons dari server uji."
                )
                _state.value = SpeedTestState(
                    phase = SpeedTestPhase.ERROR,
                    statusMessage = errMsg,
                    errorMessage = errMsg,
                    errorType = errType
                )
                return@withContext
            }

            val avgPing = (pingResults.average() * 10).toInt() / 10.0
            val jitter = if (pingResults.size > 1) {
                val diffs = pingResults.zipWithNext { a, b -> abs(a - b) }
                ((diffs.average() * 10).toInt() / 10.0)
            } else {
                0.0
            }

            _state.value = _state.value.copy(
                pingMs = avgPing,
                jitterMs = jitter,
                progress = 0.20f
            )

            // PHASE 2: DOWNLOAD TEST
            _state.value = _state.value.copy(
                phase = SpeedTestPhase.DOWNLOAD,
                statusMessage = "Menguji kecepatan Unduh (Download)...",
                progress = 0.25f
            )

            var finalDownloadMbps = 0.0
            var downloadBytesTransferred = 0L
            var downloadTransferDurationNs = 0L
            var downloadError: Exception? = null

            for (endpoint in downloadEndpoints) {
                currentCoroutineContext().ensureActive()
                if (downloadBytesTransferred > 0L && finalDownloadMbps > 0.0) break

                try {
                    val req = Request.Builder()
                        .url(endpoint)
                        .header("User-Agent", USER_AGENT)
                        .header("Cache-Control", "no-cache")
                        .get()
                        .build()

                    val call = client.newCall(req)
                    activeCall = call
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            downloadError = IOException("HTTP ${response.code} saat mengunduh dari $endpoint")
                            return@use
                        }

                        val body = response.body ?: run {
                            downloadError = IOException("Response body kosong dari server download")
                            return@use
                        }

                        body.byteStream().use { bodyStream ->
                            val buffer = ByteArray(64 * 1024)
                            var totalBytesRead = 0L
                            var bytesRead: Int
                            val streamStartTime = System.nanoTime()
                            var lastUpdateTime = streamStartTime

                            while (bodyStream.read(buffer).also { bytesRead = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                if (bytesRead > 0) {
                                    totalBytesRead += bytesRead
                                }
                                val now = System.nanoTime()
                                val elapsedNs = (now - streamStartTime).coerceAtLeast(1L)
                                val totalDurationSec = elapsedNs / 1_000_000_000.0

                                if (now - lastUpdateTime >= 100_000_000L && totalDurationSec >= 0.05) {
                                    val currentSpeedMbps = (totalBytesRead * 8.0) / (totalDurationSec * 1_000_000.0)
                                    val roundedMbps = (currentSpeedMbps * 10).toInt() / 10.0
                                    finalDownloadMbps = roundedMbps
                                    historySamples.add(roundedMbps.toFloat())
                                    if (historySamples.size > 40) historySamples.removeAt(0)

                                    val downloadProgress = (0.25f + ((totalDurationSec / MAX_DOWNLOAD_DURATION_SEC).toFloat() * 0.40f))
                                        .coerceAtMost(0.65f)
                                    _state.value = _state.value.copy(
                                        downloadMbps = roundedMbps,
                                        currentTransferMbps = roundedMbps,
                                        progress = downloadProgress,
                                        speedHistory = historySamples.toList()
                                    )
                                    lastUpdateTime = now
                                }

                                if (totalDurationSec >= MAX_DOWNLOAD_DURATION_SEC || totalBytesRead >= MAX_DOWNLOAD_BYTES) {
                                    break
                                }
                            }

                            val totalStreamNs = (System.nanoTime() - streamStartTime).coerceAtLeast(1L)
                            if (totalBytesRead > 0L) {
                                downloadBytesTransferred = totalBytesRead
                                downloadTransferDurationNs = totalStreamNs
                                val avgMbps = (totalBytesRead * 8.0) / ((totalStreamNs / 1_000_000_000.0) * 1_000_000.0)
                                finalDownloadMbps = ((avgMbps * 10).toInt() / 10.0).coerceAtLeast(0.1)
                                _state.value = _state.value.copy(
                                    downloadMbps = finalDownloadMbps,
                                    currentTransferMbps = finalDownloadMbps,
                                    progress = 0.65f
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    downloadError = e
                } finally {
                    activeCall = null
                }
            }

            if (downloadBytesTransferred <= 0L || finalDownloadMbps <= 0.0 || downloadTransferDurationNs <= 0L) {
                val (errType, errMsg) = classifyNetworkError(
                    downloadError,
                    "Pengujian Download gagal: tidak ada data yang berhasil diunduh."
                )
                _state.value = _state.value.copy(
                    phase = SpeedTestPhase.ERROR,
                    currentTransferMbps = 0.0,
                    statusMessage = errMsg,
                    errorMessage = errMsg,
                    errorType = errType
                )
                return@withContext
            }

            // PHASE 3: UPLOAD TEST
            _state.value = _state.value.copy(
                phase = SpeedTestPhase.UPLOAD,
                statusMessage = "Menguji kecepatan Unggah (Upload)...",
                progress = 0.68f,
                currentTransferMbps = 0.0
            )

            val uploadChunk = ByteArray(256 * 1024) { (it % 127).toByte() }
            val totalUploadIterations = 8
            var totalBytesSent = 0L
            var totalUploadActiveNs = 0L
            var successfulUploads = 0
            var uploadError: Exception? = null

            for (iter in 1..totalUploadIterations) {
                currentCoroutineContext().ensureActive()

                val reqBody = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = uploadChunk.size.toLong()
                    override fun writeTo(sink: BufferedSink) {
                        sink.write(uploadChunk)
                    }
                }

                val req = Request.Builder()
                    .url(uploadEndpoint)
                    .header("User-Agent", USER_AGENT)
                    .post(reqBody)
                    .build()

                val iterStartNs = System.nanoTime()
                try {
                    val call = client.newCall(req)
                    activeCall = call
                    call.execute().use { resp ->
                        resp.body?.close()
                        val iterElapsedNs = (System.nanoTime() - iterStartNs).coerceAtLeast(1L)
                        if (resp.isSuccessful) {
                            successfulUploads++
                            totalBytesSent += uploadChunk.size
                            totalUploadActiveNs += iterElapsedNs

                            val activeSec = totalUploadActiveNs / 1_000_000_000.0
                            if (activeSec > 0.0) {
                                val currentUpMbps = (totalBytesSent * 8.0) / (activeSec * 1_000_000.0)
                                val roundedUp = ((currentUpMbps * 10).toInt() / 10.0).coerceAtLeast(0.1)
                                historySamples.add(roundedUp.toFloat())
                                if (historySamples.size > 40) historySamples.removeAt(0)

                                val uploadProgress = (0.68f + (iter.toFloat() / totalUploadIterations * 0.30f))
                                    .coerceAtMost(0.98f)
                                _state.value = _state.value.copy(
                                    uploadMbps = roundedUp,
                                    currentTransferMbps = roundedUp,
                                    progress = uploadProgress,
                                    speedHistory = historySamples.toList()
                                )
                            }
                        } else {
                            uploadError = IOException("HTTP ${resp.code} saat mengunggah data")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    uploadError = e
                } finally {
                    activeCall = null
                }

                if (totalUploadActiveNs >= 5_000_000_000L && successfulUploads >= 2) {
                    break
                }

                if (iter < totalUploadIterations) {
                    delay(60)
                }
            }

            if (successfulUploads == 0 || totalBytesSent <= 0L || totalUploadActiveNs <= 0L) {
                val (errType, errMsg) = classifyNetworkError(
                    uploadError,
                    "Pengujian Upload gagal: tidak ada paket data yang berhasil dikirim ke server."
                )
                _state.value = _state.value.copy(
                    phase = SpeedTestPhase.ERROR,
                    currentTransferMbps = 0.0,
                    statusMessage = errMsg,
                    errorMessage = errMsg,
                    errorType = errType
                )
                return@withContext
            }

            val finalUploadMbps = _state.value.uploadMbps

            // FINISHED (ONLY WHEN ALL PHASES SUCCEEDED)
            _state.value = _state.value.copy(
                phase = SpeedTestPhase.FINISHED,
                progress = 1.0f,
                currentTransferMbps = 0.0,
                statusMessage = "Tes selesai. Hasil nyata disimpan ke riwayat.",
                errorMessage = null,
                errorType = SpeedTestErrorType.NONE
            )

            speedTestDao.insertRecord(
                SpeedTestEntity(
                    ssid = ssid.ifBlank { "WiFi Aktif" },
                    pingMs = _state.value.pingMs,
                    jitterMs = _state.value.jitterMs,
                    downloadMbps = finalDownloadMbps,
                    uploadMbps = finalUploadMbps
                )
            )
        } catch (e: CancellationException) {
            activeCall?.cancel()
            activeCall = null
            _state.value = _state.value.copy(
                phase = SpeedTestPhase.CANCELLED,
                currentTransferMbps = 0.0,
                progress = 0.0f,
                statusMessage = "Pengujian kecepatan dibatalkan oleh pengguna."
            )
            throw e
        } catch (e: Exception) {
            activeCall?.cancel()
            activeCall = null
            val (errType, errMsg) = classifyNetworkError(e, "Pengujian kecepatan gagal.")
            _state.value = _state.value.copy(
                phase = SpeedTestPhase.ERROR,
                currentTransferMbps = 0.0,
                statusMessage = errMsg,
                errorMessage = errMsg,
                errorType = errType
            )
        } finally {
            activeCall = null
        }
    }

    fun markCancelled() {
        activeCall?.cancel()
        activeCall = null
        _state.value = SpeedTestState(
            phase = SpeedTestPhase.CANCELLED,
            statusMessage = "Pengujian kecepatan dihentikan."
        )
    }

    fun reset() {
        activeCall?.cancel()
        activeCall = null
        _state.value = SpeedTestState()
    }

    private fun classifyNetworkError(e: Exception?, fallbackMsg: String): Pair<SpeedTestErrorType, String> {
        return when (e) {
            is UnknownHostException -> SpeedTestErrorType.DNS_FAILURE to
                "Gagal menghubungi server uji (DNS tidak dapat me-resolve domain / tidak ada koneksi internet)."
            is SocketTimeoutException -> SpeedTestErrorType.TIMEOUT to
                "Waktu koneksi habis (Connection/Read Timeout) saat menghubungi server uji kecepatan."
            is ConnectException -> SpeedTestErrorType.OFFLINE to
                "Tidak dapat terhubung ke server uji. Periksa koneksi internet Anda."
            is IOException -> {
                val msg = e.message.orEmpty()
                if (msg.startsWith("HTTP ")) {
                    SpeedTestErrorType.HTTP_ERROR to "Server uji mengembalikan respons gagal ($msg)."
                } else {
                    SpeedTestErrorType.SERVER_UNAVAILABLE to
                        "Koneksi jaringan terputus atau server tidak tersedia: ${msg.ifBlank { fallbackMsg }}"
                }
            }
            else -> SpeedTestErrorType.UNKNOWN to (e?.localizedMessage?.let { "Kesalahan: $it" } ?: fallbackMsg)
        }
    }
}
