//
//  Speaker.kt
//  AccessEye
//
//  Wraps Android's TextToSpeech (the port of Speech/Speaker.swift, which wraps
//  AVSpeechSynthesizer). Speaks a description aloud in the chosen language,
//  exposes whether speech is in progress, and lets the user set the rate. All
//  offline, all built into the OS (README §2, §6.6).
//
//  Android specifics vs iOS:
//   - The engine initializes asynchronously; a speak() issued before
//     onInit(SUCCESS) is queued and played once the engine is ready.
//   - We prefer the Google TTS engine when installed (best offline voices),
//     and pick the best *offline* voice ourselves instead of trusting
//     setLanguage alone.
//   - Instead of an AVAudioSession we set accessibility audio attributes
//     (speech plays even in silent/DND-adjacent modes) and take transient
//     may-duck audio focus around each utterance so other audio ducks politely.
//

package gr.orestislef.accesseye.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import gr.orestislef.accesseye.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow

class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True while speech is being produced (used to drive UI + haptics). */
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /**
     * Speaking rate, 0...1 mapped onto the engine's speech-rate multiplier.
     * 0.5 is a comfortable default for narration (maps to 1.0x, normal speed).
     */
    var rate: Float = 0.5f

    /**
     * Called on the MAIN thread when an utterance finishes. NOT called when
     * speech is cancelled/stopped (iOS parity: didCancel only clears
     * isSpeaking). Used by the view model to know when the spoken
     * description is done.
     */
    var onFinish: (() -> Unit)? = null

    // Speech played on the accessibility stream: audible regardless of ringer
    // mode, and the right usage for a self-voicing app for blind users.
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // One focus-request instance reused for every utterance; the SAME instance
    // must be passed to abandonAudioFocusRequest or the abandon is ignored.
    private val focusRequest = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()
    private var hasAudioFocus = false

    private var isReady = false
    private var initFailed = false

    /** speak() issued before the engine finished initializing; replayed on init. */
    private var pending: Pair<String, Language>? = null

    private val utteranceCounter = AtomicLong(0)

    /**
     * Id of the utterance we are currently playing (main thread only). Progress
     * callbacks for any OTHER id are stale — e.g. the onStop of an utterance we
     * just interrupted arriving after the replacement already took audio focus —
     * and must be ignored.
     */
    private var currentUtteranceId: String? = null

    private var tts: TextToSpeech

    init {
        tts = createEngine()
    }

    /**
     * Build (or rebuild) the TextToSpeech client. Init failure is not a dead
     * end: `speak()` recreates the engine on the next request, so a transient
     * TTS-service hiccup at launch can't leave a blind user with a mute app.
     */
    private fun createEngine(): TextToSpeech {
        // Prefer the Google engine when present — it ships the offline voices
        // for all nine of our languages. getEngines() only needs a client
        // instance (no init), so probe with a throwaway one.
        val engine = run {
            val probe = TextToSpeech(appContext) { /* probe only, never spoken */ }
            val hasGoogle = try {
                probe.engines.any { it.name == GOOGLE_TTS_PACKAGE }
            } catch (_: Exception) {
                false
            }
            probe.shutdown()
            if (hasGoogle) GOOGLE_TTS_PACKAGE else null
        }

        lateinit var client: TextToSpeech
        client = TextToSpeech(appContext, { status ->
            // onInit may arrive on a binder thread — hop to main before
            // touching our state.
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true
                    initFailed = false
                    client.setAudioAttributes(audioAttributes)
                    pending?.let { (text, language) ->
                        pending = null
                        speak(text, language)
                    }
                } else {
                    initFailed = true
                    pending = null
                }
            }
        }, engine)

        client.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            // All three callbacks arrive on binder threads — post to main.

            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId == currentUtteranceId) _isSpeaking.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId != currentUtteranceId) return@post
                    currentUtteranceId = null
                    abandonFocus()
                    _isSpeaking.value = false
                    onFinish?.invoke()
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // Cancelled — clear the flag but do NOT fire onFinish
                // (iOS parity with didCancel).
                mainHandler.post {
                    if (utteranceId != currentUtteranceId) return@post
                    currentUtteranceId = null
                    abandonFocus()
                    _isSpeaking.value = false
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId != currentUtteranceId) return@post
                    currentUtteranceId = null
                    abandonFocus()
                    _isSpeaking.value = false
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    if (utteranceId != currentUtteranceId) return@post
                    currentUtteranceId = null
                    abandonFocus()
                    _isSpeaking.value = false
                }
            }
        })
        return client
    }

    /** Speak `text` in `language`. Any current speech is stopped first. */
    fun speak(text: String, language: Language) {
        if (initFailed) {
            // Previous engine init failed — rebuild and treat this request as
            // pending so it plays as soon as the new engine comes up.
            initFailed = false
            isReady = false
            try {
                tts.shutdown()
            } catch (_: Exception) {
            }
            pending = text to language
            tts = createEngine()
            return
        }
        if (!isReady) {
            // Engine still starting up — keep (only) the latest request and
            // play it from onInit.
            pending = text to language
            return
        }

        // Stopping explicitly (rather than relying on QUEUE_FLUSH) keeps the
        // audio-focus bookkeeping straight: the interrupted utterance's onStop
        // fires, then we take focus again for the new one.
        stop()

        val locale = language.ttsLocale
        val voice = bestOfflineVoice(locale)
        if (voice != null) {
            tts.voice = voice
        } else {
            // No offline voice installed — let the engine do its best
            // (possibly a network voice); Settings surfaces the install path.
            tts.setLanguage(locale)
        }

        // Map our 0...1 rate onto the engine multiplier: 0 -> 0.5x,
        // 0.5 -> 1.0x (normal), 1 -> 2.0x. Applied per speak() so slider
        // changes take effect on the next utterance.
        tts.setSpeechRate(2f.pow((rate - 0.5f) * 2f).coerceIn(0.25f, 3.0f))

        requestFocus()

        val utteranceId = "accesseye-${utteranceCounter.incrementAndGet()}"
        currentUtteranceId = utteranceId
        _isSpeaking.value = true
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            // Enqueue failed synchronously — no listener callback will come.
            currentUtteranceId = null
            abandonFocus()
            _isSpeaking.value = false
        }
    }

    /** Stop any current speech immediately. */
    fun stop() {
        pending = null
        currentUtteranceId = null // any in-flight callbacks are now stale
        if (isReady) {
            tts.stop() // triggers onStop for any in-flight utterance
        }
        abandonFocus()
        _isSpeaking.value = false
    }

    /** True when an *offline* voice for this language is installed. */
    fun isVoiceAvailable(language: Language): Boolean =
        bestOfflineVoice(language.ttsLocale) != null

    /**
     * Send the user to where the missing voice can be installed: Google TTS
     * voice-data install screen when available, otherwise the system
     * text-to-speech settings.
     */
    fun openTtsSettings() {
        try {
            appContext.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    .setPackage(GOOGLE_TTS_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                appContext.startActivity(
                    Intent("com.android.settings.TTS_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // No settings activity at all — nothing more we can do here;
                // the voiceMissing text in Settings still explains the state.
            }
        }
    }

    /** Release the engine. The app keeps one Speaker for the process lifetime. */
    fun shutdown() {
        stop()
        tts.shutdown()
        isReady = false
    }

    // MARK: - Internals

    /**
     * Best installed *offline* voice for `locale`: match by language (Arabic's
     * offline voice is generic "ar", so never require a country match), skip
     * network-only and not-yet-downloaded voices, then prefer an exact country
     * match and higher quality.
     */
    private fun bestOfflineVoice(locale: Locale): Voice? {
        val voices = try {
            tts.voices
        } catch (_: Exception) {
            null // some engines throw before init / on bad state
        }
        return voices
            ?.filter {
                it.locale.language == locale.language &&
                    !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
            }
            ?.maxWithOrNull(
                compareBy({ it.locale.country == locale.country }, { it.quality })
            )
    }

    private fun requestFocus() {
        if (!hasAudioFocus) {
            audioManager.requestAudioFocus(focusRequest)
            hasAudioFocus = true
        }
    }

    private fun abandonFocus() {
        if (hasAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasAudioFocus = false
        }
    }

    private companion object {
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    }
}
