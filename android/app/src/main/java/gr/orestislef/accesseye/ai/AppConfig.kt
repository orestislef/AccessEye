//
//  AppConfig.kt
//  AccessEye
//
//  One place for build-time choices.
//

package gr.orestislef.accesseye.ai

import android.content.Context

object AppConfig {

    /**
     * Flip to `true` once the MediaPipe SDK is linked (see SETUP.md). While
     * `false`, the app uses `MockDescriber` so it runs end-to-end without the
     * multi-GB model — and onboarding/download is skipped. (README §9 — M0.)
     */
    const val useRealGemma: Boolean = true

    /**
     * Whether the app must obtain the model before it can work. Only the real
     * engine needs the downloaded file; the mock describer needs nothing.
     */
    val requiresModelDownload: Boolean get() = useRealGemma

    // MARK: - Model file & download
    // NOTE: file name + URL are deliberately two easily-edited consts — they may
    // be swapped to a MediaPipe `.task` file later.

    /**
     * File name the model is stored under on device (in the no-backup files
     * directory). LiteRT-LM uses the `.litertlm` format. Default: Gemma 3n E2B, int4.
     */
    const val modelFileName: String = "gemma-3n-E2B-it-int4.litertlm"

    /**
     * Direct download URL for the model. ⚠️ Set this before shipping the real
     * engine. While `null`, the download step reports that no source is
     * configured. The link must be a *direct* file URL (not an HTML page).
     *
     * The official model lives on Hugging Face (`google/gemma-3n-E2B-it-litert-lm`)
     * behind Google's Gemma license, so a plain public URL won't work for Play
     * Store users — host the file yourself (your CDN / signed URL), or have the
     * user supply it. (README §3 Risk #2.)
     */
    val modelDownloadUrl: String? =
        "https://huggingface.co/OrestisIqtaxi/accesseye-gemma3n-e2b/resolve/main/gemma-3n-E2B-it-int4.litertlm"

    /**
     * Human-readable size shown during onboarding. The Gemma 3n E2B int4
     * `.litertlm` file is ~3.4 GB (verified from Hugging Face).
     */
    const val approxModelSizeText: String = "about 3.4 GB"

    /** Builds the describer the app should use. */
    fun makeDescriber(context: Context): SceneDescriber =
        if (useRealGemma) GemmaService(context, ModelStore(context)) else MockDescriber()
}
