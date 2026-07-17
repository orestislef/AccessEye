//
//  HistoryScreen.kt
//  AccessEye
//
//  Port of Views/HistoryView.swift. A list of past descriptions. Tap one to
//  hear it again (in the language it was made in); each row has an explicit
//  delete button (more discoverable for TalkBack than iOS swipe-to-delete);
//  clear all from the top bar. (User request: history.)
//

package gr.orestislef.accesseye.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.orestislef.accesseye.AppViewModel
import gr.orestislef.accesseye.history.DescriptionRecord
import gr.orestislef.accesseye.support.LocalizedUI
import gr.orestislef.accesseye.support.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(vm: AppViewModel, onDone: () -> Unit) {
    val records by vm.history.records.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val t = LocalizedUI.textFor(language)

    // System back = the Done button (this screen is a full-screen "sheet").
    BackHandler(onBack = onDone)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            TopBar(t = t, hasRecords = records.isNotEmpty(), vm = vm, onDone = onDone)

            if (records.isEmpty()) {
                EmptyState(t = t, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(records, key = { it.id }) { record ->
                        HistoryRow(record = record, vm = vm, t = t)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Title + Clear-history (only when there is something to clear) + Done. */
@Composable
private fun TopBar(t: UiText, hasRecords: Boolean, vm: AppViewModel, onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = t.history,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (hasRecords) {
            TextButton(
                onClick = { vm.history.clear() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(t.clearHistory)
            }
        }
        TextButton(onClick = onDone) {
            Text(t.done)
        }
    }
}

/** Mirrors iOS ContentUnavailableView: clock, "No descriptions yet", hint. */
@Composable
private fun EmptyState(t: UiText, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // One TalkBack element for the whole empty state.
            modifier = Modifier
                .padding(32.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null, // decorative
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = t.noHistory,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = t.tapToDescribe,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One saved description: thumbnail, text, language + date, delete. Tapping the
 * row replays the description in ITS language (iOS parity).
 */
@Composable
private fun HistoryRow(record: DescriptionRecord, vm: AppViewModel, t: UiText) {
    val view = LocalView.current
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }

    // Decode the thumbnail off the main thread; small JPEGs, so no sampling
    // needed (HistoryStore already stores them at max 768 px).
    val thumbnail by produceState<Bitmap?>(null, record.imageFileName) {
        value = withContext(Dispatchers.IO) {
            vm.history.imageFile(record)?.let { BitmapFactory.decodeFile(it.path) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            // clickable merges descendants into one TalkBack element; the
            // delete IconButton stays its own node (it's clickable itself).
            .clickable(role = Role.Button, onClickLabel = t.playsAgainHint) {
                vm.speak(record, view)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        thumbnail?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null, // decorative, like iOS accessibilityHidden
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = record.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = record.language.localizedName + " · " +
                    dateFormat.format(Date(record.dateEpochMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { vm.history.delete(record) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = t.delete,
            )
        }
    }
}
