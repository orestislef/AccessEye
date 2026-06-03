//
//  AppViewModel.swift
//  AccessEye
//
//  The brain of the main screen. Owns the camera, the describer (Gemma or mock),
//  the speaker (TTS), and the history. Drives a small state machine:
//
//    readiness:  preparing → ready / failed     (model load on launch)
//    activity:   idle → describing → speaking → idle   (one capture cycle)
//
//  Every meaningful transition gives spoken + haptic feedback, because the
//  primary user may not see the screen at all. (README §7.)
//

import SwiftUI
import UIKit
import Combine

@MainActor
final class AppViewModel: ObservableObject {

    enum Readiness: Equatable {
        case preparing
        case ready
        case failed(String)
    }

    enum Activity: Equatable {
        case idle
        case describing
        case speaking
    }

    @Published private(set) var readiness: Readiness = .preparing
    @Published private(set) var activity: Activity = .idle
    @Published private(set) var lastDescription: String?

    /// User's chosen output language (persisted). Changing it re-announces ready.
    @Published var language: Language {
        didSet {
            UserDefaults.standard.set(language.rawValue, forKey: Keys.language)
        }
    }

    /// Speech rate 0...1 (persisted).
    @Published var speechRate: Float {
        didSet {
            speaker.rate = speechRate
            UserDefaults.standard.set(speechRate, forKey: Keys.speechRate)
        }
    }

    let camera: CameraManager
    let history = HistoryStore()

    private let speaker = Speaker()
    private let describer = AppConfig.makeDescriber()

    /// True only while we're waiting for the *description* utterance to finish,
    /// so finishing a short status announcement doesn't flip us back to idle.
    private var awaitingDescriptionEnd = false

    private enum Keys {
        static let language = "selectedLanguage"
        static let speechRate = "speechRate"
    }

    init(camera: CameraManager) {
        self.camera = camera

        // Restore persisted preferences.
        let savedLang = UserDefaults.standard.string(forKey: Keys.language)
            .flatMap(Language.init(rawValue:)) ?? .english
        self.language = savedLang

        let savedRate = UserDefaults.standard.object(forKey: Keys.speechRate) as? Float
        self.speechRate = savedRate ?? 0.5

        speaker.rate = speechRate
        speaker.onFinish = { [weak self] in
            guard let self else { return }
            if self.awaitingDescriptionEnd {
                self.awaitingDescriptionEnd = false
                self.activity = .idle
                Haptics.soft()   // gentle cue that the description finished
            }
        }
    }

    /// Localized strings for the current language.
    var t: UIText { LocalizedUI.text(for: language) }

    // MARK: - Lifecycle

    /// Called when the main screen appears: start the camera and preload the
    /// model so the first capture is fast. Shows "Getting things ready…".
    func onAppear() {
        camera.start()
        Task { await prepareModel() }
    }

    func onDisappear() {
        speaker.stop()
        camera.stop()
    }

    /// Retry model preparation after a failure.
    func retry() {
        Task { await prepareModel() }
    }

    private func prepareModel() async {
        readiness = .preparing
        announce(t.gettingReady)

        // Engine creation can fail transiently on the first try (GPU shader
        // compile / memory pressure), so retry a couple of times before giving up.
        let maxAttempts = 3
        var lastError: Error?
        for attempt in 1...maxAttempts {
            do {
                try await describer.prepare()
                readiness = .ready
                Haptics.success()
                announce(t.ready)
                return
            } catch {
                lastError = error
                if attempt < maxAttempts {
                    try? await Task.sleep(for: .seconds(1))
                }
            }
        }

        readiness = .failed("\(t.notReady) \(lastError?.localizedDescription ?? "")")
        Haptics.error()
        announce(t.notReady)
    }

    // MARK: - Capture → describe → speak

    var canCapture: Bool {
        readiness == .ready && activity == .idle && !camera.permissionDenied
    }

    /// The core action: grab a frame, describe it, speak it.
    func captureAndDescribe() {
        guard canCapture else { return }
        Haptics.tap()
        activity = .describing
        announce(t.describing)

        camera.capturePhoto { [weak self] image in
            guard let self else { return }
            guard let image else {
                self.fail(message: self.t.notReady)
                return
            }
            Task { await self.runDescription(on: image) }
        }
    }

    private func runDescription(on image: UIImage) async {
        do {
            let text = try await describer.describe(image: image, in: language)
            lastDescription = text
            history.add(text: text, language: language, image: image)
            speakResult(text, in: language)
        } catch {
            fail(message: error.localizedDescription)
        }
    }

    private func fail(message: String) {
        activity = .idle
        Haptics.error()
        announce(message)
    }

    // MARK: - Speech

    /// Speak the previous description again.
    func repeatLast() {
        guard let text = lastDescription else { return }
        Haptics.tap()
        speakResult(text, in: language)
    }

    /// Tap-the-text behaviour: if currently speaking, stop; otherwise replay the
    /// current description. (User request: tap text to speak / stop.)
    func toggleSpeech() {
        if activity == .speaking {
            stopSpeaking()
        } else if let text = lastDescription {
            Haptics.tap()
            speakResult(text, in: language)
        }
    }

    /// Dismiss the current description from the main screen.
    func clearCurrent() {
        stopSpeaking()
        lastDescription = nil
        activity = .idle
        Haptics.soft()
    }

    /// Replay a description from history (in the language it was made in).
    func speak(_ record: DescriptionRecord) {
        Haptics.tap()
        lastDescription = record.text
        speakResult(record.text, in: record.language)
    }

    /// Stop any speech now.
    func stopSpeaking() {
        speaker.stop()
        if awaitingDescriptionEnd {
            awaitingDescriptionEnd = false
        }
        activity = .idle
    }

    /// Speak a real description and track until it ends.
    private func speakResult(_ text: String, in language: Language) {
        awaitingDescriptionEnd = true
        activity = .speaking
        speaker.speak(text, in: language)
    }

    /// Speak a short status message (does not affect `activity`).
    ///
    /// The app self-voices via TTS so it works even with VoiceOver OFF. But if
    /// VoiceOver IS on, routing short status through it avoids two voices talking
    /// at once. The actual scene description always uses our own TTS, so it's
    /// spoken in the user's chosen language regardless of VoiceOver. (README §7.)
    private func announce(_ text: String) {
        if UIAccessibility.isVoiceOverRunning {
            UIAccessibility.post(notification: .announcement, argument: text)
        } else {
            speaker.speak(text, in: language)
        }
    }
}
