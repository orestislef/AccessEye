//
//  SettingsView.swift
//  AccessEye
//
//  Language, speech speed, and AI-model management. Presented as a sheet from
//  the main screen. (README §6.4, §6.6, §3 Risk #2.)
//

import SwiftUI

struct SettingsView: View {
    @ObservedObject var vm: AppViewModel
    @ObservedObject var modelManager: ModelManager
    @Environment(\.dismiss) private var dismiss

    @State private var showDeleteConfirm = false

    var body: some View {
        NavigationStack {
            Form {
                languageSection
                speechSection
                modelSection
            }
            .navigationTitle(vm.t.settings)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(vm.t.done) { dismiss() }
                }
            }
        }
    }

    private var languageSection: some View {
        Section(vm.t.language) {
            Picker(vm.t.language, selection: $vm.language) {
                ForEach(Language.allCases) { lang in
                    Text(lang.localizedName).tag(lang)
                }
            }
            .pickerStyle(.inline)
            .labelsHidden()
        }
    }

    private var speechSection: some View {
        Section(vm.t.speechSpeed) {
            HStack {
                Image(systemName: "tortoise.fill").accessibilityHidden(true)
                Slider(value: $vm.speechRate, in: 0...1)
                    .accessibilityLabel(vm.t.speechSpeed)
                    .accessibilityValue("\(Int(vm.speechRate * 100)) percent")
                Image(systemName: "hare.fill").accessibilityHidden(true)
            }
        }
    }

    @ViewBuilder
    private var modelSection: some View {
        Section("AI Model") {
            if AppConfig.requiresModelDownload {
                LabeledContent("Model", value: AppConfig.modelFileName)

                switch modelManager.state {
                case .available where ModelStore.isBundledOnly:
                    LabeledContent("Status", value: "Included with app")
                    LabeledContent("Size", value: ModelStore.installedSizeText)
                    Text("This build includes the model, so there's nothing to download or delete.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                case .available:
                    LabeledContent("Status", value: "Downloaded")
                    LabeledContent("Size on device", value: ModelStore.installedSizeText)
                    Button(role: .destructive) {
                        showDeleteConfirm = true
                    } label: {
                        Label("Delete model", systemImage: "trash")
                    }
                    .accessibilityHint("Frees up space. You'll need to download the model again to use the app.")

                case .downloading(let progress):
                    LabeledContent("Status", value: "Downloading \(Int(progress * 100))%")

                default:
                    LabeledContent("Status", value: "Not installed")
                    Button {
                        modelManager.download()
                    } label: {
                        Label("Download model", systemImage: "arrow.down.circle")
                    }
                }
            } else {
                Text("This build runs a built-in demo describer, so no model download is required.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .confirmationDialog("Delete the AI model?",
                            isPresented: $showDeleteConfirm,
                            titleVisibility: .visible) {
            Button("Delete model", role: .destructive) {
                modelManager.deleteModel()
                dismiss()   // returns to onboarding, which prompts re-download
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the \(ModelStore.installedSizeText) model from your device. You can download it again any time.")
        }
    }
}
