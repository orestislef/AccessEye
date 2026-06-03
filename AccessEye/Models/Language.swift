//
//  Language.swift
//  AccessEye
//
//  The set of output languages the app supports in v1.
//  Each case carries:
//   - `englishName`: how we refer to the language *inside the prompt* we send to
//     Gemma (the model is told "describe ... in Greek", in English, which is the
//     most reliable way to steer it).
//   - `localizedName`: how the language is shown to the user, written in that
//     language itself (so a Greek speaker sees "Ελληνικά").
//   - `ttsLocale`: the BCP-47 locale used to pick an AVSpeechSynthesisVoice.
//
//  See README §6 (v1 language list) and §4 (one model does description +
//  translation — we only change the prompt and the TTS voice).
//

import Foundation
import AVFoundation

enum Language: String, CaseIterable, Identifiable, Codable {
    case english
    case greek
    case spanish
    case french
    case german
    case arabic
    case hindi
    case italian
    case russian

    var id: String { rawValue }

    /// Name used *inside the prompt* sent to Gemma. Keep these in English.
    var englishName: String {
        switch self {
        case .english: return "English"
        case .greek:   return "Greek"
        case .spanish: return "Spanish"
        case .french:  return "French"
        case .german:  return "German"
        case .arabic:  return "Arabic"
        case .hindi:   return "Hindi"
        case .italian: return "Italian"
        case .russian: return "Russian"
        }
    }

    /// Name shown to the user, written in the language itself (endonym).
    var localizedName: String {
        switch self {
        case .english: return "English"
        case .greek:   return "Ελληνικά"
        case .spanish: return "Español"
        case .french:  return "Français"
        case .german:  return "Deutsch"
        case .arabic:  return "العربية"
        case .hindi:   return "हिन्दी"
        case .italian: return "Italiano"
        case .russian: return "Русский"
        }
    }

    /// Locale used to choose a text-to-speech voice.
    var ttsLocale: String {
        switch self {
        case .english: return "en-US"
        case .greek:   return "el-GR"
        case .spanish: return "es-ES"
        case .french:  return "fr-FR"
        case .german:  return "de-DE"
        case .arabic:  return "ar-SA"
        case .hindi:   return "hi-IN"
        case .italian: return "it-IT"
        case .russian: return "ru-RU"
        }
    }

    /// True for right-to-left scripts, so the UI can mirror text alignment.
    var isRightToLeft: Bool {
        self == .arabic
    }

    /// The best available offline voice for this language, if iOS has one
    /// installed. Returns nil when the user has not downloaded a voice for the
    /// language (we then fall back to the system default in `Speaker`).
    var preferredVoice: AVSpeechSynthesisVoice? {
        // Prefer an exact locale match; AVSpeechSynthesisVoice(language:) returns
        // the highest-quality voice iOS has for that language.
        AVSpeechSynthesisVoice(language: ttsLocale)
    }
}
