//
//  SettingsScreen.kt
//  AccessEye
//
//  Port of Views/SettingsView.swift. Language, speech speed, and AI-model
//  management, presented full-screen over the main screen (full screens keep
//  TalkBack focus simpler than sheets) with a Done action and back-press to
//  close. (README §6.4, §6.6, §3 Risk #2.)
//
//  Android-only addition: a per-language voice check. iOS ships offline voices
//  for every language; on Android the user may need to install one, so when the
//  chosen language has no offline TTS voice we say so and link straight to the
//  voice-install screen.
//
//  Every wording comes from LocalizedUI in the CHOSEN language; every control
//  is a 48dp+ target with proper roles/hints for TalkBack.
//

package gr.orestislef.accesseye.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.orestislef.accesseye.AppViewModel
import gr.orestislef.accesseye.ai.AppConfig
import gr.orestislef.accesseye.ai.ModelManager
import gr.orestislef.accesseye.model.Language
import gr.orestislef.accesseye.support.LocalizedUI
import gr.orestislef.accesseye.support.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: AppViewModel, modelManager: ModelManager, onDone: () -> Unit) {
    val language by vm.language.collectAsStateWithLifecycle()
    val speechRate by vm.speechRate.collectAsStateWithLifecycle()
    val t = remember(language) { LocalizedUI.textFor(language) }

    // System back closes Settings just like the Done button (sheet-dismiss parity).
    BackHandler(onBack = onDone)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(t = t, onDone = onDone)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                LanguageSection(t = t, selected = language, onSelect = vm::setLanguage)
                SpeechSection(
                    vm = vm,
                    t = t,
                    language = language,
                    speechRate = speechRate,
                )
                ModelSection(modelManager = modelManager, t = t, onDone = onDone)
                LicensesSection(t = t)
            }
        }
    }
}

// MARK: - Top bar

/** Title (a TalkBack heading) + trailing Done, like the iOS navigation bar. */
@Composable
private fun TopBar(t: UiText, onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = t.settings,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(
            onClick = onDone,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(t.done, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// MARK: - Language

/**
 * One radio row per supported language, labelled with its endonym
 * ("Ελληνικά", "العربية", …) so users find their own language even when the
 * UI currently speaks another one. The whole row is the selectable target.
 */
@Composable
private fun LanguageSection(t: UiText, selected: Language, onSelect: (Language) -> Unit) {
    SectionHeader(t.language)
    Language.entries.forEach { lang ->
        val isSelected = lang == selected
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                ) { onSelect(lang) }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The row carries the selectable semantics; a second click target
            // inside it would only confuse TalkBack.
            RadioButton(selected = isSelected, onClick = null)
            Spacer(Modifier.width(16.dp))
            Text(
                text = lang.localizedName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// MARK: - Speech speed

/**
 * The rate slider (walk → run stands in for iOS's tortoise → hare) plus the
 * Android-only voice check: no offline voice installed for the chosen language
 * → explain and link to the TTS voice-install screen.
 */
@Composable
private fun SpeechSection(
    vm: AppViewModel,
    t: UiText,
    language: Language,
    speechRate: Float,
) {
    SectionHeader(t.speechSpeed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Decorative endpoints (contentDescription = null hides them from
        // TalkBack); the slider itself carries the accessible label and value.
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Slider(
            value = speechRate,
            onValueChange = vm::setSpeechRate,
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = t.speechSpeed
                    stateDescription = t.percentLoaded.format((speechRate * 100).toInt())
                },
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }

    // Re-check whenever the chosen language changes (also re-runs after Settings
    // is reopened, catching a voice the user just installed).
    val voiceAvailable = remember(language) { vm.speaker.isVoiceAvailable(language) }
    if (!voiceAvailable) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = t.voiceMissing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { vm.speaker.openTtsSettings() },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(t.installVoice, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// MARK: - AI model

/**
 * Model file, install status and the download / delete actions. Deleting asks
 * for confirmation, then closes Settings — RootScreen sees the state flip to
 * Missing and returns to onboarding, which offers the re-download.
 */
@Composable
private fun ModelSection(modelManager: ModelManager, t: UiText, onDone: () -> Unit) {
    if (!AppConfig.requiresModelDownload) return // mock build: nothing to manage

    val state by modelManager.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Reading the size stats the multi-GB file on disk — keep it off the main
    // thread and refresh whenever the install state changes.
    val installedSizeText by produceState(initialValue = "", state) {
        value = withContext(Dispatchers.IO) { modelManager.store.installedSizeText }
    }

    SectionHeader(t.aiModel)
    LabeledRow(label = t.aiModel, value = AppConfig.modelFileName)

    when (val s = state) {
        is ModelManager.State.Available -> {
            LabeledRow(label = t.status, value = t.downloaded)
            LabeledRow(label = t.sizeOnDevice, value = installedSizeText)
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .heightIn(min = 48.dp)
                    // Custom click label = the a11y hint ("Frees up space…");
                    // action = null keeps the button's own click handling.
                    .semantics { onClick(label = t.deleteModelHint, action = null) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(t.deleteModel, style = MaterialTheme.typography.titleMedium)
            }
        }

        is ModelManager.State.Downloading -> {
            LabeledRow(
                label = t.status,
                value = "${t.downloading} ${(s.progress * 100).toInt()}%",
            )
        }

        else -> { // Checking / Missing / Failed
            LabeledRow(label = t.status, value = t.notInstalled)
            Button(
                onClick = { modelManager.download() },
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowCircleDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(t.downloadModel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(t.deleteModelConfirmTitle) },
            text = { Text(t.deleteModelConfirmBody.format(installedSizeText)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        modelManager.deleteModel()
                        onDone() // back to onboarding, which prompts re-download
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(t.deleteModel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(t.cancel)
                }
            },
        )
    }
}

// MARK: - Licenses

/**
 * The Gemma Terms of Use, reachable at any time (not only during onboarding).
 * The row opens the same GemmaTermsDialog the onboarding screen uses; the
 * caption below it is the exact Notice sentence the terms require, kept in
 * English on purpose (it is a legal notice, not UI copy).
 */
@Composable
private fun LicensesSection(t: UiText) {
    var showTerms by remember { mutableStateOf(false) }

    SectionHeader(t.licenses)
    TextButton(
        onClick = { showTerms = true },
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .heightIn(min = 48.dp),
    ) {
        Text(t.viewGemmaTerms, style = MaterialTheme.typography.titleMedium)
    }
    Text(
        text = "Gemma is provided under and subject to the Gemma Terms of Use " +
            "found at ai.google.dev/gemma/terms",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )

    if (showTerms) {
        GemmaTermsDialog(t = t, onDismiss = { showTerms = false })
    }
}

// MARK: - Building blocks (the Compose stand-ins for iOS Form section/rows)

/** Section title in the accent color, a TalkBack heading like iOS sections. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Label + trailing value, read by TalkBack as one item (iOS LabeledContent). */
@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
