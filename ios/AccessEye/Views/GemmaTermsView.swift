//
//  GemmaTermsView.swift
//  AccessEye
//
//  Sheet that shows the full Gemma Terms of Use (see GemmaTerms.swift).
//  Presented from onboarding (before the first model download) and from
//  Settings > Licenses. Large readable text, selectable, VoiceOver friendly:
//  the whole document reads as regular text and Done dismisses.
//

import SwiftUI

struct GemmaTermsView: View {
    /// Localized labels for the user's chosen language (title + Done).
    let t: UIText

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(GemmaTerms.fullText)
                    .font(.body)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
            .navigationTitle(t.viewGemmaTerms)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(t.done) { dismiss() }
                }
            }
        }
    }
}

#Preview {
    GemmaTermsView(t: LocalizedUI.text(for: .english))
}
