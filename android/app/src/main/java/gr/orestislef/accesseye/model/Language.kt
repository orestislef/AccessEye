//
//  Language.kt
//  AccessEye
//
//  The set of output languages the app supports in v1.
//  Each case carries:
//   - `englishName`: how we refer to the language *inside the prompt* we send to
//     Gemma (the model is told "describe ... in Greek", in English, which is the
//     most reliable way to steer it).
//   - `localizedName`: how the language is shown to the user, written in that
//     language itself (so a Greek speaker sees "Ελληνικά").
//   - `ttsLocale`: the BCP-47 locale used to pick a text-to-speech voice.
//
//  See README §6 (v1 language list) and §4 (one model does description +
//  translation — we only change the prompt and the TTS voice).
//

package gr.orestislef.accesseye.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class Language(val id: String) {
    @SerialName("english") ENGLISH("english"),
    @SerialName("greek") GREEK("greek"),
    @SerialName("spanish") SPANISH("spanish"),
    @SerialName("french") FRENCH("french"),
    @SerialName("german") GERMAN("german"),
    @SerialName("arabic") ARABIC("arabic"),
    @SerialName("hindi") HINDI("hindi"),
    @SerialName("italian") ITALIAN("italian"),
    @SerialName("russian") RUSSIAN("russian");

    /** Name used *inside the prompt* sent to Gemma. Keep these in English. */
    val englishName: String
        get() = when (this) {
            ENGLISH -> "English"
            GREEK -> "Greek"
            SPANISH -> "Spanish"
            FRENCH -> "French"
            GERMAN -> "German"
            ARABIC -> "Arabic"
            HINDI -> "Hindi"
            ITALIAN -> "Italian"
            RUSSIAN -> "Russian"
        }

    /** Name shown to the user, written in the language itself (endonym). */
    val localizedName: String
        get() = when (this) {
            ENGLISH -> "English"
            GREEK -> "Ελληνικά"
            SPANISH -> "Español"
            FRENCH -> "Français"
            GERMAN -> "Deutsch"
            ARABIC -> "العربية"
            HINDI -> "हिन्दी"
            ITALIAN -> "Italiano"
            RUSSIAN -> "Русский"
        }

    /** Locale used to choose a text-to-speech voice. */
    val ttsLocale: Locale
        get() = Locale.forLanguageTag(
            when (this) {
                ENGLISH -> "en-US"
                GREEK -> "el-GR"
                SPANISH -> "es-ES"
                FRENCH -> "fr-FR"
                GERMAN -> "de-DE"
                ARABIC -> "ar-SA"
                HINDI -> "hi-IN"
                ITALIAN -> "it-IT"
                RUSSIAN -> "ru-RU"
            }
        )

    /** True for right-to-left scripts, so the UI can mirror text alignment. */
    val isRightToLeft: Boolean
        get() = this == ARABIC

    companion object {
        /** Look up a language by its persisted id, falling back to English. */
        fun fromId(id: String?): Language =
            entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}
