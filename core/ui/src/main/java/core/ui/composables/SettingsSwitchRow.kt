package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/**
 * A row with a title, optional summary and an optional leading icon that toggles a switch. The
 * whole row toggles, unless [onClick] is set, in which case only the switch toggles and the row
 * opens [onClick].
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    containerColor: Color = settingsRowContainerColor,
    onClick: (() -> Unit)? = null
) {
    val rowSelectionModifier: Modifier = if (onClick == null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    val switchOnCheckedChange: ((Boolean) -> Unit)? = if (onClick == null) null else onCheckedChange
    SheetRow(
        modifier = rowSelectionModifier,
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
        trailingContent = { Switch(checked = checked, onCheckedChange = switchOnCheckedChange, enabled = enabled) },
        containerColor = containerColor
    )
}
