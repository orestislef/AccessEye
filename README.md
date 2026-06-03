# AccessEye

![Platform](https://img.shields.io/badge/platform-iOS%2026%2B-blue)
![Swift](https://img.shields.io/badge/Swift-5-orange)
![UI](https://img.shields.io/badge/UI-SwiftUI-green)
![Model](https://img.shields.io/badge/AI-Gemma%203n%20(on--device)-purple)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

An offline AI narrator for blind and low-vision users. Point your iPhone at the
world, tap the screen, and it describes what's in front of you out loud, in your
language. Everything runs on the device. No internet, no account, no cloud, no
subscription.

I built AccessEye because most "describe the world" tools depend on a server, an
account, and a data connection. That doesn't help if you're offline, travelling,
or simply don't want your camera feed leaving your phone. Modern iPhones are fast
enough to run a real multimodal model locally, so AccessEye does exactly that.

## Screenshots

<p align="center">
  <img src="docs/screenshots/camera.png" alt="Live camera with a spoken description of the scene" width="250">
  &nbsp;&nbsp;
  <img src="docs/screenshots/history.png" alt="History of past descriptions with photo thumbnails, in multiple languages" width="250">
</p>

<p align="center">
  <img src="docs/screenshots/onboarding.png" alt="First-launch welcome" width="250">
  &nbsp;&nbsp;
  <img src="docs/screenshots/downloading.png" alt="Model downloading with progress, speed and ETA" width="250">
</p>

<p align="center"><em>Point and tap, and the scene is described and read aloud. Every
description is saved with its photo. The model downloads once, then everything
runs offline.</em></p>

## What it does

- Point and tap. The whole screen is the shutter; tap anywhere to capture.
- On-device understanding. Google's Gemma 3n vision model runs locally on the
  Metal GPU and describes the scene for a blind user.
- Speaks it aloud. The description is read out with the system text-to-speech, in
  your chosen language, and it plays even when the phone is on silent.
- Multilingual. English, Greek, Spanish, French, German, Arabic, Hindi, Italian
  and Russian. One model handles both the description and the translation.
- History with photos. Every description is saved with its photo; tap to hear it
  again, swipe to delete.
- Accessibility first. Full VoiceOver support, a huge tap target, haptics,
  high-contrast large text, and spoken feedback on every action.
- Private. The image never leaves the phone. The only network use in the whole
  app is a one-time model download.

## How it works

```
 Camera frame  ->  Gemma 3n (on-device, Metal GPU)  ->  Text-to-Speech
 (AVFoundation)    "describe this for a blind user      (AVSpeechSynthesizer,
                    in Greek"                            your language, offline)
```

Gemma 3n is both multimodal (it sees images) and multilingual, so a single
on-device model does the scene description and the translation in one step. There
is no separate translation service. Switching language only changes the prompt and
the speech voice.

The app ships small (around 40 MB). On first launch it downloads the model once
(about 3.4 GB) with a progress bar, then works fully offline afterwards. You can
delete and re-download the model anytime from Settings.

## Tech

- UI: SwiftUI (iOS 26)
- Model: Gemma 3n E2B (int4), .litertlm format
- Inference: LiteRT-LM (Google AI Edge) with Metal GPU acceleration
- Camera: AVFoundation
- Speech: AVSpeechSynthesizer (built into iOS, offline)
- No third-party analytics, no accounts, no backend.

### Project layout

```
AccessEye/
  AccessEyeApp.swift     entry point -> RootView
  AppViewModel.swift     state machine: prepare -> ready -> describe -> speak
  AI/                    model config, LiteRT-LM service, download manager
  Camera/                AVFoundation capture + SwiftUI preview
  Speech/                text-to-speech wrapper
  History/               saved descriptions + photos
  Models/                supported languages
  Support/               haptics, localized strings
  Views/                 onboarding, camera screen, settings, history
```

## Building it yourself

Requirements: Xcode 26+, and an iPhone with 8 GB RAM (iPhone 15 Pro / 16 / 17 and
newer). It must run on a real device. The model needs the Metal GPU, which the
Simulator cannot provide.

1. Clone the repo and open `AccessEye.xcodeproj`.
2. Set your signing team. The app uses the Increased Memory Limit entitlement so
   iOS allows it to hold the roughly 3.4 GB model.
3. Build and run on your iPhone.
4. On first launch, tap Download the AI model. It downloads once, then you are
   ready.

The model download URL is in `AI/AppConfig.swift`. By default it points at a
public copy of the model; you can host your own and change the URL there.

There is also a built-in demo describer (canned descriptions) for trying the whole
flow on the Simulator without the model. Set `useRealGemma = false` in
`AppConfig.swift`.

## Accessibility

This is the whole point, so it is not an afterthought:

- The entire screen is the capture button, plus a clearly labeled VoiceOver button.
- The app voices itself, so it is usable with VoiceOver off (point, tap, listen).
- When VoiceOver is on, status routes through it so two voices do not overlap.
- Haptics confirm capture, readiness, and when a description finishes.
- Speech plays through the silent switch, and the language follows your choice.

## Privacy

The camera image is processed entirely on-device and is never uploaded. The only
network request the app makes is the one-time model download. Descriptions and
photos are stored locally on the phone.

## License and model terms

The app code is released under the MIT License (see `LICENSE`).

The Gemma 3n model is provided by Google under the Gemma Terms of Use
(https://ai.google.dev/gemma/terms). If you redistribute the model, you must
follow those terms.

## Author

Orestis Lef — https://github.com/orestislef
