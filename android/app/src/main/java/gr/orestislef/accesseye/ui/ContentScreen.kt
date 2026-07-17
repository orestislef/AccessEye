//
//  ContentScreen.kt
//  AccessEye
//
//  The main screen (port of Views/ContentView.swift): a full-screen live camera
//  that the user taps anywhere to capture, after which Gemma describes the
//  scene and the phone speaks it aloud. Designed audio-first for blind /
//  low-vision users (README §7):
//   - the ENTIRE screen is the capture button (huge tap target),
//   - a clearly-labeled "Describe" button for TalkBack users,
//   - spoken + haptic feedback on every step,
//   - History and Settings within easy reach.
//
//  Android-only details: the CAMERA runtime permission is requested here on
//  first entry (iOS asks inside CameraManager); Settings and History open as
//  full-screen surfaces instead of sheets (better for TalkBack), with the
//  system Back gesture closing them first.
//

package gr.orestislef.accesseye.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.orestislef.accesseye.AppViewModel
import gr.orestislef.accesseye.ai.ModelManager
import gr.orestislef.accesseye.camera.CameraPreview
import gr.orestislef.accesseye.support.LocalizedUI
import gr.orestislef.accesseye.support.UiText

/** Yellow that stays readable on black (same as the onboarding error color). */
private val WarningYellow = Color(0xFFFFE24B)

@Composable
fun ContentScreen(vm: AppViewModel, modelManager: ModelManager) {
    val readiness by vm.readiness.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val lastDescription by vm.lastDescription.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val permissionDenied by vm.camera.permissionDenied.collectAsStateWithLifecycle()

    val t = remember(language) { LocalizedUI.textFor(language) }
    val view = LocalView.current // haptics need a View (Haptics.kt)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    // Same rule as vm.canCapture, but derived from the collected states so the
    // UI is guaranteed to recompose exactly when the answer changes.
    val canCapture = readiness == AppViewModel.Readiness.Ready &&
        activity == AppViewModel.Activity.IDLE &&
        !permissionDenied

    // Mirror of iOS onAppear/onDisappear. onAppear starts the camera (it waits
    // for permission internally: CameraManager remembers the owner and binds
    // when onPermissionResult(true) arrives) and preloads the model.
    DisposableEffect(lifecycleOwner) {
        vm.onAppear(lifecycleOwner)
        onDispose { vm.onDisappear() }
    }

    // The runtime permission REQUEST lives here in the UI layer (contract);
    // the result feeds CameraManager, which mirrors the iOS requestAccess flow.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.camera.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            vm.camera.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera layer: full-bleed preview + a light scrim so white text stays
        // readable on bright scenes. Both decorative — hidden from TalkBack
        // (CameraPreview hides itself). Permission denied → plain black.
        if (!permissionDenied) {
            CameraPreview(vm.camera, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .semantics { hideFromAccessibility() },
            )
        }

        // Full-screen capture target for direct-touch users. Hidden from
        // TalkBack, which uses the explicit Describe button instead.
        if (canCapture) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { vm.captureAndDescribe(view) })
                    }
                    .semantics { hideFromAccessibility() },
            )
        }

        // Controls layer. No pointer input of its own, so taps on empty space
        // fall through to the capture target underneath.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            TopBar(
                t = t,
                languageName = language.localizedName,
                onHistory = { showHistory = true },
                onSettings = { showSettings = true },
            )

            Spacer(Modifier.weight(1f))

            if (activity == AppViewModel.Activity.DESCRIBING) {
                DescribingCard(t)
            } else {
                lastDescription?.let { text ->
                    DescriptionCard(
                        text = text,
                        speaking = activity == AppViewModel.Activity.SPEAKING,
                        t = t,
                        onToggle = { vm.toggleSpeech(view) },
                        onClear = { vm.clearCurrent(view) },
                    )
                }
            }

            DescribeButton(
                t = t,
                enabled = canCapture,
                onClick = { vm.captureAndDescribe(view) },
            )
        }

        // Full-screen overlays (above everything). Each swallows taps so the
        // controls underneath can't be hit while the overlay explains itself.
        if (readiness == AppViewModel.Readiness.Preparing) {
            PreparingOverlay(t)
        }
        (readiness as? AppViewModel.Readiness.Failed)?.let { failed ->
            FailedOverlay(message = failed.message, t = t, onRetry = { vm.retry() })
        }
        if (permissionDenied) {
            PermissionOverlay(t = t) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            }
        }
    }

    // Settings / History as full-screen surfaces on top (iOS presents sheets;
    // full screens work better with TalkBack). Back closes an open one first.
    if (showSettings) {
        SettingsScreen(vm = vm, modelManager = modelManager, onDone = { showSettings = false })
    }
    if (showHistory) {
        HistoryScreen(vm = vm, onDone = { showHistory = false })
    }
    BackHandler(enabled = showSettings || showHistory) {
        if (showHistory) showHistory = false else showSettings = false
    }
}

