//
//  GemmaService.swift
//  AccessEye
//
//  The REAL on-device describer: Gemma 3n (E2B) via Google's LiteRT-LM Swift API
//  with Metal GPU acceleration. Everything runs locally — the image never leaves
//  the device.
//
//  Why LiteRT-LM (not MediaPipe): the MediaPipe LLM Inference iOS API is
//  deprecated and never officially supported multimodal *image* input on iOS —
//  exactly the README §3 Risk #1. LiteRT-LM is Google's current engine, ships an
//  official Swift Package, supports image input, and runs on Metal. (Verified
//  against github.com/google-ai-edge/LiteRT-LM v0.13.0.)
//
//  This whole file is guarded by `#if canImport(LiteRTLM)` so the project still
//  builds if the package is ever removed; without it, calls report
//  `sdkNotInstalled` and the app falls back to `MockDescriber`.
//
//  To use it: add the model (.litertlm) and set AppConfig.useRealGemma = true.
//  See SETUP.md.
//

import UIKit

#if canImport(LiteRTLM)
import LiteRTLM

actor GemmaService: SceneDescriber {

    /// The engine is heavy to create, so we build it once and keep it.
    private var engine: Engine?

    func prepare() async throws {
        guard engine == nil else { return }

        guard let modelPath = ModelStore.usableModelPath else {
            throw DescriberError.modelFileMissing
        }

        do {
            // Metal GPU for both the language model and the vision encoder.
            // (This model's vision encoder *requires* the GPU backend; supplying
            // `visionBackend` is also what enables multimodal image input.)
            let config = try EngineConfig(
                modelPath: modelPath,
                backend: .gpu,
                visionBackend: .gpu,
                maxNumTokens: 1024,
                cacheDir: Self.cacheDirectory()
            )
            let engine = Engine(engineConfig: config)
            try await engine.initialize()
            self.engine = engine
        } catch {
            throw DescriberError.inferenceFailed(error.localizedDescription)
        }
    }

    func describe(image: UIImage, in language: Language) async throws -> String {
        try await prepare()
        guard let engine else { throw DescriberError.modelFileMissing }

        guard let jpeg = image.jpegData(compressionQuality: 0.85) else {
            throw DescriberError.inferenceFailed("Could not encode the captured image.")
        }

        do {
            let sampler = try SamplerConfig(topK: 40, topP: 0.95, temperature: 0.6)
            let conversationConfig = ConversationConfig(samplerConfig: sampler)

            // A fresh conversation per capture: each description is independent,
            // so earlier scenes don't bias the next one.
            let conversation = try await engine.createConversation(with: conversationConfig)

            // Image + the "describe for a blind user, in <language>" instruction.
            let message = Message(contents: [
                .imageData(jpeg),
                .text(Prompts.describeScene(in: language)),
            ])

            let response = try await conversation.sendMessage(message)
            let text = response.toString.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else {
                throw DescriberError.inferenceFailed("The model returned no text.")
            }
            return text
        } catch let error as DescriberError {
            throw error
        } catch {
            throw DescriberError.inferenceFailed(error.localizedDescription)
        }
    }

    /// A persistent directory for the engine's compiled GPU artifacts, so they
    /// survive across launches (faster, more reliable init than a temp dir).
    private static func cacheDirectory() -> String {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("LiteRTLM", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.path
    }
}

#else

// Package not linked — provide a type with the same name so call sites compile.
actor GemmaService: SceneDescriber {
    func prepare() async throws {
        throw DescriberError.sdkNotInstalled
    }

    func describe(image: UIImage, in language: Language) async throws -> String {
        throw DescriberError.sdkNotInstalled
    }
}

#endif
