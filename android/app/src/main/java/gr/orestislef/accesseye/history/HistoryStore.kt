//
//  HistoryStore.kt
//  AccessEye
//
//  Keeps a persistent list of past descriptions — text + the captured photo —
//  so the user can review or replay them. Stored on-device: metadata as JSON in
//  the app files directory, photos as JPEGs in a sibling folder. Nothing leaves
//  the device. (User request: history, with images.)
//

package gr.orestislef.accesseye.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import gr.orestislef.accesseye.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** One saved description. */
@Serializable
data class DescriptionRecord(
    val id: String,                 // UUID string
    val text: String,
    val language: Language,
    val dateEpochMillis: Long,
    /** File name of the saved photo in the history images folder, if any. */
    val imageFileName: String? = null,
)

class HistoryStore(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val _records = MutableStateFlow<List<DescriptionRecord>>(emptyList())

    /** Newest-first. */
    val records: StateFlow<List<DescriptionRecord>> = _records.asStateFlow()

    /** Cap how many we keep so storage stays small. */
    private val maxRecords = 200

    /**
     * Max dimension we store photos at — small enough to be cheap, big enough
     * to recognise the scene in the history list.
     */
    private val storedImageMaxDimension = 768

    private val file: File = File(context.filesDir, "history.json")
    private val imagesDirectory: File = File(context.filesDir, "history_images")

    private val json = Json { ignoreUnknownKeys = true }

    /** Single-lane IO dispatcher so mutations and writes stay ordered. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        scope.launch(ioDispatcher) {
            imagesDirectory.mkdirs()
            load()
        }
    }

    /** Add a new description (with its photo) to the top of the list. */
    fun add(text: String, language: Language, image: Bitmap?) {
        val date = System.currentTimeMillis()
        scope.launch(ioDispatcher) {
            val id = UUID.randomUUID().toString()
            val imageFileName = image?.let { saveImage(it, id) }
            val record = DescriptionRecord(
                id = id, text = text, language = language,
                dateEpochMillis = date, imageFileName = imageFileName,
            )
            var updated = listOf(record) + _records.value
            if (updated.size > maxRecords) {
                updated.drop(maxRecords).forEach { deleteImageFile(it) }
                updated = updated.take(maxRecords)
            }
            _records.value = updated
            save()
        }
    }

    /** The photo file for a record, or null if it has none / it is missing. */
    fun imageFile(record: DescriptionRecord): File? {
        val name = record.imageFileName ?: return null
        val file = File(imagesDirectory, name)
        return if (file.exists()) file else null
    }

    fun delete(record: DescriptionRecord) {
        scope.launch(ioDispatcher) {
            deleteImageFile(record)
            _records.value = _records.value.filterNot { it.id == record.id }
            save()
        }
    }

    fun clear() {
        scope.launch(ioDispatcher) {
            _records.value.forEach { deleteImageFile(it) }
            _records.value = emptyList()
            save()
        }
    }

    // MARK: - Image files

    private fun saveImage(image: Bitmap, id: String): String? {
        val scaled = image.downscaled(storedImageMaxDimension)
        val name = "$id.jpg"
        val target = File(imagesDirectory, name)
        return try {
            FileOutputStream(target).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)) return null
            }
            name
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    private fun deleteImageFile(record: DescriptionRecord) {
        val name = record.imageFileName ?: return
        File(imagesDirectory, name).delete()
    }

    // MARK: - Persistence

    private fun load() {
        if (!file.exists()) return
        try {
            val decoded = json.decodeFromString(
                ListSerializer(DescriptionRecord.serializer()), file.readText()
            )
            _records.value = decoded
        } catch (_: Exception) {
            // Corrupt or unreadable history — start fresh rather than crash.
        }
    }

    private fun save() {
        try {
            // Atomic write: temp file in the same directory, then rename over the old one.
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(
                json.encodeToString(
                    ListSerializer(DescriptionRecord.serializer()), _records.value
                )
            )
            if (!temp.renameTo(file)) {
                // rename can fail across some filesystems — fall back to direct copy.
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) {
            // Best effort, like iOS `try?` — never crash the app over history.
        }
    }
}

/** Returns a copy whose longest side is at most `maxDimension` pixels. */
private fun Bitmap.downscaled(maxDimension: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxDimension) return this
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}
