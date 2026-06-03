//
//  SplashView.swift
//  AccessEye
//
//  Briefly shown while we check whether the model is present. Kept simple and
//  high-contrast.
//

import SwiftUI

struct SplashView: View {
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 20) {
                Image(systemName: "eye.circle.fill")
                    .font(.system(size: 80))
                    .foregroundStyle(.white)
                ProgressView()
                    .tint(.white)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("AccessEye is starting")
    }
}

#Preview {
    SplashView()
}
