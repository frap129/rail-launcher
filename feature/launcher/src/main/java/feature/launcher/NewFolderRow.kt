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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import core.popups.popupIconSize
import core.ui.composables.OutlinedText

private val newFolderRowHeight: Dp = 64.dp

/** The "New Folder" row shared by the Favorites editor and the Add to Folder sheet. */
@Composable
fun NewFolderRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = newFolderRowHeight)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(newFolderRowHeight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.Plus,
                contentDescription = null,
                modifier = Modifier.size(popupIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedText(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
