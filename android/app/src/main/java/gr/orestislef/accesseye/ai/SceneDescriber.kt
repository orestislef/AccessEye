//
//  SceneDescriber.kt
//  AccessEye
//
//  The boundary between the app and the on-device model. The whole UI talks to
//  this interface, never to MediaPipe directly. That means:
//   - the app builds and runs TODAY using `MockDescriber` (no SDK, no model),
//   - the real `GemmaService` can be swapped in behind the same interface once
//     the MediaPipe dependency + model file are added (README §3 Risk #1 / M0).
//

package gr.orestislef.accesseye.ai

import android.graphics.Bitmap
import gr.orestislef.accesseye.model.Language

/** Anything that can turn a camera frame into a spoken-ready description. */
interface SceneDescriber {
    /**
     * Load the model into memory if it isn't already. Safe to call repeatedly.
     * Throws [DescriberException] if the model can't be made ready.
     */
    suspend fun prepare()

    /** Describe [image] for a blind user, in [language]. */
    suspend fun describe(image: Bitmap, language: Language): String

    /** Release engine resources. */
    fun close() {}
}

sealed class DescriberException(message: String) : Exception(message) {
    /** The MediaPipe SDK is not linked into the build. */
    class SdkNotInstalled : DescriberException("The on-device AI engine is not installed in this build.")

    /** The model file (e.g. model.task / model.bin) was not found. */
    class ModelFileMissing : DescriberException("The AI model file is missing from the app.")

    /** Inference failed at runtime. */
    class InferenceFailed(detail: String) : DescriberException("The AI could not describe the scene. $detail")
}
