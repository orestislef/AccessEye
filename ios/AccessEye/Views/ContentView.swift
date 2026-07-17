//
//  ContentView.swift
//  AccessEye
//
//  The main screen: a full-screen live camera that the user taps anywhere to
//  capture, after which Gemma describes the scene and the phone speaks it aloud.
//  Designed audio-first for blind / low-vision users (README §7):
//   - the ENTIRE screen is the capture button (huge tap target),
//   - a clearly-labeled "Describe" button for VoiceOver users,
//   - spoken + haptic feedback on every step,
//   - Repeat, History and Settings within easy reach.
//

import SwiftUI

struct ContentView: View {
    // Camera is created here and injected into the view model so both this view
    // and the view model observe the same instance.
    @StateObject private var camera: CameraManager
    @StateObject private var vm: AppViewModel
    @ObservedObject var modelManager: ModelManager

    @State private var showSettings = false
    @State private var showHistory = false

    init(modelManager: ModelManager) {
        self.modelManager = modelManager
        let cam = CameraManager()
        _camera = StateObject(wrappedValue: cam)
        _vm = StateObject(wrappedValue: AppViewModel(camera: cam))
    }

    var body: some View {
        ZStack {
            cameraLayer

            // Full-screen capture target for direct-touch users. Hidden from
            // VoiceOver, which uses the explicit Describe button instead.
            if vm.canCapture {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture { vm.captureAndDescribe() }
                    .accessibilityHidden(true)
            }

            controlsLayer

            if vm.readiness == .preparing {
                preparingOverlay
            }
            if case .failed(let message) = vm.readiness {
                failedOverlay(message)
            }
            if camera.permissionDenied {
                permissionOverlay
            }
        }
        .environment(\.layoutDirection, vm.language.isRightToLeft ? .rightToLeft : .leftToRight)
        .statusBarHidden(true)
        .onAppear { vm.onAppear() }
        .onDisappear { vm.onDisappear() }
        .sheet(isPresented: $showSettings) {
            SettingsView(vm: vm, modelManager: modelManager)
        }
        .sheet(isPresented: $showHistory) {
            HistoryView(vm: vm)
        }
    }

    // MARK: - Layers

    @ViewBuilder
    private var cameraLayer: some View {
        if camera.permissionDenied {
            Color.black.ignoresSafeArea()
        } else {
            CameraPreview(session: camera.session)
                .ignoresSafeArea()
                .overlay(Color.black.opacity(0.15).ignoresSafeArea()) // contrast for text
                .accessibilityHidden(true) // decorative; the Describe button is the control
        }
    }

    private var controlsLayer: some View {
        VStack(spacing: 0) {
            topBar
            Spacer()
            statusCard
            describeButton
        }
        .padding()
    }

    private var topBar: some View {
        HStack {
            // Current language chip.
            Text(vm.language.localizedName)
                .font(.headline)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(.ultraThinMaterial, in: Capsule())
                .accessibilityLabel("\(vm.t.language): \(vm.language.localizedName)")

            Spacer()

            iconButton("clock.arrow.circlepath", label: vm.t.history) {
                showHistory = true
            }
            iconButton("gearshape.fill", label: vm.t.settings) {
                showSettings = true
            }
        }
    }

    @ViewBuilder
    private var statusCard: some View {
        if vm.activity == .describing {
            HStack(spacing: 12) {
                ProgressView().tint(.white)
                Text(vm.t.describing)
                    .font(.title3.weight(.medium))
                    .foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 16))
            .padding(.bottom, 8)
            .accessibilityElement(children: .combine)
            .accessibilityLabel(vm.t.describing)
        } else if let text = vm.lastDescription {
            descriptionCard(text)
        }
    }

    /// The current description: tap it to replay or stop the speech; the ✕
    /// dismisses it. (User request: tap text to speak/stop + a way to clear.)
    private func descriptionCard(_ text: String) -> some View {
        let speaking = vm.activity == .speaking
        return ZStack(alignment: .topTrailing) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: speaking ? "speaker.wave.3.fill" : "play.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.white)
                    .frame(width: 30, alignment: .center) // fixed so text doesn't shift
                ScrollView {
                    Text(text)
                        .font(.title3.weight(.medium))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxHeight: 220) // scroll long descriptions instead of truncating
            }
            .padding()
            .padding(.trailing, 72) // keep the first lines clear of the flag + ✕
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 16))
            .contentShape(Rectangle())
            .onTapGesture { vm.toggleSpeech() }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(text)
            .accessibilityHint(speaking ? vm.t.stop : vm.t.tapToHear)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { vm.toggleSpeech() }

            HStack(spacing: 0) {
                Button {
                    reportDescription(text)
                } label: {
                    Image(systemName: "flag")
                        .font(.title3)
                        .foregroundStyle(.white.opacity(0.85))
                        .frame(width: 44, height: 44) // comfortable tap target
                }
                .accessibilityLabel(vm.t.reportDescription)
                .accessibilityHint(vm.t.reportHint)

                Button {
                    vm.clearCurrent()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(.white.opacity(0.85))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(vm.t.clear)
            }
        }
        .padding(.bottom, 8)
    }

    /// Opens the user's mail app with the description prefilled so a wrong or
    /// misleading description can be reported. If no mail app can handle
    /// mailto:, `open` fails silently and nothing happens.
    private func reportDescription(_ text: String) {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = "iqtaxico@gmail.com"
        components.queryItems = [
            URLQueryItem(name: "subject", value: "AccessEye description report"),
            URLQueryItem(name: "body", value: "\(text)\n\nLanguage: \(vm.language.rawValue)")
        ]
        guard let url = components.url else { return }
        UIApplication.shared.open(url)
    }

    private var describeButton: some View {
        Button {
            vm.captureAndDescribe()
        } label: {
            Label(vm.t.tapToDescribe, systemImage: "camera.viewfinder")
                .font(.title2.bold())
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
        }
        .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 20))
        .foregroundStyle(.white)
        .disabled(!vm.canCapture)
        .opacity(vm.canCapture ? 1 : 0.5)
        .accessibilityHint("Captures what the camera sees and describes it aloud")
    }

    // MARK: - Overlays

    private var preparingOverlay: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView().tint(.white).scaleEffect(1.4)
                Text(vm.t.gettingReady)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.white)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(vm.t.gettingReady)
    }

    private func failedOverlay(_ message: String) -> some View {
        ZStack {
            Color.black.opacity(0.85).ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 56)).foregroundStyle(.yellow)
                Text(message)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                Button("Try again") { vm.retry() }
                    .font(.title2.bold())
                    .tint(.white)
            }
            .padding()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message)
    }

    private var permissionOverlay: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 60)).foregroundStyle(.white)
                Text(vm.t.cameraNeeded)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                .font(.title3.bold())
                .tint(.white)
            }
            .padding()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(vm.t.cameraNeeded)
    }

    // MARK: - Small builders

    private func iconButton(_ systemName: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.title2)
                .frame(width: 44, height: 44)
                .background(.ultraThinMaterial, in: Circle())
        }
        .accessibilityLabel(label)
    }
}

#Preview {
    ContentView(modelManager: ModelManager())
}
