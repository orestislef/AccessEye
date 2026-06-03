//
//  HistoryStore.swift
//  AccessEye
//
//  Keeps a persistent list of past descriptions — text + the captured photo —
//  so the user can review or replay them. Stored on-device: metadata as JSON in
//  Application Support, photos as JPEGs in a sibling folder. Nothing leaves the
//  device. (User request: history, with images.)
//

import Foundation
import UIKit
import Combine

/// One saved description.
struct DescriptionRecord: Identifiable, Codable, Hashable {
    let id: UUID
    let text: String
    let language: Language
    let date: Date
    /// File name of the saved photo in the history images folder, if any.
    var imageFileName: String?

    init(id: UUID = UUID(), text: String, language: Language,
         date: Date = Date(), imageFileName: String? = nil) {
        self.id = id
        self.text = text
        self.language = language
        self.date = date
        self.imageFileName = imageFileName
    }
}

@MainActor
final class HistoryStore: ObservableObject {

    @Published private(set) var records: [DescriptionRecord] = []

    /// Cap how many we keep so storage stays small.
    private let maxRecords = 200

    /// Max dimension we store photos at — small enough to be cheap, big enough
    /// to recognise the scene in the history list.
    private let storedImageMaxDimension: CGFloat = 768

    private let fileURL: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory,
                                            in: .userDomainMask)[0]
        return base.appendingPathComponent("history.json")
    }()

    private let imagesDirectory: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory,
                                            in: .userDomainMask)[0]
        return base.appendingPathComponent("HistoryImages", isDirectory: true)
    }()

    init() {
        try? FileManager.default.createDirectory(at: imagesDirectory,
                                                 withIntermediateDirectories: true)
        load()
    }

    /// Add a new description (with its photo) to the top of the list.
    func add(text: String, language: Language, image: UIImage?, date: Date = Date()) {
        let id = UUID()
        let imageFileName = image.flatMap { saveImage($0, id: id) }
        let record = DescriptionRecord(id: id, text: text, language: language,
                                       date: date, imageFileName: imageFileName)
        records.insert(record, at: 0)
        if records.count > maxRecords {
            let removed = records.suffix(records.count - maxRecords)
            removed.forEach { deleteImageFile($0) }
            records.removeLast(records.count - maxRecords)
        }
        save()
    }

    /// Load the photo for a record, if it has one.
    func image(for record: DescriptionRecord) -> UIImage? {
        guard let name = record.imageFileName else { return nil }
        return UIImage(contentsOfFile: imagesDirectory.appendingPathComponent(name).path)
    }

    func delete(_ record: DescriptionRecord) {
        deleteImageFile(record)
        records.removeAll { $0.id == record.id }
        save()
    }

    func delete(at offsets: IndexSet) {
        offsets.forEach { deleteImageFile(records[$0]) }
        records = records.enumerated()
            .filter { !offsets.contains($0.offset) }
            .map(\.element)
        save()
    }

    func clear() {
        records.forEach { deleteImageFile($0) }
        records.removeAll()
        save()
    }

    // MARK: - Image files

    private func saveImage(_ image: UIImage, id: UUID) -> String? {
        let scaled = image.downscaled(maxDimension: storedImageMaxDimension)
        guard let data = scaled.jpegData(compressionQuality: 0.7) else { return nil }
        let name = "\(id.uuidString).jpg"
        let url = imagesDirectory.appendingPathComponent(name)
        do {
            try data.write(to: url, options: .atomic)
            return name
        } catch {
            return nil
        }
    }

    private func deleteImageFile(_ record: DescriptionRecord) {
        guard let name = record.imageFileName else { return }
        try? FileManager.default.removeItem(at: imagesDirectory.appendingPathComponent(name))
    }

    // MARK: - Persistence

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        if let decoded = try? JSONDecoder().decode([DescriptionRecord].self, from: data) {
            records = decoded
        }
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(records) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}

private extension UIImage {
    /// Returns a copy whose longest side is at most `maxDimension` points.
    func downscaled(maxDimension: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxDimension else { return self }
        let scale = maxDimension / longest
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: newSize, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
