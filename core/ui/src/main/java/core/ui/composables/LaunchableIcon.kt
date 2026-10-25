package core.ui.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.launchables.model.Launchable

/** A Launchable's icon at the launcher's standard list size. */
@Composable
fun LaunchableIcon(launchable: Launchable) {
    AsyncImage(
        modifier = Modifier.size(64.dp).padding(10.dp),
        model = launchable.icon,
        contentDescription = launchable.name
    )
}