// MARK: - Top bar

@Composable
private fun TopBar(
    t: UiText,
    languageName: String,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Current language chip (endonym), translucent capsule like iOS.
        Text(
            text = languageName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = "${t.language}: $languageName" },
        )

        Spacer(Modifier.weight(1f))

        TopIconButton(icon = Icons.Filled.History, label = t.history, onClick = onHistory)
        TopIconButton(icon = Icons.Filled.Settings, label = t.settings, onClick = onSettings)
    }
}

/** 48dp icon button on a translucent circle (iOS ultraThinMaterial stand-in). */
@Composable
private fun TopIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White)
    }
}

// MARK: - Status card

/** "Looking…" while Gemma works. Polite live region so TalkBack hears it. */
@Composable
private fun DescribingCard(t: UiText) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = t.describing
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = t.describing,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

/**
 * The current description: tap it to replay or stop the speech; the ✕
 * dismisses it. (User request: tap text to speak/stop + a way to clear.)
 */
@Composable
private fun DescriptionCard(
    text: String,
    speaking: Boolean,
    t: UiText,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                // One merged node: label is the description itself, the click
                // label reads as the hint ("Stop" / "Tap to hear again").
                .semantics(mergeDescendants = true) { contentDescription = text }
                .clickable(
                    onClickLabel = if (speaking) t.stop else t.tapToHear,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(16.dp)
                .padding(end = 24.dp), // room for the ✕ (iOS parity)
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Fixed-width slot so the text doesn't shift when the icon swaps.
            Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (speaking) Icons.AutoMirrored.Filled.VolumeUp
                    else Icons.Filled.PlayCircle,
                    contentDescription = null, // covered by the merged label
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            // Scroll long descriptions instead of truncating.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        }

        IconButton(
            onClick = onClear,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = t.clear,
                tint = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

// MARK: - Describe button

/** The explicit capture button — the primary control for TalkBack users. */
@Composable
private fun DescribeButton(t: UiText, enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primaryContainer
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            // iOS dims the same accent to 50% rather than swapping colors.
            .alpha(if (enabled) 1f else 0.5f)
            // Custom click label = the a11y hint, like iOS accessibilityHint.
            .semantics { onClick(label = t.describeHint, action = null) },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White,
            disabledContainerColor = accent,
            disabledContentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null, // the text label carries the meaning
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = t.tapToDescribe,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

// MARK: - Overlays

/** Swallows taps so an overlay blocks the controls underneath (iOS parity). */
private fun Modifier.blockTouches(): Modifier =
    pointerInput(Unit) { detectTapGestures { } }

/** Model is loading: scrim + spinner + "Getting things ready…". */
@Composable
private fun PreparingOverlay(t: UiText) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .blockTouches()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = t.gettingReady
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(44.dp))
            Text(
                text = t.gettingReady,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Model failed to load: message + "Try again". */
@Composable
private fun FailedOverlay(message: String, t: UiText, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .blockTouches()
            // Merged label = the failure message; the retry button stays its
            // own focusable node because it is a clickable child.
            .semantics(mergeDescendants = true) { contentDescription = message },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = WarningYellow,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = t.tryAgain,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/** Camera permission denied: explain and route to the app's system settings. */
@Composable
private fun PermissionOverlay(t: UiText, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .blockTouches()
            .semantics(mergeDescendants = true) { contentDescription = t.cameraNeeded },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp),
            )
            Text(
                text = t.cameraNeeded,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = t.openSettings,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
