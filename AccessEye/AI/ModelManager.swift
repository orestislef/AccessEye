//
//  ModelManager.swift
//  AccessEye
//
//  Handles the one-time, on-first-launch download of the Gemma model with live
//  progress, so onboarding can show "Downloading… 42%". Everything stays on the
//  device; the only network use in the whole app is fetching this model file
//  once. (README §3 Risk #2.)
//

import Foundation
import Combine

@MainActor
final class ModelManager: NSObject, ObservableObject {

    enum State: Equatable {
        case checking
        case missing
        case downloading(progress: Double)   // 0...1
        case available
        case failed(String)
    }

    /// Rich progress for the download UI: percent, bytes, speed and ETA.
    struct DownloadProgress: Equatable {
        var fraction: Double            // 0...1
        var downloadedBytes: Int64
        var totalBytes: Int64
        var bytesPerSecond: Double
        var etaSeconds: Double?
    }

    @Published private(set) var state: State = .checking
    @Published private(set) var progress: DownloadProgress?

    private var session: URLSession?
    private var task: URLSessionDownloadTask?

    // Sampling for speed/ETA (main-actor only).
    private var sampleTime: Date?
    private var sampleBytes: Int64 = 0
    private var smoothedSpeed: Double = 0
    private var lastUIUpdate: Date?

    /// Check what we already have. Call on launch.
    func refresh() {
        // Mock mode needs no model at all.
        guard AppConfig.requiresModelDownload else {
            state = .available
            return
        }
        // Available if downloaded OR bundled into the app (dev builds).
        state = (ModelStore.usableModelPath != nil) ? .available : .missing
    }

    /// Start (or restart) the model download.
    func download() {
        guard let url = AppConfig.modelDownloadURL else {
            state = .failed("No model download source is configured yet.")
            return
        }
        // Tear down any previous attempt.
        task?.cancel()
        resetSampling()
        state = .downloading(progress: 0)
        progress = DownloadProgress(fraction: 0, downloadedBytes: 0,
                                    totalBytes: 0, bytesPerSecond: 0, etaSeconds: nil)

        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        self.session = session

        let task = session.downloadTask(with: url)
        self.task = task
        task.resume()
    }

    /// Cancel an in-progress download.
    func cancel() {
        task?.cancel()
        task = nil
        progress = nil
        resetSampling()
        state = ModelStore.isInstalled ? .available : .missing
    }

    /// Delete the model and return to the "missing" state.
    func deleteModel() {
        ModelStore.remove()
        progress = nil
        refresh()
    }

    // MARK: - Progress sampling

    private func resetSampling() {
        sampleTime = nil
        sampleBytes = 0
        smoothedSpeed = 0
        lastUIUpdate = nil
    }

    /// Update progress + speed/ETA from a download callback (throttled).
    fileprivate func updateProgress(written: Int64, total: Int64) {
        guard case .downloading = state else { return }
        let now = Date()

        // Sample speed roughly twice a second for a stable estimate.
        if let st = sampleTime {
            let dt = now.timeIntervalSince(st)
            if dt >= 0.5 {
                let inst = Double(written - sampleBytes) / dt
                smoothedSpeed = smoothedSpeed == 0 ? inst : (0.6 * smoothedSpeed + 0.4 * inst)
                sampleTime = now
                sampleBytes = written
            }
        } else {
            sampleTime = now
            sampleBytes = written
        }

        // Throttle UI refreshes (~3/sec), but always show the final 100%.
        let isComplete = total > 0 && written >= total
        if let lu = lastUIUpdate, now.timeIntervalSince(lu) < 0.3, !isComplete {
            return
        }
        lastUIUpdate = now

        let fraction = total > 0 ? Double(written) / Double(total) : 0
        let eta: Double? = (smoothedSpeed > 0 && total > 0)
            ? Double(total - written) / smoothedSpeed : nil
        progress = DownloadProgress(fraction: fraction, downloadedBytes: written,
                                    totalBytes: max(total, 0),
                                    bytesPerSecond: smoothedSpeed, etaSeconds: eta)
        state = .downloading(progress: fraction)
    }
}

extension ModelManager: URLSessionDownloadDelegate {

    nonisolated func urlSession(_ session: URLSession,
                                downloadTask: URLSessionDownloadTask,
                                didWriteData bytesWritten: Int64,
                                totalBytesWritten: Int64,
                                totalBytesExpectedToWrite: Int64) {
        Task { @MainActor in
            self.updateProgress(written: totalBytesWritten, total: totalBytesExpectedToWrite)
        }
    }

    nonisolated func urlSession(_ session: URLSession,
                                downloadTask: URLSessionDownloadTask,
                                didFinishDownloadingTo location: URL) {
        // The temp file is only valid inside this callback — install it now.
        do {
            try ModelStore.install(from: location)
            Task { @MainActor in self.state = .available }
        } catch {
            Task { @MainActor in
                self.state = .failed("Could not save the model: \(error.localizedDescription)")
            }
        }
    }

    nonisolated func urlSession(_ session: URLSession,
                                task: URLSessionTask,
                                didCompleteWithError error: Error?) {
        guard let error else { return }
        // Ignore explicit cancellations (handled in cancel()).
        let nsError = error as NSError
        if nsError.code == NSURLErrorCancelled { return }
        Task { @MainActor in
            if !ModelStore.isInstalled {
                self.state = .failed(error.localizedDescription)
            }
        }
    }
}
