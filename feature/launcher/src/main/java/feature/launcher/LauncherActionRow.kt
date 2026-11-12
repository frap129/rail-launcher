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
import androidx.compose.ui.text.style.TextOverflow
import core.ui.composables.OutlinedText
import core.ui.composables.launchableIconSize
import core.ui.layout.compactIconSize

/** A full-width launcher action row with a leading icon, a label and optional trailing content. */
@Composable
internal fun LauncherActionRow(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    accessibilityAnnouncement: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val rowDescription: String? = launcherItemDescription(label = label, announcement = accessibilityAnnouncement)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = launchableIconSize)
            .contentDescriptionSemantics(description = rowDescription)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
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
            modifier = if (trailingContent != null) Modifier.weight(1f) else Modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
        trailingContent?.invoke()
    }
}
