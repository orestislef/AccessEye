//
//  ModelDownloadService.kt
//  AccessEye
//
//  Android-only: keeps the multi-GB model download alive when the user
//  backgrounds the app or locks the screen. A dataSync foreground service with
//  an ongoing progress notification ("Downloading… 42%", localized in the
//  user's CHOSEN language). All download state lives in ModelManager — this
//  service just hosts the work and mirrors progress into the notification.
//
//  If the system kills us (or the dataSync time limit hits), the `.part` file
//  plus HTTP Range resume continue where we left off next time.
//

package gr.orestislef.accesseye.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.ServiceCompat
import gr.orestislef.accesseye.AccessEyeApp
import gr.orestislef.accesseye.model.Language
import gr.orestislef.accesseye.support.LocalizedUI
import gr.orestislef.accesseye.support.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var manager: ModelManager
    private lateinit var t: UiText
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Same singleton the UI observes — one source of truth for progress.
        manager = (application as AccessEyeApp).container.modelManager
        t = LocalizedUI.textFor(currentLanguage())
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(manager.progress.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (!started) {
            started = true
            serviceScope.launch { manager.performDownload() }
            serviceScope.launch { observeProgress() }
            serviceScope.launch { observeState() }
        }
        // Don't auto-restart with a null intent; the app resumes the download
        // (Range + .part) next time the user opens it.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Cancels performDownload() cleanly; the .part file is kept for resume.
        serviceScope.cancel()
        cancelNotification()
        super.onDestroy()
    }

    private fun cancelNotification() {
        try {
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    /** dataSync services get ~6h on API 35+; stop cleanly, resume later. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    // MARK: - ModelManager observation

    /** Mirror download progress into the notification, throttled to ~1/sec. */
    private suspend fun observeProgress() {
        var lastNotify = 0L
        manager.progress.collect { p ->
            if (p == null) return@collect
            // Never post after the download ended — a late final update would
            // resurrect the notification right after stopForeground removed it,
            // leaving a stuck "100%" that nothing ever cancels.
            if (manager.state.value !is ModelManager.State.Downloading) return@collect
            val now = SystemClock.elapsedRealtime()
            val isComplete = p.totalBytes > 0 && p.downloadedBytes >= p.totalBytes
            if (now - lastNotify < 1000 && !isComplete) return@collect
            lastNotify = now
            postNotification(buildNotification(p))
        }
    }

    /** The service only exists while a download runs — stop when it ends. */
    private suspend fun observeState() {
        manager.state.collect { state ->
            if (state !is ModelManager.State.Downloading) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                cancelNotification()
                stopSelf()
            }
        }
    }

    // MARK: - Notification

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            t.downloadModel,
            NotificationManager.IMPORTANCE_LOW, // quiet: no sound, no heads-up
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(p: ModelManager.DownloadProgress?): Notification {
        val percent = ((p?.fraction ?: 0.0) * 100).toInt().coerceIn(0, 100)
        val indeterminate = p == null || p.totalBytes <= 0
        val text = if (p != null && p.totalBytes > 0) {
            Formatter.formatFileSize(this, p.downloadedBytes) +
                " / " + Formatter.formatFileSize(this, p.totalBytes)
        } else {
            t.downloadModel
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("${t.downloading} $percent%")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun postNotification(notification: Notification) {
        // Foreground-service notifications never crash without POST_NOTIFICATIONS;
        // if the user denied it the update is simply suppressed on some versions.
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Suppressed — the download keeps running regardless.
        }
    }

    /** Tapping the notification reopens the app (MainActivity). */
    private fun contentIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    // MARK: - Localization

    /**
     * The notification speaks the user's CHOSEN language (like all UI text),
     * read straight from the same SharedPreferences the app writes. Falls back
     * to English (or the system language mapping) if nothing is saved yet.
     */
    private fun currentLanguage(): Language {
        // Check both plausible pref files so a rename never breaks localization.
        val names = listOf("${packageName}_preferences", "accesseye")
        val id = names.firstNotNullOfOrNull { name ->
            getSharedPreferences(name, MODE_PRIVATE).getString("selectedLanguage", null)
        }
        // First launch (no saved pref): follow the system language, like the app.
        return if (id != null) Language.fromId(id)
               else Language.fromLocale(java.util.Locale.getDefault())
    }

    private companion object {
        const val CHANNEL_ID = "model_download"
        const val NOTIFICATION_ID = 1
    }
}
