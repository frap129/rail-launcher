package feature.launcher.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.icons.model.Icon
import core.ui.composables.SHEET_TITLE_ICON_TEST_TAG
import core.ui.composables.sheetRowContentInset
import core.ui.composables.sheetVerticalPadding

/** The icon and label heading a Widget's menu, aligned with the rows' leading-icon column. */
@Composable
fun WidgetMenuTitle(icon: Icon, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = sheetRowContentInset, vertical = sheetVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            alignment = Alignment.CenterStart,
            modifier = Modifier.size(40.dp).testTag(SHEET_TITLE_ICON_TEST_TAG)
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
