//
//  ModelManager.kt
//  AccessEye
//
//  Handles the one-time, on-first-launch download of the Gemma model with live
//  progress, so onboarding can show "Downloading… 42%". Everything stays on the
//  device; the only network use in the whole app is fetching this model file
//  once. (README §3 Risk #2.)
//
//  The download itself must survive the user backgrounding the app or locking
//  the screen, so the actual streaming work runs inside ModelDownloadService (a
//  dataSync foreground service) which calls performDownload() here. This class
//  owns ALL download state — the service and the UI observe the same StateFlows,
//  so there is a single source of truth. The `.part` file plus HTTP Range
//  requests let an interrupted download resume where it left off.
//

package gr.orestislef.accesseye.ai

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

class ModelManager(context: Context, private val scope: CoroutineScope) {

    sealed class State {
        data object Checking : State()
        data object Missing : State()
        data class Downloading(val progress: Double) : State()   // 0..1
        data object Available : State()
        data class Failed(val message: String) : State()
    }

    /** Rich progress for the download UI: percent, bytes, speed and ETA. */
    data class DownloadProgress(
        val fraction: Double,            // 0..1
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Double,
        val etaSeconds: Double?,
    )

    private val appContext: Context = context.applicationContext

    val store = ModelStore(appContext)

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** True while a performDownload() invocation is running (guards doubles). */
    private val downloadRunning = AtomicBoolean(false)

    @Volatile private var downloadJob: Job? = null
    @Volatile private var currentCall: Call? = null

    // Speed/ETA sampling — touched only from the single download coroutine.
    private var sampleTimeNanos: Long = 0
    private var sampleBytes: Long = 0
    private var smoothedSpeed: Double = 0.0
    private var lastUiUpdateNanos: Long = 0

    /** Check what we already have. Call on launch. */
    fun refresh() {
        // Mock mode needs no model at all.
        if (!AppConfig.requiresModelDownload) {
            _state.value = State.Available
            return
        }
        // Never clobber an in-flight download.
        if (_state.value is State.Downloading) return
        scope.launch(Dispatchers.IO) {
            _state.value = if (store.isInstalled) State.Available else State.Missing
        }
    }

    /**
     * Start (or restart) the model download. The heavy lifting happens in
     * ModelDownloadService (so it survives backgrounding), which calls
     * [performDownload] on this same instance.
     */
    fun download() {
        if (AppConfig.modelDownloadUrl == null) {
            _state.value = State.Failed("No model download source is configured yet.")
            return
        }
        if (downloadJob?.isActive == true) return

        _state.value = State.Downloading(0.0)
        _progress.value = DownloadProgress(0.0, 0, 0, 0.0, null)
        appContext.startForegroundService(Intent(appContext, ModelDownloadService::class.java))
    }

