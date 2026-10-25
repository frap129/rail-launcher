package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.data.launchables.model.Launchable

private val titleHorizontalPadding = 20.dp
private val titleVerticalPadding = 10.dp
private val titleFontSize = 28.sp

/** The icon-and-name header shown at the top of a launcher sheet. */
@Composable
fun SheetTitle(launchable: Launchable, modifier: Modifier = Modifier, onNameClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = titleHorizontalPadding, vertical = titleVerticalPadding)
    ) {
        LaunchableIcon(launchable = launchable)
        Text(
            text = launchable.name,
            fontSize = titleFontSize,
            modifier = if (onNameClick == null) Modifier else Modifier.clickable(onClick = onNameClick)
        )
    }
}
