//
//  AccessEyeApp.kt
//  AccessEye
//
//  App entry point (port of AccessEyeApp.swift, plus the Android-only wiring).
//  Holds ONE `AppContainer` with the app-wide singletons so the ViewModel and
//  the model-download foreground service share the SAME instances (no
//  duplicated download state). Manual wiring, no DI framework — the graph is
//  four objects.
//

package gr.orestislef.accesseye

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import gr.orestislef.accesseye.ai.ModelManager
import gr.orestislef.accesseye.ai.ModelStore
import gr.orestislef.accesseye.history.HistoryStore
import gr.orestislef.accesseye.model.Language
import gr.orestislef.accesseye.speech.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AccessEyeApp : Application() {
    /** Lazily built so nothing heavy happens before it's actually needed. */
    val container: AppContainer by lazy { AppContainer(this) }
}

/** The app-wide object graph. One instance, owned by [AccessEyeApp]. */
class AppContainer(app: Application) {

    /**
     * Application-scoped work (model download, history IO) that must outlive
     * any single screen. SupervisorJob: one failed child never kills the rest.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Downloads/installs the model; shared by the ViewModel and the service. */
    val modelManager = ModelManager(app, applicationScope)

    /** Single source of truth for the model file's location. */
    val modelStore: ModelStore get() = modelManager.store

    /** Saved descriptions + photos. */
    val historyStore = HistoryStore(app, applicationScope)

    /** One TTS engine for the whole app; lives as long as the process. */
    val speaker = Speaker(app)

    /**
     * User preferences (chosen language, speech rate). Exposed here so the
     * download service can localize its notification in the SAME language the
     * ViewModel persists to.
     */
    val preferences: SharedPreferences =
        app.getSharedPreferences(app.packageName + "_preferences", Context.MODE_PRIVATE)

    /** The user's chosen output language right now (first launch: device locale). */
    val currentLanguage: Language
        get() {
            val saved = preferences.getString("selectedLanguage", null)
            return if (saved != null) Language.fromId(saved)
            else Language.fromLocale(java.util.Locale.getDefault())
        }
}
