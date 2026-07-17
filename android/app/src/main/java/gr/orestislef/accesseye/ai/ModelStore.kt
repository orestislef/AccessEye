//
//  ModelStore.kt
//  AccessEye
//
//  Knows WHERE the on-device model file lives and how to install it. The model
//  is too large to ship in the Play Store binary, so it's downloaded once on
//  first launch and kept in the app's no-backup files directory (excluded from
//  Auto Backup by location so it doesn't bloat the user's backup — mirrors the
//  iOS isExcludedFromBackup flag). (README §3 Risk #2.)
//
//  Both `ModelManager` (downloads it) and `GemmaService` (loads it) go through
//  here, so there is a single source of truth for the path.
//

package gr.orestislef.accesseye.ai

import android.content.Context
import android.text.format.Formatter
import java.io.File
import java.io.IOException

class ModelStore(context: Context) {

    // Formatter.formatFileSize needs a Context, so keep the application one.
    private val appContext: Context = context.applicationContext

    /** Directory holding the model, created on demand. */
    val directory: File = File(appContext.noBackupFilesDir, "model")

    /** Final on-device location of the model file. */
    val file: File = File(directory, AppConfig.modelFileName)

    /** In-progress download location (supports resume across launches). */
    val partFile: File = File(directory, AppConfig.modelFileName + ".part")

    /** True once the model has been fully downloaded and installed. */
    val isInstalled: Boolean
        get() = file.exists()

    /** Size of the installed model on disk in bytes (0 if none). */
    val installedSizeBytes: Long
        get() = if (isInstalled) file.length() else 0L

    /** A human-readable size string for the installed model. */
    val installedSizeText: String
        get() = Formatter.formatFileSize(appContext, installedSizeBytes)

    /** Move a freshly downloaded temp file into its final location atomically. */
    fun install(temp: File) {
        directory.mkdirs()
        if (file.exists() && !file.delete()) {
            throw IOException("Could not replace the existing model file.")
        }
        if (!temp.renameTo(file)) {
            throw IOException("Could not move the downloaded model into place.")
        }
    }

    /** Remove the downloaded model (e.g. to free space or re-download). */
    fun remove() {
        file.delete()
        // Also drop any stale partial download so the next attempt starts clean.
        partFile.delete()
    }
}
