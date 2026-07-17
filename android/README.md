# AccessEye for Android (planned)

The Android port of AccessEye: same product as the iOS app in `../ios/` —
an offline, on-device AI scene narrator for blind and low-vision users.
Camera → Gemma running locally → spoken description in the user's language.
No internet, no account.

Nothing is built here yet. This folder reserves the place in the repo and
records the intended stack so work can start without re-deciding everything.

## Planned stack (mirrors the iOS decisions)

| Concern   | iOS (shipped)                    | Android (planned)                          |
|-----------|----------------------------------|--------------------------------------------|
| UI        | SwiftUI                          | Kotlin + Jetpack Compose                   |
| Model     | Gemma (multimodal + multilingual)| same model file (`.litertlm` / `.task`)    |
| Inference | LiteRT-LM via local Swift package| MediaPipe LLM Inference API (Google AI Edge, first-class on Android) |
| Camera    | AVFoundation                     | CameraX                                    |
| Speech    | AVSpeechSynthesizer              | Android `TextToSpeech`                     |
| A11y      | VoiceOver, haptics               | TalkBack, haptics                          |

Notes:

- The inference engine is actually *easier* on Android — MediaPipe LLM
  Inference started as an Android API, GPU delegate included.
- The model is downloaded at runtime (like iOS, see `ios/AccessEye/AI/ModelManager.swift`);
  the download URL / model config should stay in sync with `ios/AccessEye/AI/AppConfig.swift`.
- Prompts and the supported-language list should be ported from
  `ios/AccessEye/AI/Prompts.swift` and `ios/AccessEye/Models/Language.swift`
  so both apps behave identically.

## Getting started (when work begins)

1. Create the project here with Android Studio: "Empty Activity (Compose)",
   package `com.accesseye.app`, min SDK 31+ (needs a device with enough RAM
   for the model — 8 GB class, e.g. Pixel 8 Pro or newer).
2. First milestone, same as iOS M0: ONE image + prompt → Gemma → text on a
   real device, before any UI.
