//
// CameraManager.kt
// AccessEye
//
// Thin CameraX wrapper: runs a live preview and grabs ONE still frame on demand
// (tap-to-describe). Port of Camera/CameraManager.swift. All camera work happens
// off the main thread (CameraX executors + coroutines); UI-facing state is
// exposed as StateFlows so Compose stays happy.
//
// Unlike iOS, the runtime permission REQUEST lives in the UI layer
// (rememberLauncherForActivityResult) — the result is fed back here via
// onPermissionResult(granted), which mirrors the iOS requestAccess callback.
//

package gr.orestislef.accesseye.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class CameraManager(private val context: Context) {

    /// Whether camera access was denied, so the UI can explain it (spoken + shown).
    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

    /// Whether the use cases are bound and the preview is live.
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /// The Preview use case CameraPreview attaches its PreviewView surface to.
    /// Created eagerly so the composable can attach before/after binding freely.
    val preview: Preview = Preview.Builder().build()

    /// Still capture tuned for speed over quality — a blind user waiting for a
    /// description cares about latency, and Gemma downscales the image anyway.
    private val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    // Binding must happen on the main thread; keep our own scope so start/stop
    // don't depend on the caller's.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bindJob: Job? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /// The lifecycle we should bind to once permission arrives (start() may run
    /// before the permission dialog is answered).
    private var pendingOwner: LifecycleOwner? = null

    private val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    // MARK: - Lifecycle

    /// Bind preview + still capture to the back camera if permission is already
    /// granted; otherwise remember the owner and wait for onPermissionResult.
    fun start(lifecycleOwner: LifecycleOwner) {
        pendingOwner = lifecycleOwner
        if (hasPermission) {
            _permissionDenied.value = false
            bind(lifecycleOwner)
        }
        // else: the UI layer is requesting permission; onPermissionResult(true)
        // will bind with the owner we just stored.
    }

    /// Result of the UI-layer permission request (mirrors iOS requestAccess).
    fun onPermissionResult(granted: Boolean) {
        _permissionDenied.value = !granted
        if (granted) pendingOwner?.let { bind(it) }
    }

    /// Stop the preview (e.g. when leaving the screen). Safe to call repeatedly.
    fun stop() {
        bindJob?.cancel()
        bindJob = null
        pendingOwner = null
        cameraProvider?.unbindAll()
        _isRunning.value = false
    }

    // MARK: - Capture

    /// Capture a single still frame, rotation applied, or null on any failure.
    /// Guard: capturing while the camera is not running returns null (mirrors iOS).
    suspend fun capturePhoto(): Bitmap? {
        if (!_isRunning.value) return null
        val image = awaitStill() ?: return null
        // Decode + rotate off the main thread; frames can be large.
        return withContext(Dispatchers.Default) {
            try {
                image.use { proxy ->
                    val degrees = proxy.imageInfo.rotationDegrees
                    val bitmap = proxy.toBitmap()
                    if (degrees == 0) bitmap
                    else Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height,
                        Matrix().apply { postRotate(degrees.toFloat()) }, true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // failure surfaces as a spoken retry upstream
            }
        }
    }

    // MARK: - Internals

    /// Wrap ImageCapture's callback API in a coroutine. Never throws — a failed
    /// capture resumes with null so callers get the same "no photo" path as iOS.
    private suspend fun awaitStill(): ImageProxy? = suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    if (cont.isActive) cont.resume(image) else image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    /// Get the process-wide provider and (re)bind our use cases to the back camera.
    private fun bind(owner: LifecycleOwner) {
        bindJob?.cancel()
        bindJob = scope.launch {
            try {
                val provider = ProcessCameraProvider.awaitInstance(context)
                cameraProvider = provider
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
                _isRunning.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No usable back camera / bind failure: stay not-running so the
                // capture guard returns null and the UI speaks a retry.
                _isRunning.value = false
            }
        }
    }
}
