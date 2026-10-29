package feature.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import core.ui.composables.OutlinedText
import core.ui.composables.launchableIconSize
import core.ui.layout.compactIconSize

/**
 * A full-width launcher action row with a leading icon and a label. When [contentDescription] is
 * set the row announces it instead of the label alone.
 */
@Composable
internal fun LauncherActionRow(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = launchableIconSize)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(launchableIconSize),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(compactIconSize),
                imageVector = imageVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedText(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
