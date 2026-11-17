package core.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import core.data.launchables.model.Launchable

/** The size of a Launchable's icon in the App List and the rows that match it. */
val launchableIconSize: Dp = 64.dp

/** The padding inside a Launchable's icon slot in the App List. */
val launchableIconPadding: Dp = 10.dp

private val greyscaleIconColorFilter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The greyscale filter that marks a Suspended App's icon, or null while [isSuspended] is false. */
fun suspendedIconColorFilter(isSuspended: Boolean): ColorFilter? = if (isSuspended) greyscaleIconColorFilter else null

/** The greyscale filter that marks this Launchable's icon while it is Suspended. */
val Launchable.iconColorFilter: ColorFilter? get() = suspendedIconColorFilter(isSuspended = isSuspended)

/** Whether this icon load has finished, whether the icon drew or failed. */
internal fun AsyncImagePainter.State.hasSettled(): Boolean =
    this is AsyncImagePainter.State.Success || this is AsyncImagePainter.State.Error

/** A Launchable's icon at the launcher's standard list size; [onIconSettled] runs each time the icon load settles, whether it drew or failed. */
@Composable
fun LaunchableIcon(
    launchable: Launchable,
    modifier: Modifier = Modifier,
    contentDescription: String? = launchable.accessibilityName,
    onIconSettled: (() -> Unit)? = null
) {
    Box(modifier = modifier.size(launchableIconSize), contentAlignment = Alignment.Center) {
        LaunchableIconArtwork(launchable = launchable, contentDescription = contentDescription, onIconSettled = onIconSettled)
    }
}

/** A Launchable's drawn icon, sized to the artwork inside the standard icon slot. */
@Composable
fun LaunchableIconArtwork(
    launchable: Launchable,
    modifier: Modifier = Modifier,
    contentDescription: String? = launchable.accessibilityName,
    onIconSettled: (() -> Unit)? = null
) {
    AsyncImage(
        modifier = modifier.size(launchableIconSize - launchableIconPadding * 2),
        model = launchable.icon,
        contentDescription = contentDescription,
        colorFilter = launchable.iconColorFilter,
        onState = onIconSettled?.let { settled ->
            { state -> if (state.hasSettled()) settled() }
        }
    )
}
