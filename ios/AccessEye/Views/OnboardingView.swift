//
//  OnboardingView.swift
//  AccessEye
//
//  First-launch flow that downloads the on-device AI model once. Explains why a
//  one-time download is needed, downloads it over Wi-Fi with live progress, and
//  handles errors/retry. Fully accessible: large text, high contrast, VoiceOver
//  labels, and progress announced as a percentage. (README §3 Risk #2.)
//

import SwiftUI

struct OnboardingView: View {
    @ObservedObject var modelManager: ModelManager

    @State private var showGemmaTerms = false

    /// Localized labels. Onboarding runs before the main view model exists, so
    /// read the persisted language choice directly (same key AppViewModel uses).
    private var t: UIText {
        let saved = UserDefaults.standard.string(forKey: "selectedLanguage")
            .flatMap(Language.init(rawValue:)) ?? .english
        return LocalizedUI.text(for: saved)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 28) {
                Spacer()

                Image(systemName: "arrow.down.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(.white)
                    .accessibilityHidden(true)

                Text("Welcome to AccessEye")
                    .font(.largeTitle.bold())
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)

                Text("AccessEye describes the world around you out loud — completely on your device, with no internet needed once set up.\n\nTo work offline, it needs to download its AI model one time (\(AppConfig.approxModelSizeText)). Please stay on Wi-Fi.")
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)

                // Gemma license notice + a way to read the full terms before
                // agreeing (the download button is the acceptance).
                Text(t.modelTermsNotice)
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)

                Button(t.viewGemmaTerms) {
                    showGemmaTerms = true
                }
                .font(.callout.weight(.semibold))
                .tint(.white)

                Spacer()

                content

                Spacer()
            }
            .padding(28)
        }
        .sheet(isPresented: $showGemmaTerms) {
            GemmaTermsView(t: t)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch modelManager.state {
        case .checking, .missing:
            downloadButton

        case .downloading(let fraction):
            let p = modelManager.progress
            VStack(spacing: 14) {
                ProgressView(value: fraction)
                    .tint(.white)

                Text("\(Int(fraction * 100))%")
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .monospacedDigit()

                if let p {
                    Text("\(byteText(p.downloadedBytes)) of \(byteText(p.totalBytes))")
                        .font(.headline)
                        .foregroundStyle(.white.opacity(0.9))

                    HStack(spacing: 16) {
                        if p.bytesPerSecond > 0 {
                            Label(speedText(p.bytesPerSecond), systemImage: "speedometer")
                        }
                        if let eta = p.etaSeconds {
                            Label(etaText(eta), systemImage: "clock")
                        }
                    }
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
                }

                Button(role: .cancel) {
                    modelManager.cancel()
                } label: {
                    Text("Cancel").font(.title3)
                }
                .tint(.white)
                .padding(.top, 4)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(downloadAccessibilityLabel(fraction: fraction, progress: p))

        case .available:
            // RootView will swap us out; show a confirmation in the meantime.
            Label("Ready", systemImage: "checkmark.circle.fill")
                .font(.title2.bold())
                .foregroundStyle(.green)

        case .failed(let message):
            VStack(spacing: 16) {
                Text(message)
                    .font(.headline)
                    .foregroundStyle(.yellow)
                    .multilineTextAlignment(.center)
                downloadButton
            }
        }
    }

    // MARK: - Formatting

    private func byteText(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }

    private func speedText(_ bytesPerSecond: Double) -> String {
        let perSec = ByteCountFormatter.string(fromByteCount: Int64(bytesPerSecond), countStyle: .file)
        return "\(perSec)/s"
    }

    private func etaText(_ seconds: Double) -> String {
        let s = Int(seconds.rounded())
        if s >= 3600 { return "\(s / 3600) h \((s % 3600) / 60) min left" }
        if s >= 60 { return "about \(s / 60) min left" }
        return "about \(max(s, 1)) sec left"
    }

    private func downloadAccessibilityLabel(fraction: Double,
                                            progress: ModelManager.DownloadProgress?) -> String {
        var parts = ["Downloading the AI model, \(Int(fraction * 100)) percent"]
        if let eta = progress?.etaSeconds {
            parts.append(etaText(eta))
        }
        return parts.joined(separator: ", ")
    }

    private var downloadButton: some View {
        Button {
            // Tapping is acceptance of the Gemma Terms of Use (shown above).
            UserDefaults.standard.set(true, forKey: "gemmaTermsAccepted")
            modelManager.download()
        } label: {
            Text(t.agreeAndDownload)
                .font(.title2.bold())
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
        }
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
        .foregroundStyle(.black)
        .accessibilityHint("Downloads the AI model so the app can work offline")
    }
}
