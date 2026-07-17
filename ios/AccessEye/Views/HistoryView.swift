//
//  HistoryView.swift
//  AccessEye
//
//  A list of past descriptions. Tap one to hear it again; swipe to delete;
//  clear all from the toolbar. (User request: history.)
//

import SwiftUI

struct HistoryView: View {
    @ObservedObject var vm: AppViewModel
    @ObservedObject private var history: HistoryStore
    @Environment(\.dismiss) private var dismiss

    init(vm: AppViewModel) {
        self.vm = vm
        self.history = vm.history
    }

    var body: some View {
        NavigationStack {
            Group {
                if history.records.isEmpty {
                    ContentUnavailableView(vm.t.noHistory,
                                           systemImage: "clock",
                                           description: Text(vm.t.tapToDescribe))
                } else {
                    list
                }
            }
            .navigationTitle(vm.t.history)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(vm.t.done) { dismiss() }
                }
                if !history.records.isEmpty {
                    ToolbarItem(placement: .destructiveAction) {
                        Button(vm.t.clearHistory, role: .destructive) {
                            history.clear()
                        }
                    }
                }
            }
        }
    }

    private var list: some View {
        List {
            ForEach(history.records) { record in
                Button {
                    vm.speak(record)
                } label: {
                    HStack(alignment: .top, spacing: 12) {
                        if let image = history.image(for: record) {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 64, height: 64)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                .accessibilityHidden(true)
                        }
                        VStack(alignment: .leading, spacing: 6) {
                            Text(record.text)
                                .font(.body)
                                .foregroundStyle(.primary)
                                .lineLimit(4)
                            HStack {
                                Text(record.language.localizedName)
                                Text("·")
                                Text(record.date, format: .dateTime.day().month().hour().minute())
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
                .accessibilityHint("Plays this description again")
            }
            .onDelete { history.delete(at: $0) }
        }
    }
}
