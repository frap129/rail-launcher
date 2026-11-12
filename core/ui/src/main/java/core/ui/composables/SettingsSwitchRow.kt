package core.ui.composables

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/** A settings row with a title, optional summary and an optional leading icon that toggles a switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    containerColor: Color = settingsRowContainerColor
) {
    SheetRow(
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
        leadingContent = leadingIcon?.let { icon ->
            {
                RowLeadingIcon(imageVector = icon)
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        containerColor = containerColor
    )
}
