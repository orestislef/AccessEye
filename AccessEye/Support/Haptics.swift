//
//  Haptics.swift
//  AccessEye
//
//  Small wrapper for tactile feedback. For a user who can't see the screen,
//  haptics confirm that a tap registered and that work started/finished.
//  (README §7.)
//

import UIKit

@MainActor
enum Haptics {
    /// A capture/tap was registered.
    static func tap() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
    }

    /// Something completed successfully (model ready, description done).
    static func success() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    /// Something went wrong.
    static func error() {
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }

    /// A gentle cue, e.g. when a spoken description finishes.
    static func soft() {
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    }
}
