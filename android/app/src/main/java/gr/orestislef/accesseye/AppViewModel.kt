//
//  AppViewModel.kt
//  AccessEye
//
//  The brain of the main screen (port of AppViewModel.swift). Owns the camera,
//  the describer (Gemma or mock), and drives a small state machine:
//
//    readiness:  PREPARING → READY / FAILED          (model load on launch)
//    activity:   IDLE → DESCRIBING → SPEAKING → IDLE (one capture cycle)
//
//  Every meaningful transition gives spoken + haptic feedback, because the
//  primary user may not see the screen at all. (README §7.)
//

package gr.orestislef.accesseye

import android.app.Application
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import gr.orestislef.accesseye.ai.AppConfig
import gr.orestislef.accesseye.camera.CameraManager
import gr.orestislef.accesseye.history.DescriptionRecord
import gr.orestislef.accesseye.history.HistoryStore
import gr.orestislef.accesseye.model.Language
import gr.orestislef.accesseye.speech.Speaker
import gr.orestislef.accesseye.support.Haptics
import gr.orestislef.accesseye.support.LocalizedUI
import gr.orestislef.accesseye.support.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {

    enum class Activity { IDLE, DESCRIBING, SPEAKING }

    sealed class Readiness {
        data object Preparing : Readiness()
        data object Ready : Readiness()
        data class Failed(val message: String) : Readiness()
    }

    private object Keys {
        const val LANGUAGE = "selectedLanguage"
        const val SPEECH_RATE = "speechRate"
    }

    private val container = getApplication<AccessEyeApp>().container
    private val prefs = container.preferences

    private val _readiness = MutableStateFlow<Readiness>(Readiness.Preparing)
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    private val _activity = MutableStateFlow(Activity.IDLE)
    val activity: StateFlow<Activity> = _activity.asStateFlow()

    private val _lastDescription = MutableStateFlow<String?>(null)
    val lastDescription: StateFlow<String?> = _lastDescription.asStateFlow()

    /**
     * User's chosen output language (persisted). FIRST launch (no saved pref):
     * the phone's system language, when we support it.
     */
    private val _language = MutableStateFlow(
        if (prefs.contains(Keys.LANGUAGE)) Language.fromId(prefs.getString(Keys.LANGUAGE, null))
        else Language.fromLocale(Locale.getDefault())
    )
    val language: StateFlow<Language> = _language.asStateFlow()

    /** Speech rate 0..1 (persisted). */
    private val _speechRate = MutableStateFlow(prefs.getFloat(Keys.SPEECH_RATE, 0.5f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    /**
     * Status text for TalkBack. When TalkBack is running, short status messages
     * route through it (the UI renders this in a polite live region) instead of
     * our own TTS, so two voices never talk over each other. Nullable: null
     * until the first announcement.
     */
    private val _accessibilityAnnouncement = MutableStateFlow<String?>(null)
    val accessibilityAnnouncement: StateFlow<String?> = _accessibilityAnnouncement.asStateFlow()

    /** Alias, in case call sites use the shorter contract name. */
    val announcement: StateFlow<String?> get() = accessibilityAnnouncement

    /** Owned here, like iOS: the ViewModel creates its own CameraManager. */
    val camera = CameraManager(app)

    val history: HistoryStore = container.historyStore
    val speaker: Speaker = container.speaker

    private val describer = AppConfig.makeDescriber(app)

    /**
     * True only while we're waiting for the *description* utterance to finish,
     * so finishing a short status announcement doesn't flip us back to idle.
     */
    private var awaitingDescriptionEnd = false

    private var prepareJob: Job? = null

    /**
     * The last View a user gesture came through — haptics need a View on
     * Android. Weak so we never leak a screen.
     */
    private var hapticView: WeakReference<View>? = null

    init {
        // Persist the first-launch default so every process (e.g. the download
        // service's notification) sees the same language from the start.
        if (!prefs.contains(Keys.LANGUAGE)) {
            prefs.edit().putString(Keys.LANGUAGE, _language.value.id).apply()
        }

        speaker.rate = _speechRate.value
        speaker.onFinish = {
            // Invoked on the main thread by Speaker.
            if (awaitingDescriptionEnd) {
                awaitingDescriptionEnd = false
                _activity.value = Activity.IDLE
                hapticView?.get()?.let { Haptics.soft(it) } // gentle "description finished" cue
            }
        }
    }

    /** Localized strings for the current language (recomputed on read). */
    val t: UiText get() = LocalizedUI.textFor(_language.value)

    // MARK: - Preferences

    fun setLanguage(l: Language) {
        _language.value = l
        prefs.edit().putString(Keys.LANGUAGE, l.id).apply()
    }

    fun setSpeechRate(r: Float) {
        _speechRate.value = r
        speaker.rate = r
        prefs.edit().putFloat(Keys.SPEECH_RATE, r).apply()
    }

    // MARK: - Lifecycle

    /**
     * Called when the main screen appears: start the camera and preload the
     * model so the first capture is fast. Shows "Getting things ready…".
     */
    fun onAppear(lifecycleOwner: LifecycleOwner) {
        camera.start(lifecycleOwner)
        prepareModel()
    }

    /**
     * Called when the main screen goes away. Only the camera stops — speech
     * keeps playing so a description finishes even if the screen locks
     * mid-utterance (crucial for a user who can't see a "resume" button).
     */
    fun onDisappear() {
        camera.stop()
    }

    /** Retry model preparation after a failure. */
    fun retry() {
        prepareModel()
    }

    private fun prepareModel() {
        if (prepareJob?.isActive == true) return // one preparation at a time
        prepareJob = viewModelScope.launch {
            _readiness.value = Readiness.Preparing
            announce(t.gettingReady)

            // Engine creation can fail transiently on the first try (GPU shader
            // compile / memory pressure), so retry a couple of times before
            // giving up. (iOS parity: 3 attempts, 1 s apart.)
            val maxAttempts = 3
            var lastError: Exception? = null
            for (attempt in 1..maxAttempts) {
                try {
                    describer.prepare()
                    _readiness.value = Readiness.Ready
                    hapticView?.get()?.let { Haptics.success(it) }
                    announce(t.ready)
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < maxAttempts) delay(1_000)
                }
            }

            _readiness.value = Readiness.Failed("${t.notReady} ${lastError?.message.orEmpty()}")
            hapticView?.get()?.let { Haptics.error(it) }
            announce(t.notReady)
        }
    }

    // MARK: - Capture → describe → speak

    val canCapture: Boolean
        get() = _readiness.value == Readiness.Ready &&
            _activity.value == Activity.IDLE &&
            !camera.permissionDenied.value

    /** The core action: grab a frame, describe it, speak it. */
    fun captureAndDescribe(view: View) {
        if (!canCapture) return
        rememberView(view)
        Haptics.tap(view)
        _activity.value = Activity.DESCRIBING
        announce(t.describing)

        viewModelScope.launch {
            val image = camera.capturePhoto()
            if (image == null) {
                fail(t.notReady)
                return@launch
            }
            try {
                val text = describer.describe(image, _language.value)
                _lastDescription.value = text
                history.add(text, _language.value, image)
                speakResult(text, _language.value)
            } catch (e: Exception) {
                fail(e.message ?: t.notReady)
            }
        }
    }

    private fun fail(message: String) {
        _activity.value = Activity.IDLE
        hapticView?.get()?.let { Haptics.error(it) }
        announce(message)
    }

    // MARK: - Speech

    /** Speak the previous description again. */
    fun repeatLast(view: View) {
        val text = _lastDescription.value ?: return
        rememberView(view)
        Haptics.tap(view)
        speakResult(text, _language.value)
    }

    /**
     * Tap-the-text behaviour: if currently speaking, stop; otherwise replay the
     * current description. (User request: tap text to speak / stop.)
     */
    fun toggleSpeech(view: View) {
        if (_activity.value == Activity.SPEAKING) {
            stopSpeaking()
        } else {
            val text = _lastDescription.value ?: return
            rememberView(view)
            Haptics.tap(view)
            speakResult(text, _language.value)
        }
    }

    /** Dismiss the current description from the main screen. */
    fun clearCurrent(view: View) {
        rememberView(view)
        stopSpeaking()
        _lastDescription.value = null
        _activity.value = Activity.IDLE
        Haptics.soft(view)
    }

    /** Replay a description from history (in the language it was made in). */
    fun speak(record: DescriptionRecord, view: View) {
        rememberView(view)
        Haptics.tap(view)
        _lastDescription.value = record.text
        speakResult(record.text, record.language)
    }

    /** Stop any speech now. */
    fun stopSpeaking() {
        speaker.stop()
        awaitingDescriptionEnd = false
        _activity.value = Activity.IDLE
    }

    /** Speak a real description and track until it ends. */
    private fun speakResult(text: String, language: Language) {
        awaitingDescriptionEnd = true
        _activity.value = Activity.SPEAKING
        speaker.speak(text, language)
    }

    /**
     * Speak a short status message (does not affect `activity`).
     *
     * The app self-voices via TTS so it works even with TalkBack OFF. But if
     * TalkBack IS on, routing short status through it (via the live-region
     * flow) avoids two voices talking at once. The actual scene description
     * always uses our own TTS, so it's spoken in the user's chosen language
     * regardless of TalkBack. (README §7.)
     */
    fun announce(text: String) {
        if (isTalkBackRunning()) {
            // Live regions only fire on *change*: nudge repeated identical
            // messages with a zero-width space so TalkBack re-announces them.
            _accessibilityAnnouncement.value =
                if (_accessibilityAnnouncement.value == text) text + "\u200B" else text
        } else {
            speaker.speak(text, _language.value)
        }
    }

    private fun isTalkBackRunning(): Boolean {
        val am = getApplication<Application>()
            .getSystemService(AccessibilityManager::class.java) ?: return false
        return am.isEnabled && am.isTouchExplorationEnabled
    }

    private fun rememberView(view: View) {
        hapticView = WeakReference(view)
    }

    /**
     * Give haptics a View before any user gesture happens — the root compose
     * view, attached by MainActivity. Without this the "ready" success haptic
     * on a fresh launch has no View to buzz through and is silently dropped.
     */
    fun attachView(view: View) = rememberView(view)

    /**
     * Called by the UI a few seconds after a live-region announcement has been
     * delivered, so the invisible announcer node doesn't keep a stale status
     * message in the accessibility tree for TalkBack users to stumble on.
     */
    fun clearAnnouncement() {
        _accessibilityAnnouncement.value = null
    }

    override fun onCleared() {
        speaker.onFinish = null // Speaker outlives us (app-scoped); detach our hook
        describer.close()
        super.onCleared()
    }

    companion object {
        /** For `viewModel(factory = AppViewModel.Factory)` at the call site. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as AccessEyeApp
                AppViewModel(app)
            }
        }
    }
}
