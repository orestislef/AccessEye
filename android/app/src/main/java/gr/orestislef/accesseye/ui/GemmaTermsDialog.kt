//
//  GemmaTermsDialog.kt
//  AccessEye
//
//  Full-screen dialog that shows the complete Gemma Terms of Use from
//  res/raw/gemma_terms.txt. Shared by OnboardingScreen (before the first model
//  download) and SettingsScreen (the Licenses section), so both surfaces show
//  exactly the same text. The file is read off the main thread; the text is
//  selectable; the title is a TalkBack heading and Done (48dp+) closes it, as
//  does the system back gesture via the dialog's dismiss request.
//

package gr.orestislef.accesseye.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import gr.orestislef.accesseye.R
import gr.orestislef.accesseye.support.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GemmaTermsDialog(t: UiText, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // The terms file is a long legal text; read it off the main thread once.
    val termsText by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.gemma_terms)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                // Title bar mirrors SettingsScreen's: heading + trailing Done.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = t.viewGemmaTerms,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(t.done, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Selectable so users can copy passages (or share them with a
                // helper); TalkBack reads the whole document paragraph by
                // paragraph as usual.
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = termsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
