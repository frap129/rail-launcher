package core.ui.popups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.data.shortcuts.ShortcutLaunchable
import core.ui.R

private val emptyStatePadding = 16.dp
private val addAppsButtonTopPadding = 8.dp

/** A Shortcut's one-line label: the long label, falling back to the short one when it overflows. */
@Composable
fun PopupShortcutLabel(shortcut: ShortcutLaunchable, modifier: Modifier = Modifier) {
    var useLongLabel: Boolean by remember(shortcut.longLabel, shortcut.shortLabel) { mutableStateOf(true) }
    val label: String = if (useLongLabel) shortcut.longLabel else shortcut.shortLabel
    Text(
        modifier = modifier,
        text = label.ifBlank { shortcut.shortLabel },
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (useLongLabel && result.hasVisualOverflow && shortcut.shortLabel != shortcut.longLabel) {
                useLongLabel = false
            }
        }
    )
}

/** A centred empty-state message with optional Turn on and Add apps actions. */
@Composable
fun PopupEmptyState(messageResId: Int, onTurnOn: (() -> Unit)? = null, onAddApps: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(emptyStatePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onTurnOn != null) {
            TextButton(onClick = onTurnOn) {
                Text(stringResource(R.string.turn_on))
            }
        }
        if (onAddApps != null) {
            Spacer(Modifier.height(addAppsButtonTopPadding))
            FilledTonalButton(onClick = onAddApps) {
                Text(stringResource(R.string.add_apps))
            }
        }
    }
}
