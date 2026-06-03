//
//  AppConfig.swift
//  AccessEye
//
//  One place for build-time choices.
//

import Foundation

enum AppConfig {

    /// Flip to `true` once the MediaPipe SDK is linked (see SETUP.md). While
    /// `false`, the app uses `MockDescriber` so it runs end-to-end without the
    /// 1.3 GB model — and onboarding/download is skipped. (README §9 — M0.)
    static let useRealGemma = true

    /// Whether the app must obtain the model before it can work. Only the real
    /// engine needs the downloaded file; the mock describer needs nothing.
    static var requiresModelDownload: Bool { useRealGemma }

    // MARK: - Model file & download

    /// File name the model is stored under on device (in Application Support).
    /// LiteRT-LM uses the `.litertlm` format. Default: Gemma 3n E2B, int4.
    static let modelFileName = "gemma-3n-E2B-it-int4.litertlm"

    /// Direct download URL for the model. ⚠️ Set this before shipping the real
    /// engine. While `nil`, the download step reports that no source is
    /// configured. The link must be a *direct* file URL (not an HTML page).
    ///
    /// The official model lives on Hugging Face (`google/gemma-3n-E2B-it-litert-lm`)
    /// behind Google's Gemma license, so a plain public URL won't work for App
    /// Store users — host the file yourself (your CDN / signed URL), or have the
    /// user supply it. For dev, just drag the `.litertlm` into the app target;
    /// `ModelStore` will find it without any download. (README §3 Risk #2.)
    static let modelDownloadURL: URL? = URL(string:
        "https://huggingface.co/OrestisIqtaxi/accesseye-gemma3n-e2b/resolve/main/gemma-3n-E2B-it-int4.litertlm")

    /// Human-readable size shown during onboarding. The Gemma 3n E2B int4
    /// `.litertlm` file is ~3.4 GB (verified from Hugging Face).
    static let approxModelSizeText = "about 3.4 GB"

    /// Builds the describer the app should use.
    static func makeDescriber() -> SceneDescriber {
        useRealGemma ? GemmaService() : MockDescriber()
    }
}
