//
//  RootView.swift
//  AccessEye
//
//  Decides which top-level screen to show based on whether the AI model is
//  available:
//    • checking      → SplashView
//    • available     → ContentView (the camera/describe screen)
//    • anything else → OnboardingView (download the model)
//
//  Deleting the model from Settings flips the state back to "missing", which
//  brings the user here to re-download — no app restart needed.
//

import SwiftUI

struct RootView: View {
    @StateObject private var modelManager = ModelManager()

    var body: some View {
        Group {
            switch modelManager.state {
            case .checking:
                SplashView()
            case .available:
                ContentView(modelManager: modelManager)
            case .missing, .downloading, .failed:
                OnboardingView(modelManager: modelManager)
            }
        }
        .onAppear { modelManager.refresh() }
    }
}

#Preview {
    RootView()
}
