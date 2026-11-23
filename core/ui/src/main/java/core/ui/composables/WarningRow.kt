package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

/**
 * A row warning that [title] is unavailable, with optional [supportingText] and a trailing
 * [actionLabel] action; the row and the action run [onClick].
 */
@Composable
fun WarningRow(
    title: String,
    actionLabel: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    containerColor: Color = settingsRowContainerColor
) {
    SheetRow(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = supportingText?.let { text ->
            {
                Text(text)
            }
        },
        trailingContent = {
            TextButton(onClick = onClick) {
                Text(actionLabel)
            }
        },
        containerColor = containerColor
    )
}
