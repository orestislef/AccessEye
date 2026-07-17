# AccessEye for Android

[Διαβάστε στα Ελληνικά](README.el.md)

The Android version of AccessEye: the same product as the iOS app in `../ios/`,
an offline, on-device AI scene narrator for blind and low-vision users.
Point the phone, tap anywhere, and it describes what is in front of you out
loud, in your language. No internet, no account, no cloud.

<p align="center">
  <img src="../docs/screenshots/android/camera.png" alt="Live camera with a spoken description of the scene" width="250">
  &nbsp;&nbsp;
  <img src="../docs/screenshots/android/history.png" alt="History of past descriptions in Greek and English" width="250">
</p>

## Stack

| Concern   | What it uses                                             |
|-----------|----------------------------------------------------------|
| UI        | Kotlin + Jetpack Compose (Material 3), edge to edge      |
| Model     | Gemma 3n E2B (int4), the exact same `.litertlm` file the iOS app downloads |
| Inference | LiteRT-LM Android API (Google AI Edge), CPU decode + GPU vision encoder |
| Camera    | CameraX                                                   |
| Speech    | Android `TextToSpeech`, best installed offline voice per language |
| A11y      | TalkBack live regions, haptics, self-voicing UI, RTL for Arabic |

The app self-voices every state change in the user's chosen language, so it is
fully usable with TalkBack off. When TalkBack is running, short status messages
route through it instead, so two voices never talk over each other. The scene
description itself always uses the app's own speech, in the chosen language.

## The model download

The app ships small (about 48 MB). On first launch it downloads the Gemma 3n
model once (about 3.4 GB) and then works fully offline. The download runs in a
foreground service with a progress notification, so it keeps going if you
leave the app or lock the screen, and it resumes where it stopped if the
connection drops (HTTP range requests against a `.part` file). You can delete
and re-download the model any time from Settings.

The download URL lives in `app/src/main/java/gr/orestislef/accesseye/ai/AppConfig.kt`
and points at the same file the iOS app uses, so both platforms stay in sync.

## Building it

Requirements: JDK 17+, Android SDK 37, a device with 8 GB RAM for the real
model (tested on a Moto G15, Android 15).

```
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Release builds are signed automatically when `signing/keystore.properties`
exists (see below); otherwise `assembleRelease` produces an unsigned build.

```
./gradlew :app:assembleRelease   # APK
./gradlew :app:bundleRelease     # AAB for Play
```

### Signing

Create a keystore once and describe it in `signing/keystore.properties`
(both are gitignored, never commit them):

```
storeFile=signing/accesseye-release.jks
storePassword=...
keyAlias=accesseye
keyPassword=...
```

### Trying it without the model

Set `useRealGemma = false` in `AppConfig.kt` and the app runs end to end with
a built-in demo describer, no download needed. Handy for emulators.

## Languages

English, Greek, Spanish, French, German, Arabic, Hindi, Italian and Russian.
One model handles both the description and the translation; switching language
only changes the prompt and the speech voice. On first launch the app follows
the phone's system language when it is one of the nine. If the offline voice
for a language is not installed, Settings offers a shortcut to install it.

## Performance notes

Gemma 3n E2B needs a device in the 8 GB RAM class. Text generation runs on the
CPU (on mid-range Mali GPUs that is both faster and more stable than GPU
decode) and the vision encoder runs on the GPU, which Gemma 3n requires. The
first description after a fresh install is slow while the engine compiles and
caches its kernels; later ones reuse the cache. While the model is thinking the
camera is released so the UI stays responsive on low-end chips.
