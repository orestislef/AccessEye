//
//  OnboardingScreen.kt
//  AccessEye
//
//  Port of Views/OnboardingView.swift. First-launch flow that downloads the
//  on-device AI model once. Explains why a one-time download is needed, shows
//  live progress (percent, bytes, speed, ETA), and handles errors/retry. Fully
//  accessible: large text, high contrast, the whole progress block is a single
//  polite live region so TalkBack hears periodic "Downloading, N percent, time
//  left…" updates. (README §3 Risk #2.)
//
//  Android-only detail: the POST_NOTIFICATIONS runtime permission is requested
//  right before the first download starts, so the foreground download service
//  can show its progress notification. The download proceeds either way —
//  denial only suppresses the notification.
//
//  Gemma Terms compliance: whenever the download can be started, the screen
//  shows a notice that the model is provided under the Gemma Terms of Use, plus
//  a button that opens the full terms (GemmaTermsDialog). The call-to-action
//  reads "Agree and download": tapping it is the user's acceptance, recorded
//  once in the shared preferences ("gemmaTermsAccepted").
//

package gr.orestislef.accesseye.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.orestislef.accesseye.AccessEyeApp
import gr.orestislef.accesseye.ai.AppConfig
import gr.orestislef.accesseye.ai.ModelManager
import gr.orestislef.accesseye.support.UiText
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(modelManager: ModelManager, t: UiText) {
    val state by modelManager.state.collectAsStateWithLifecycle()
    val progress by modelManager.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showTermsDialog by rememberSaveable { mutableStateOf(false) }

    // Ask for notification permission right before the first download so the
    // foreground service can show progress. Download starts whatever the user
    // answers — the permission only gates the notification, never the feature.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { modelManager.download() }

    val startDownload: () -> Unit = {
        // Tapping "Agree and download" is the user's acceptance of the Gemma
        // Terms of Use. Record it (once is enough) in the same prefs the
        // ViewModel uses, before the first download begins.
        (context.applicationContext as AccessEyeApp).container.preferences
            .edit().putBoolean("gemmaTermsAccepted", true).apply()

        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            modelManager.download()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = Icons.Filled.ArrowCircleDown,
            contentDescription = null, // decorative, like iOS accessibilityHidden
            tint = Color.White,
            modifier = Modifier.size(72.dp),
        )

        Text(
            text = t.welcomeTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Text(
            text = t.welcomeBody.format(AppConfig.approxModelSizeText),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // Gemma Terms notice, shown whenever the download can be started, so
        // the user hears it before agreeing. One merged TalkBack node; the
        // full-terms button below stays its own focusable target.
        if (state !is ModelManager.State.Downloading &&
            state !is ModelManager.State.Available
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = t.modelTermsNotice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .semantics(mergeDescendants = true) {},
                )
                TextButton(
                    onClick = { showTermsDialog = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = t.viewGemmaTerms,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when (val s = state) {
            is ModelManager.State.Checking,
            is ModelManager.State.Missing -> DownloadButton(t = t, onClick = startDownload)

            is ModelManager.State.Downloading -> DownloadingBlock(
                fraction = s.progress,
                progress = progress,
                t = t,
                onCancel = { modelManager.cancel() },
            )

            // RootScreen swaps us out; show a confirmation in the meantime.
            is ModelManager.State.Available -> ReadyBadge(t)

            is ModelManager.State.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFE24B), // yellow: readable error on black
                    textAlign = TextAlign.Center,
                )
                DownloadButton(t = t, onClick = startDownload)
            }
        }

        Spacer(Modifier.weight(1f))
    }

    if (showTermsDialog) {
        GemmaTermsDialog(t = t, onDismiss = { showTermsDialog = false })
    }
}

// MARK: - Pieces

/** Big white-on-black call-to-action, min 56dp tall. */
@Composable
private fun DownloadButton(t: UiText, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // Custom click label = the a11y hint ("Downloads the AI model so
            // the app can work offline"); action = null keeps Button's own.
            .semantics { onClick(label = t.downloadModelHint, action = null) },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            // "Agree and download": pressing this is the acceptance of the
            // Gemma Terms of Use shown right above.
            text = t.agreeAndDownload,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Live progress: linear bar, huge percent, "downloaded / total", speed + ETA,
 * Cancel. The stats form ONE merged a11y node whose label is rebuilt from
 * UiText ("Downloading, 42 percent, time left: 3 min") and is a polite live
 * region so TalkBack announces updates periodically. Cancel is a clickable
 * child, so it stays its own focusable node.
 */
@Composable
private fun DownloadingBlock(
    fraction: Double,
    progress: ModelManager.DownloadProgress?,
    t: UiText,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val percent = (fraction * 100).toInt().coerceIn(0, 100)

    val a11yLabel = buildString {
        append(t.downloading)
        append(", ")
        append(t.percentLoaded.format(percent))
        progress?.etaSeconds?.let {
            append(", ")
            append(t.etaLabel)
            append(": ")
            append(etaText(it))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = a11yLabel
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
        )

        Text(
            text = "$percent%",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum", // tabular digits — no jitter
            ),
            color = Color.White,
        )

        progress?.let { p ->
            Text(
                text = Formatter.formatFileSize(context, p.downloadedBytes) +
                    " / " + Formatter.formatFileSize(context, p.totalBytes),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (p.bytesPerSecond > 0) {
                    StatChip(
                        icon = { Icon(Icons.Filled.Speed, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.85f)) },
                        text = Formatter.formatFileSize(context, p.bytesPerSecond.toLong()) + "/s",
                    )
                }
                p.etaSeconds?.let { eta ->
                    StatChip(
                        icon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.85f)) },
                        text = etaText(eta),
                    )
                }
            }
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = t.cancel,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun StatChip(icon: @Composable () -> Unit, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** Transient "done" badge shown for the instant before RootScreen swaps views. */
@Composable
private fun ReadyBadge(t: UiText) {
    val green = Color(0xFF6DD58C)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = green, modifier = Modifier.size(28.dp))
        Text(
            text = t.downloaded,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = green,
        )
    }
}

// MARK: - Formatting

/**
 * Compact remaining-time text, mirroring iOS etaText. Unit abbreviations are
 * kept numeric/short (h, min, s) so they read acceptably in all 9 languages;
 * the surrounding label (t.etaLabel) carries the localized meaning.
 */
private fun etaText(seconds: Double): String {
    val s = seconds.roundToInt()
    return when {
        s >= 3600 -> "${s / 3600} h ${(s % 3600) / 60} min"
        s >= 60 -> "${s / 60} min"
        else -> "${max(s, 1)} s"
    }
}
