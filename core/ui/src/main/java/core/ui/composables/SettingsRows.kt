package core.ui.composables

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.ui.R as CoreUiR

private val navigationIconSize: Dp = 64.dp
private val settingsSectionTitleBottomPadding: Dp = 8.dp
private val singleChoiceRowVerticalPadding: Dp = 12.dp

/** The padding around Settings content that is not a row, such as an empty state or a search field. */
val settingsContentPadding: Dp = 16.dp

/** The text a Settings list shows when it has no rows. */
@Composable
fun SettingsEmptyState(@StringRes text: Int, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(
        text = stringResource(text),
        modifier = modifier.padding(settingsContentPadding),
        style = style
    )
}

/** The section title above a group of settings rows. */
@Composable
fun SettingsSectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(
            start = settingsContentPadding,
            top = settingsContentPadding,
            end = settingsContentPadding,
            bottom = settingsSectionTitleBottomPadding
        ),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
}

/** A Main-screen row that opens another Settings screen. */
@Composable
fun SettingsNavigationRow(@StringRes title: Int, @StringRes summary: Int, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        leadingContent = {
            Icon(
                modifier = Modifier.size(navigationIconSize).padding(settingsContentPadding),
                imageVector = icon,
                contentDescription = stringResource(title)
            )
        }
    )
}

@Composable
fun SettingsActionRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    containerColor: Color = settingsRowContainerColor
) {
    SheetRow(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = if (value.isNotEmpty() || supportingText != null) {
            { Text(value.ifEmpty { supportingText.orEmpty() }) }
        } else {
            null
        },
        containerColor = containerColor
    )
}

@Composable
fun SettingsDestructiveRow(title: String, onClick: () -> Unit, containerColor: Color = settingsRowContainerColor) {
    SheetRow(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(text = title, color = MaterialTheme.colorScheme.error) },
        containerColor = containerColor
    )
}

/** A [SettingsDestructiveRow] that runs [onConfirm] only after the user confirms the [DestructiveConfirmationDialog]. */
@Composable
fun SettingsConfirmedDestructiveRow(title: String, dialogTitle: String, dialogBody: String, onConfirm: () -> Unit) {
    var showConfirmation: Boolean by remember { mutableStateOf(false) }
    SettingsDestructiveRow(title = title, onClick = { showConfirmation = true })
    if (showConfirmation) {
        DestructiveConfirmationDialog(
            title = dialogTitle,
            body = dialogBody,
            confirmLabel = title,
            onConfirm = {
                onConfirm()
                showConfirmation = false
            },
            onDismiss = { showConfirmation = false }
        )
    }
}

/** The bottom-centred Snackbar host every Settings screen shows in its content Box. */
@Composable
fun BoxScope.SettingsSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(settingsContentPadding)
    )
}

data class SingleChoiceOption<T>(val value: T, val label: String, val enabled: Boolean = true)

@Composable
fun <T> SingleChoiceDialog(title: String, options: List<SingleChoiceOption<T>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.value == selected,
                                enabled = option.enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.value) }
                            )
                            .padding(vertical = singleChoiceRowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option.value == selected, onClick = null, enabled = option.enabled)
                        Text(text = option.label, modifier = Modifier.padding(start = settingsContentPadding))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}
