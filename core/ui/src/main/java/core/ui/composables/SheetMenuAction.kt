package core.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val sheetRowHorizontalPadding: Dp = 20.dp
val sheetRowVerticalPadding: Dp = 10.dp
val sheetRowIconSize: Dp = 56.dp
val sheetRowIconPadding: Dp = 10.dp

/** One tappable action row in a launcher sheet. */
@Composable
fun SheetMenuAction(imageVector: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = sheetRowHorizontalPadding, vertical = sheetRowVerticalPadding)
    ) {
        Image(
            modifier = Modifier.size(sheetRowIconSize).padding(sheetRowIconPadding),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            contentDescription = label
        )
        Text(label)
    }
}
