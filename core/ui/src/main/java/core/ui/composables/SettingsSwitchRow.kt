package core.ui.composables

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import core.ui.layout.compactIconSize

/** A settings row with a title, optional summary and an optional leading icon that toggles a switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null
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
        leadingContent = leadingIcon?.let { icon ->
            {
                Icon(
                    modifier = Modifier.size(compactIconSize),
                    imageVector = icon,
                    contentDescription = null
                )
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    )
}
