package core.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.launchables.model.Launchable

/** The size of a Launchable's icon in the App List and the rows that match it. */
val launchableIconSize: Dp = 64.dp

/** The padding inside a Launchable's icon slot in the App List. */
val launchableIconPadding: Dp = 10.dp

/** A Launchable's icon at the launcher's standard list size. */
@Composable
fun LaunchableIcon(launchable: Launchable, modifier: Modifier = Modifier, contentDescription: String? = launchable.accessibilityName) {
    Box(modifier = modifier.size(launchableIconSize), contentAlignment = Alignment.Center) {
        LaunchableIconArtwork(launchable = launchable, contentDescription = contentDescription)
    }
}

/** A Launchable's drawn icon, sized to the artwork inside the standard icon slot. */
@Composable
fun LaunchableIconArtwork(
    launchable: Launchable,
    modifier: Modifier = Modifier,
    contentDescription: String? = launchable.accessibilityName
) {
    AsyncImage(
        modifier = modifier.size(launchableIconSize - launchableIconPadding * 2),
        model = launchable.icon,
        contentDescription = contentDescription
    )
}
