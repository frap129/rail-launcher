package feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val navigationIconSize: Dp = 64.dp
private val navigationIconPadding: Dp = 16.dp
private val snackbarPadding: Dp = 16.dp

/** The section title above a group of settings rows. */
@Composable
internal fun SettingsSectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
}

/** A Main-screen row that opens another Settings screen. */
@Composable
internal fun SettingsNavigationRow(@StringRes title: Int, @StringRes summary: Int, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        leadingContent = {
            Icon(
                modifier = Modifier.size(navigationIconSize).padding(navigationIconPadding),
                imageVector = icon,
                contentDescription = stringResource(title)
            )
        }
    )
}

@Composable
internal fun SettingsActionRow(title: String, value: String, onClick: () -> Unit, enabled: Boolean = true, supportingText: String? = null) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = if (value.isNotEmpty() || supportingText != null) {
            { Text(value.ifEmpty { supportingText.orEmpty() }) }
        } else {
            null
        }
    )
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null
) {
    ListItem(
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        ),
        headlineContent = { Text(title) },
        supportingContent = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    )
}

@Composable
internal fun SettingsDestructiveRow(title: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(text = title, color = MaterialTheme.colorScheme.error) }
    )
}

/** The bottom-centred Snackbar host every Settings screen shows in its content Box. */
@Composable
internal fun BoxScope.SettingsSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(snackbarPadding)
    )
}
