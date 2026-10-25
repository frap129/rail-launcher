package core.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.icons.model.Icon
import core.ui.composables.sheetRowHorizontalPadding
import core.ui.composables.sheetRowVerticalPadding

/** The icon and label heading a Widget's menu. */
@Composable
fun WidgetMenuTitle(icon: Icon, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = sheetRowHorizontalPadding, vertical = sheetRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
