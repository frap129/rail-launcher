package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import core.ui.R

/** Shows whether Rail may read notifications and opens the system settings when tapped. */
@Composable
fun NotificationAccessRow(hasAccess: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ListItem(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(text = stringResource(R.string.notification_access)) },
        supportingContent = {
            Text(
                text = stringResource(
                    if (hasAccess) R.string.notification_access_allowed else R.string.notification_access_not_allowed
                )
            )
        }
    )
}
