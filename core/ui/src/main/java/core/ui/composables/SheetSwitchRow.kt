package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val sheetRowTextEndPadding = 12.dp

/**
 * One switch row in a launcher sheet: an icon, a label, optional supporting
 * text and a switch. The whole row toggles, unless [onClick] is set, in which
 * case only the switch toggles and the row opens [onClick].
 */
@Composable
fun SheetSwitchRow(
    imageVector: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val rowSelectionModifier: Modifier = if (onClick == null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(rowSelectionModifier)
            .padding(horizontal = sheetHorizontalPadding, vertical = sheetVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(sheetRowIconSize).padding(sheetRowIconPadding),
            imageVector = imageVector,
            contentDescription = null
        )
        Column(modifier = Modifier.weight(1f).padding(end = sheetRowTextEndPadding)) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            if (supportingText != null) {
                Text(text = supportingText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (onClick == null) null else onCheckedChange,
            enabled = enabled
        )
    }
}
