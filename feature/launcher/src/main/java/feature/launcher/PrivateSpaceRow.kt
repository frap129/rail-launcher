package feature.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.LockOpen
import core.ui.composables.OutlinedText
import core.ui.composables.launchableIconPadding
import core.ui.composables.launchableIconSize

/** The actions the Private Space container row offers. */
@Immutable
data class PrivateSpaceActions(val onUnlock: () -> Unit, val onLock: () -> Unit)

/** The Private Space container row: a lock indicator, its name and, while unlocked, a Lock action. */
@Composable
internal fun PrivateSpaceRow(isLocked: Boolean, actions: PrivateSpaceActions, modifier: Modifier = Modifier) {
    val label: String = stringResource(R.string.private_space)
    val state: String = stringResource(if (isLocked) R.string.private_space_locked else R.string.private_space_unlocked)
    val unlockLabel: String = stringResource(R.string.unlock_private_space)
    val lockLabel: String = stringResource(R.string.lock_private_space)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (isLocked) Modifier.clickable(onClickLabel = unlockLabel, onClick = actions.onUnlock) else Modifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = state
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(launchableIconSize).padding(launchableIconPadding),
                imageVector = if (isLocked) FontAwesomeIcons.Solid.Lock else FontAwesomeIcons.Solid.LockOpen,
                contentDescription = null
            )
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
                OutlinedText(text = label)
            }
        }
        if (!isLocked) {
            IconButton(onClick = actions.onLock) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Lock,
                    contentDescription = lockLabel
                )
            }
        }
    }
}
