//
//  Speaker.swift
//  AccessEye
//
//  Wraps AVSpeechSynthesizer. Speaks a description aloud in the chosen language,
//  exposes whether speech is in progress, and lets the user set the rate. All
//  offline, all built into iOS (README §2, §6.6).
//

import AVFoundation
import Combine

@MainActor
final class Speaker: NSObject, ObservableObject {

    /// True while speech is being produced (used to drive UI + haptics).
    @Published private(set) var isSpeaking = false

    /// Speaking rate, 0...1 mapped onto AVSpeechUtterance's supported range.
    /// 0.5 is a comfortable default for narration.
    @Published var rate: Float = 0.5

    private let synthesizer = AVSpeechSynthesizer()

    /// Called when an utterance finishes (or is cancelled). Used by the view
    /// model to know when the spoken description is done.
    var onFinish: (() -> Void)?

    override init() {
        super.init()
        synthesizer.delegate = self
        activateAudioSession()
    }

    /// Speak `text` in `language`. Any current speech is stopped first.
    func speak(_ text: String, in language: Language) {
        stop()

        // Re-activate our playback session every time: the on-device model can
        // reconfigure the shared audio session while loading, which otherwise
        // leaves our speech silent.
        activateAudioSession()

        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = language.preferredVoice
            ?? AVSpeechSynthesisVoice(language: language.ttsLocale)
        utterance.rate = mappedRate
        utterance.volume = 1.0

        isSpeaking = true
        synthesizer.speak(utterance)
    }

    /// Stop any current speech immediately.
    func stop() {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        isSpeaking = false
    }

    // MARK: - Internals

    /// Map our 0...1 `rate` onto AVSpeechUtterance's min/max so the slider
    /// covers the full usable range with a sensible default near the middle.
    private var mappedRate: Float {
        let minR = AVSpeechUtteranceMinimumSpeechRate
        let maxR = AVSpeechUtteranceMaximumSpeechRate
        return minR + (maxR - minR) * rate
    }

    /// Configure + activate the audio session so speech plays even with the
    /// silent switch on, and mixes politely with other audio.
    private func activateAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? session.setActive(true)
    }
}

extension Speaker: AVSpeechSynthesizerDelegate {
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer,
                                       didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.isSpeaking = false
            self.onFinish?()
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer,
                                       didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.isSpeaking = false
        }
    }
}
