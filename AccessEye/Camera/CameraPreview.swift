//
//  CameraPreview.swift
//  AccessEye
//
//  A SwiftUI wrapper around an AVCaptureVideoPreviewLayer so the live camera
//  feed fills the screen behind the controls. (README §6.1.)
//
//  For a fully-blind user this preview is cosmetic, but it matters for low-vision
//  users and sighted helpers — and it confirms the camera is live.
//

import SwiftUI
import AVFoundation

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.videoPreviewLayer.session = session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.videoPreviewLayer.session = session
    }

    /// A UIView whose backing layer is an AVCaptureVideoPreviewLayer, so the
    /// preview resizes correctly without manual frame math.
    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer {
            layer as! AVCaptureVideoPreviewLayer
        }
    }
}
