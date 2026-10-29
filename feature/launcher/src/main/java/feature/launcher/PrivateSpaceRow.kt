package feature.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.LockOpen
import core.ui.composables.OutlinedText
import core.ui.composables.launchableIconPadding
import core.ui.composables.launchableIconSize

/** The actions the Private Space group offers. */
@Immutable
data class PrivateSpaceActions(
    val onUnlock: () -> Unit,
    val onLock: () -> Unit,
    /** Opens the system Private Space settings, or null when the system offers none. */
    val onSettings: (() -> Unit)? = null
)

/** The Private Space container row: a lock indicator, its name and its Locked/Unlocked state. */
@Composable
internal fun PrivateSpaceRow(isLocked: Boolean, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val label: String = stringResource(R.string.private_space)
    val state: String = stringResource(if (isLocked) R.string.private_space_locked else R.string.private_space_unlocked)
    val unlockLabel: String = stringResource(R.string.unlock_private_space)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLocked) Modifier.clickable(role = Role.Button, onClickLabel = unlockLabel, onClick = onUnlock) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = state
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrivateSpaceRowContent(
            imageVector = if (isLocked) FontAwesomeIcons.Solid.Lock else FontAwesomeIcons.Solid.LockOpen,
            label = label
        )
    }
}

/** The Lock and Settings action rows under the Unlocked Private Space's Apps. */
@Composable
internal fun PrivateSpaceActionRows(isLocked: Boolean, actions: PrivateSpaceActions, modifier: Modifier = Modifier) {
    if (isLocked) {
        return
    }
    val lockLabel: String = stringResource(R.string.lock_private_space)
    val settingsLabel: String = stringResource(R.string.private_space_settings)
    Column(modifier = modifier.fillMaxWidth()) {
        LauncherActionRow(
            imageVector = FontAwesomeIcons.Solid.Lock,
            label = lockLabel,
            onClick = actions.onLock,
            contentDescription = lockLabel
        )
        actions.onSettings?.let { onSettings ->
            LauncherActionRow(
                imageVector = FontAwesomeIcons.Solid.Cog,
                label = settingsLabel,
                onClick = onSettings,
                contentDescription = settingsLabel
            )
        }
    }
}

@Composable
private fun PrivateSpaceRowContent(imageVector: ImageVector, label: String) {
    Icon(
        modifier = Modifier.size(launchableIconSize).padding(launchableIconPadding),
        imageVector = imageVector,
        contentDescription = null
    )
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
        OutlinedText(text = label)
    }
}
