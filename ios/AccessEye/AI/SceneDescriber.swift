//
//  SceneDescriber.swift
//  AccessEye
//
//  The boundary between the app and the on-device model. The whole UI talks to
//  this protocol, never to MediaPipe directly. That means:
//   - the app builds and runs TODAY using `MockDescriber` (no SDK, no model),
//   - the real `GemmaService` can be swapped in behind the same interface once
//     the MediaPipe pod + model file are added (README §3 Risk #1 / M0).
//

import UIKit

/// Anything that can turn a camera frame into a spoken-ready description.
protocol SceneDescriber: Sendable {
    /// Load the model into memory if it isn't already. Safe to call repeatedly.
    /// Throws `DescriberError` if the model can't be made ready.
    func prepare() async throws

    /// Describe `image` for a blind user, in `language`.
    func describe(image: UIImage, in language: Language) async throws -> String
}

enum DescriberError: LocalizedError {
    /// The MediaPipe SDK is not linked into the build.
    case sdkNotInstalled
    /// The model file (e.g. model.task / model.bin) was not found in the bundle.
    case modelFileMissing
    /// Inference failed at runtime.
    case inferenceFailed(String)

    var errorDescription: String? {
        switch self {
        case .sdkNotInstalled:
            return "The on-device AI engine is not installed in this build."
        case .modelFileMissing:
            return "The AI model file is missing from the app."
        case .inferenceFailed(let detail):
            return "The AI could not describe the scene. \(detail)"
        }
    }
}
