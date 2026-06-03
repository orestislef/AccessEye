//
//  ModelStore.swift
//  AccessEye
//
//  Knows WHERE the on-device model file lives and how to install it. The model
//  is too large to ship in the App Store binary, so it's downloaded once on
//  first launch and kept in Application Support (excluded from iCloud backup so
//  it doesn't bloat the user's backup). (README §3 Risk #2.)
//
//  Both `ModelManager` (downloads it) and `GemmaService` (loads it) go through
//  here, so there is a single source of truth for the path.
//

import Foundation

enum ModelStore {

    /// Directory holding the model, created on demand.
    static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory,
                                            in: .userDomainMask)[0]
        return base.appendingPathComponent("Model", isDirectory: true)
    }

    /// Final on-device location of the model file.
    static var fileURL: URL {
        directory.appendingPathComponent(AppConfig.modelFileName)
    }

    /// True once the model has been fully downloaded and installed.
    static var isInstalled: Bool {
        FileManager.default.fileExists(atPath: fileURL.path)
    }

    /// True when the only model available is one baked into the app bundle (dev
    /// builds). Such a model is read-only and cannot be deleted.
    static var isBundledOnly: Bool {
        !isInstalled && usableModelPath != nil
    }

    /// Size of the usable model on disk in bytes (downloaded or bundled; 0 if none).
    static var installedSizeBytes: Int64 {
        guard let path = usableModelPath else { return 0 }
        let values = try? URL(fileURLWithPath: path)
            .resourceValues(forKeys: [.fileSizeKey, .totalFileAllocatedSizeKey])
        return Int64(values?.totalFileAllocatedSize ?? values?.fileSize ?? 0)
    }

    /// A human-readable size string for the installed model.
    static var installedSizeText: String {
        ByteCountFormatter.string(fromByteCount: installedSizeBytes, countStyle: .file)
    }

    /// A path to a usable model: the downloaded copy if present, otherwise a
    /// copy bundled in the app (handy for dev builds — drag the model into the
    /// target and it's found without downloading). Returns nil if neither.
    static var usableModelPath: String? {
        if isInstalled { return fileURL.path }
        // Dev fallback: model dragged into the app bundle.
        for ext in ["litertlm", "task", "bin"] {
            let name = (AppConfig.modelFileName as NSString).deletingPathExtension
            if let path = Bundle.main.path(forResource: name, ofType: ext) {
                return path
            }
            if let path = Bundle.main.path(forResource: "model", ofType: ext) {
                return path
            }
        }
        return nil
    }

    /// Move a freshly downloaded temp file into its final location atomically.
    static func install(from tempURL: URL) throws {
        let fm = FileManager.default
        try fm.createDirectory(at: directory, withIntermediateDirectories: true)
        if fm.fileExists(atPath: fileURL.path) {
            try fm.removeItem(at: fileURL)
        }
        try fm.moveItem(at: tempURL, to: fileURL)
        excludeFromBackup(fileURL)
    }

    /// Remove the downloaded model (e.g. to free space or re-download).
    static func remove() {
        try? FileManager.default.removeItem(at: fileURL)
    }

    private static func excludeFromBackup(_ url: URL) {
        var url = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
    }
}
