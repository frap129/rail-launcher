package core.ui.popups

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.launchables.model.Launchable

private val checkboxRowIconSize: Dp = 32.dp
private val checkboxRowIconGap: Dp = 12.dp
internal val checkboxRowHorizontalPadding: Dp = 20.dp
private val checkboxRowVerticalPadding: Dp = 10.dp

/** A tappable Launchable row with a trailing checkbox. */
@Composable
fun CheckboxLaunchableRow(launchable: Launchable, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, minHeight: Dp = 0.dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .toggleable(
                value = isChecked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = checkboxRowHorizontalPadding, vertical = checkboxRowVerticalPadding)
    ) {
        AsyncImage(
            modifier = Modifier.size(checkboxRowIconSize),
            model = launchable.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(checkboxRowIconGap))
        Text(
            text = launchable.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Checkbox(checked = isChecked, onCheckedChange = null)
    }
}
