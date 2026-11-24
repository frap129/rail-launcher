package core.ui.composables

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A generic warning row with a [title], optional [supportingText] and a trailing [actionLabel]
 * action; only the action runs [onActionClick].
 */
@Composable
fun WarningRow(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    supportingText: String? = null,
    containerColor: Color = settingsRowContainerColor
) {
    SheetRow(
        headlineContent = { Text(title) },
        supportingContent = supportingText?.let { text ->
            {
                Text(text)
            }
        },
        trailingContent = {
            TextButton(onClick = onActionClick) {
                Text(actionLabel)
            }
        },
        containerColor = containerColor
    )
}
