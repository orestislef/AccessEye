//
//  GemmaService.kt
//  AccessEye
//
//  The REAL on-device describer: Gemma 3n (E2B) via Google's LiteRT-LM Android
//  API. Everything runs locally — the image never leaves the device.
//
//  Why LiteRT-LM (not MediaPipe): the MediaPipe LLM Inference API is in
//  maintenance mode; LiteRT-LM is Google's current engine, consumes the same
//  `.litertlm` file as the iOS build, and supports multimodal image input.
//  Backends: CPU for text decode (GPU decode is slower and crash-prone on
//  Mali-class GPUs), GPU for the vision encoder (required for Gemma 3n).
//
//  The engine is heavy (~10s+ to initialize), so it is built exactly once,
//  guarded by a Mutex, always on a background dispatcher. Each describe() uses
//  a fresh Conversation so earlier scenes don't bias the next one.
//

package gr.orestislef.accesseye.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import gr.orestislef.accesseye.model.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class GemmaService(
    private val context: Context,
    private val store: ModelStore,
) : SceneDescriber {

    /** The engine is heavy to create, so we build it once and keep it. */
    @Volatile
    private var engine: Engine? = null

    /** Serializes initialization so concurrent prepare() calls build ONE engine. */
    private val initMutex = Mutex()

    override suspend fun prepare(): Unit = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (engine != null) return@withContext

            if (!store.isInstalled) {
                throw DescriberException.ModelFileMissing()
            }

            try {
                val engineConfig = EngineConfig(
                    modelPath = store.file.absolutePath,
                    // Text/decode on CPU: GPU decode is ~6x slower and unstable
                    // on Mali-G52-class GPUs. Vision MUST be GPU for Gemma 3n.
                    backend = Backend.CPU(),
                    visionBackend = Backend.GPU(),
                    maxNumTokens = 1024,
                    cacheDir = cacheDirectory(),
                )
                val newEngine = Engine(engineConfig)
                newEngine.initialize() // blocking, can take 10s+ — IO dispatcher
                engine = newEngine
            } catch (e: CancellationException) {
                throw e
            } catch (e: DescriberException) {
                throw e
            } catch (e: Exception) {
                // Retry (x3) lives in AppViewModel, like iOS.
                throw DescriberException.InferenceFailed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override suspend fun describe(image: Bitmap, language: Language): String {
        prepare()
        val engine = engine ?: throw DescriberException.ModelFileMissing()

        return withContext(Dispatchers.Default) {
            // The vision encoder works at 768px — downscale first so we don't
            // waste time encoding pixels the model will never see.
            val png = toPngBytes(downscaled(image, MAX_IMAGE_SIDE))

            try {
                // A fresh conversation per capture: each description is
                // independent, so earlier scenes don't bias the next one.
                val conversation = engine.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6),
                    )
                )
                try {
                    // Image FIRST, text after (required for an accurate last token).
                    val contents = listOf(
                        Content.ImageBytes(png),
                        Content.Text(Prompts.describeScene(language)),
                    )
                    val reply = conversation.sendMessage(Contents.of(contents))
                    val text = reply.toString().trim()
                    if (text.isEmpty()) {
                        throw DescriberException.InferenceFailed("The model returned no text.")
                    }
                    text
                } finally {
                    conversation.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DescriberException) {
                throw e
            } catch (e: Exception) {
                throw DescriberException.InferenceFailed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    /**
     * A persistent directory for the engine's compiled GPU artifacts, so they
     * survive across launches (faster, more reliable init than a temp dir).
     * Lives in noBackupFilesDir: the OS never purges it under storage pressure
     * (cacheDir can be wiped) and it stays out of device backups.
     */
    private fun cacheDirectory(): String =
        context.noBackupFilesDir.resolve("litertlm-cache").apply { mkdirs() }.absolutePath

    /** Scales [src] down so its longest side is at most [maxSide] (no-op if smaller). */
    private fun downscaled(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun toPngBytes(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw DescriberException.InferenceFailed("Could not encode the captured image.")
            }
            out.toByteArray()
        }

    private companion object {
        const val MAX_IMAGE_SIDE = 768
    }
}
