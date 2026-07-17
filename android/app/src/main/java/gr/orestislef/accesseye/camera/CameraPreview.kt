//
// CameraPreview.kt
// AccessEye
//
// A Compose wrapper around CameraX's PreviewView so the live camera feed fills
// the screen behind the controls. Port of Camera/CameraPreview.swift.
//
// For a fully-blind user this preview is cosmetic, but it matters for low-vision
// users and sighted helpers — and it confirms the camera is live. It is therefore
// completely hidden from TalkBack (decorative).
//

package gr.orestislef.accesseye.camera

import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreview(cameraManager: CameraManager, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // Fill the screen, cropping edges — same as iOS resizeAspectFill.
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // Belt and braces: hide the Android view itself from a11y too.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                cameraManager.preview.setSurfaceProvider(surfaceProvider)
            }
        },
        onRelease = {
            cameraManager.preview.setSurfaceProvider(null)
        },
        modifier = modifier.semantics { hideFromAccessibility() },
    )
}