    /** Cancel an in-progress download. The `.part` file is kept for resume. */
    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        currentCall?.cancel()
        scope.launch(Dispatchers.IO) {
            _progress.value = null
            _state.value = if (store.isInstalled) State.Available else State.Missing
        }
    }

    /** Delete the model and return to the "missing" state. */
    fun deleteModel() {
        scope.launch(Dispatchers.IO) {
            store.remove()
            _progress.value = null
            _state.value =
                if (!AppConfig.requiresModelDownload || store.isInstalled) State.Available
                else State.Missing
        }
    }

    // MARK: - The actual download (runs inside the foreground service)

    /**
     * The streaming download loop, called by ModelDownloadService from its own
     * coroutine. Suspends until the download finishes, fails for good, or is
     * cancelled. Resumes from `.part` via HTTP Range; retries transient
     * IOExceptions up to 3 times with exponential backoff.
     */
    suspend fun performDownload() {
        if (!downloadRunning.compareAndSet(false, true)) return
        try {
            downloadJob = currentCoroutineContext()[Job]
            withContext(Dispatchers.IO) { runDownload() }
        } finally {
            downloadJob = null
            currentCall = null
            downloadRunning.set(false)
            // If we're exiting while the UI still says "Downloading", the service
            // died without going through cancel() (dataSync timeout, system kill).
            // Reset to a resumable state so onboarding shows the download button
            // again instead of frozen progress; the .part file keeps the bytes.
            if (_state.value is State.Downloading) {
                _progress.value = null
                _state.value = if (store.isInstalled) State.Available else State.Missing
            }
        }
    }

    private suspend fun runDownload() {
        val url = AppConfig.modelDownloadUrl
        if (url == null) {
            _state.value = State.Failed("No model download source is configured yet.")
            return
        }

        val maxRetries = 3
        var attempt = 0
        while (true) {
            try {
                downloadOnce(url)
                return
            } catch (e: CancellationException) {
                throw e // cancel() / service shutdown — state handled elsewhere
            } catch (e: IOException) {
                coroutineContext.ensureActive() // a cancelled Call surfaces as IOException
                attempt += 1
                if (attempt > maxRetries) {
                    _state.value = State.Failed(e.message ?: "The download failed.")
                    return
                }
                delay(1000L shl (attempt - 1)) // 1s, 2s, 4s
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: "The download failed.")
                return
            }
        }
    }

    /** One full attempt: request (with Range resume), stream to `.part`, install. */
    private suspend fun downloadOnce(url: String) {
        store.ensureDirectory()
        var existing = if (store.partFile.exists()) store.partFile.length() else 0L

        val request = Request.Builder().url(url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
        }.build()

        val call = client.newCall(request)
        currentCall = call
        try {
            call.execute().use { response ->
                var append = false
                val total: Long = when (response.code) {
                    206 -> {
                        // Server honored the range — append to what we have.
                        append = true
                        parseContentRangeTotal(response.header("Content-Range"))
                            ?: response.body.contentLength().let { if (it > 0) existing + it else -1L }
                    }
                    200 -> {
                        // Server ignored the range (or fresh start) — restart cleanly.
                        existing = 0
                        store.partFile.delete()
                        response.body.contentLength()
                    }
                    416 -> {
                        // Our .part is stale/oversized — drop it and retry from scratch.
                        store.partFile.delete()
                        throw IOException("The saved partial download was rejected (HTTP 416).")
                    }
                    else -> throw IOException("The server answered HTTP ${response.code}.")
                }

                var written = existing
                beginSampling(written)

                response.body.byteStream().use { input ->
                    FileOutputStream(store.partFile, append).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            updateProgress(written, total)
                        }
                    }
                }

                if (total > 0 && written < total) {
                    throw IOException("The connection was interrupted.")
                }

                // Always show the final 100% before installing.
                updateProgress(written, if (total > 0) total else written)
                store.install(store.partFile)
                _state.value = State.Available
            }
        } finally {
            currentCall = null
        }
    }

    /** "bytes 1000-3399/3400" → 3400 (null when absent/unknown). */
    private fun parseContentRangeTotal(header: String?): Long? =
        header?.substringAfter('/', "")?.toLongOrNull()?.takeIf { it > 0 }

    // MARK: - Progress sampling (iOS parity)

    private fun beginSampling(startBytes: Long) {
        sampleTimeNanos = System.nanoTime()
        sampleBytes = startBytes
        smoothedSpeed = 0.0
        lastUiUpdateNanos = 0
    }

    /** Update progress + speed/ETA from the stream loop (throttled). */
    private fun updateProgress(written: Long, total: Long) {
        if (_state.value !is State.Downloading) return
        val now = System.nanoTime()

        // Sample speed roughly twice a second for a stable estimate (EMA 0.6/0.4).
        val dt = (now - sampleTimeNanos) / 1e9
        if (dt >= 0.5) {
            val instant = (written - sampleBytes) / dt
            smoothedSpeed = if (smoothedSpeed == 0.0) instant
                            else 0.6 * smoothedSpeed + 0.4 * instant
            sampleTimeNanos = now
            sampleBytes = written
        }

        // Throttle UI refreshes (~3/sec), but always show the final 100%.
        val isComplete = total > 0 && written >= total
        if (lastUiUpdateNanos != 0L && (now - lastUiUpdateNanos) / 1e9 < 0.3 && !isComplete) {
            return
        }
        lastUiUpdateNanos = now

        val fraction = if (total > 0) written.toDouble() / total else 0.0
        val eta: Double? = if (smoothedSpeed > 0 && total > 0)
            (total - written) / smoothedSpeed else null
        _progress.value = DownloadProgress(
            fraction = fraction,
            downloadedBytes = written,
            totalBytes = maxOf(total, 0),
            bytesPerSecond = smoothedSpeed,
            etaSeconds = eta,
        )
        _state.value = State.Downloading(fraction)
    }
}
