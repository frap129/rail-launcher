package core.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import core.data.icons.IHomeIconCache
import core.data.icons.identity
import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val NO_REVALIDATION_GENERATION: Long = 0L

/** The size of a Launchable's icon in the App List and the rows that match it. */
val launchableIconSize: Dp = 64.dp

/** The padding inside a Launchable's icon slot in the App List. */
val launchableIconPadding: Dp = 10.dp

/** The size of a Launchable's drawn icon inside its slot. */
val launchableIconArtworkSize: Dp = launchableIconSize - launchableIconPadding * 2

/** The Home icon cache whose revalidations make drawn icons reload; null where icons do not come from it. */
val LocalHomeIconCache: ProvidableCompositionLocal<IHomeIconCache?> = staticCompositionLocalOf { null }

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
    val generation: Long = rememberIconGeneration(identity = launchable.icon.identity)
    val sizePx: Int = with(LocalDensity.current) { launchableIconArtworkSize.roundToPx() }
    val platformContext: PlatformContext = LocalPlatformContext.current
    val request: ImageRequest = remember(platformContext, launchable.icon, sizePx) {
        ImageRequest.Builder(platformContext)
            .data(launchable.icon)
            .size(Size(sizePx, sizePx))
            .build()
    }
    val painter: AsyncImagePainter = rememberAsyncImagePainter(
        model = request,
        contentScale = ContentScale.Fit,
        onState = onIconSettled?.let { settled ->
            { state -> if (state.hasSettled()) settled() }
        }
    )
    var reloadedGeneration: Long by remember { mutableLongStateOf(generation) }
    LaunchedEffect(generation) {
        if (generation != reloadedGeneration) {
            reloadedGeneration = generation
            painter.restart()
        }
    }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(launchableIconArtworkSize),
        colorFilter = launchable.iconColorFilter
    )
}

/** The revalidation generation of [identity]: it changes only when that icon's stored bitmap is replaced. */
@Composable
private fun rememberIconGeneration(identity: String): Long {
    val homeIconCache: IHomeIconCache = LocalHomeIconCache.current ?: return NO_REVALIDATION_GENERATION
    val generationFlow: Flow<Long> = remember(homeIconCache, identity) {
        homeIconCache.generations
            .map { generations -> generations[identity] ?: NO_REVALIDATION_GENERATION }
            .distinctUntilChanged()
    }
    val initialGeneration: Long = remember(homeIconCache, identity) { homeIconCache.generation(identity = identity) }
    val generation: State<Long> = generationFlow.collectAsState(initial = initialGeneration)
    return generation.value
}
