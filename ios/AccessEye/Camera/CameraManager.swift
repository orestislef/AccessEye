//
//  CameraManager.swift
//  AccessEye
//
//  Thin AVFoundation wrapper: runs a live capture session and grabs ONE still
//  frame on demand (tap-to-capture). The session is configured and driven on a
//  dedicated queue; @Published state is always updated on the main actor so
//  SwiftUI stays happy. (README §6.1, M1.)
//
//  Declared `nonisolated` so the capture-delegate callbacks and session work can
//  run off the main actor; UI-facing state is pushed back to main explicitly.
//

import AVFoundation
import UIKit
import Combine

nonisolated final class CameraManager: NSObject, ObservableObject {

    /// The session SwiftUI's preview layer renders.
    let session = AVCaptureSession()

    /// Whether camera access was denied/restricted, so the UI can explain it.
    @Published var permissionDenied = false

    /// Whether the session is configured and running.
    @Published var isRunning = false

    private let sessionQueue = DispatchQueue(label: "gr.orestislef.AccessEye.camera")
    private let photoOutput = AVCapturePhotoOutput()
    private var isConfigured = false

    /// Completion for the in-flight capture, if any. Touched only on sessionQueue.
    private var captureCompletion: ((UIImage?) -> Void)?

    // MARK: - Lifecycle

    /// Request permission (if needed), configure once, and start the preview.
    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndRun()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard let self else { return }
                if granted {
                    self.configureAndRun()
                } else {
                    self.setPermissionDenied(true)
                }
            }
        case .denied, .restricted:
            setPermissionDenied(true)
        @unknown default:
            setPermissionDenied(true)
        }
    }

    /// Stop the preview (e.g. when leaving the screen).
    func stop() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
            self.setRunning(false)
        }
    }

    // MARK: - Capture

    /// Capture a single still frame. `completion` is called on the main actor.
    func capturePhoto(completion: @escaping (UIImage?) -> Void) {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else {
                Task { @MainActor in completion(nil) }
                return
            }
            self.captureCompletion = completion
            let settings = AVCapturePhotoSettings()
            self.photoOutput.capturePhoto(with: settings, delegate: self)
        }
    }

    // MARK: - Internals

    private func configureAndRun() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            if !self.isConfigured {
                self.configureSession()
            }
            guard self.isConfigured, !self.session.isRunning else { return }
            self.session.startRunning()
            self.setRunning(self.session.isRunning)
        }
    }

    /// Build the session graph: back wide camera input → photo output.
    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .photo

        guard
            let device = AVCaptureDevice.default(.builtInWideAngleCamera,
                                                 for: .video, position: .back),
            let input = try? AVCaptureDeviceInput(device: device),
            session.canAddInput(input)
        else {
            session.commitConfiguration()
            setPermissionDenied(true)
            return
        }
        session.addInput(input)

        guard session.canAddOutput(photoOutput) else {
            session.commitConfiguration()
            return
        }
        session.addOutput(photoOutput)

        session.commitConfiguration()
        isConfigured = true
    }

    private func setPermissionDenied(_ value: Bool) {
        Task { @MainActor in self.permissionDenied = value }
    }

    private func setRunning(_ value: Bool) {
        Task { @MainActor in self.isRunning = value }
    }
}

// MARK: - AVCapturePhotoCaptureDelegate

extension CameraManager: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput,
                     didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {
        let completion = captureCompletion
        captureCompletion = nil

        let image: UIImage? = {
            guard error == nil,
                  let data = photo.fileDataRepresentation(),
                  let img = UIImage(data: data) else { return nil }
            return img
        }()

        Task { @MainActor in completion?(image) }
    }
}
