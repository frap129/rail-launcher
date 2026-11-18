package core.data.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import core.data.icons.model.Icon
import core.data.profiles.IProfileSource
import core.data.runContained
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Serves an App's system icon, when it has a request size and a known update time, from the Home
 * icon cache; a miss loads the system drawable and stores its bitmap. A cached icon returns
 * without loading its App's resources and is revalidated once in the background. Icon pack,
 * Custom and Shortcut icons, and icons without a request size or update time, bypass the cache and
 * load their drawable as before.
 */
class IconFetcher private constructor(
    private val icon: Icon,
    private val options: Options,
    private val profileSource: IProfileSource,
    private val homeIconCache: IHomeIconCache,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val source: IconSource = icon.toSource(profileSource = profileSource)
        val cacheKey: HomeIconCacheKey? = icon.toHomeIconCacheKey(options = options)
        if (cacheKey == null) {
            return source.loadDrawableOrFail().asImage(shareable = true).toFetchResult(dataSource = DataSource.MEMORY)
        }
        val cachedBitmap: Bitmap? = homeIconCache.read(key = cacheKey)
        if (cachedBitmap != null) {
            scheduleRevalidation(cacheKey = cacheKey, source = source, cachedBitmap = cachedBitmap)
            return cachedBitmap.asImage(shareable = true).toFetchResult(dataSource = DataSource.DISK)
        }
        val bitmap: Bitmap = source.loadDrawableOrFail().toHomeIconBitmap(sizePx = cacheKey.sizePx)
        scheduleStore(cacheKey = cacheKey, bitmap = bitmap)
        return bitmap.asImage(shareable = true).toFetchResult(dataSource = DataSource.MEMORY)
    }

    private fun IconSource.loadDrawableOrFail(): Drawable = loadDrawable(options.context) ?: error("Unable to load an icon")

    /**
     * Rasterizes this drawable into the square slot the icon is drawn in, at [sizePx], fitted and
     * centred so a non-square icon keeps its aspect ratio as the drawable path's `ContentScale.Fit` did.
     */
    private fun Drawable.toHomeIconBitmap(sizePx: Int): Bitmap {
        val bitmap: Bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bounds = fittedBounds(intrinsicWidth = intrinsicWidth, intrinsicHeight = intrinsicHeight, sizePx = sizePx)
        draw(Canvas(bitmap))
        return bitmap
    }

    private fun Image.toFetchResult(dataSource: DataSource): FetchResult =
        ImageFetchResult(image = this, isSampled = false, dataSource = dataSource)

    /** Stores a newly rendered icon without making the draw that rendered it wait for the write. */
    private fun scheduleStore(cacheKey: HomeIconCacheKey, bitmap: Bitmap) {
        scope.launch(dispatcher) {
            runContained(message = "Could not store an icon") { homeIconCache.write(key = cacheKey, bitmap = bitmap) }
        }
    }

    /**
     * Revalidates a cached icon at most once per key between configuration changes that can alter
     * system icons, with the same rendering the miss path uses. A different system icon replaces the stored bitmap, and its generation makes the
     * next load bypass Coil's memory cache.
     */
    private fun scheduleRevalidation(cacheKey: HomeIconCacheKey, source: IconSource, cachedBitmap: Bitmap) {
        if (!homeIconCache.claimRevalidation(key = cacheKey)) {
            return
        }
        scope.launch(dispatcher) {
            runContained(message = "Could not revalidate an icon") {
                val revalidatedBitmap: Bitmap = source.loadDrawable(options.context)?.toHomeIconBitmap(sizePx = cacheKey.sizePx)
                    ?: return@runContained
                // The stored bitmap went through PNG; compare against the same round trip so
                // premultiplied-alpha rounding alone never counts as a changed icon.
                if (!cachedBitmap.sameAs(revalidatedBitmap.afterPngRoundTrip() ?: revalidatedBitmap)) {
                    homeIconCache.write(key = cacheKey, bitmap = revalidatedBitmap)
                }
            }
        }
    }

    class Factory(
        private val profileSource: IProfileSource,
        private val homeIconCache: IHomeIconCache,
        private val scope: CoroutineScope,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : Fetcher.Factory<Icon> {
        override fun create(data: Icon, options: Options, imageLoader: ImageLoader): Fetcher = IconFetcher(
            icon = data,
            options = options,
            profileSource = profileSource,
            homeIconCache = homeIconCache,
            scope = scope,
            dispatcher = dispatcher
        )
    }
}

/**
 * The bounds that fit a drawable of [intrinsicWidth] by [intrinsicHeight] into a [sizePx] square,
 * centred. A drawable without an intrinsic size, or a square one, fills the square.
 */
internal fun fittedBounds(intrinsicWidth: Int, intrinsicHeight: Int, sizePx: Int): Rect {
    if (intrinsicWidth <= 0 || intrinsicHeight <= 0 || intrinsicWidth == intrinsicHeight) {
        return Rect(0, 0, sizePx, sizePx)
    }
    val scale: Float = sizePx.toFloat() / maxOf(intrinsicWidth, intrinsicHeight)
    val width: Int = (intrinsicWidth * scale).roundToInt()
    val height: Int = (intrinsicHeight * scale).roundToInt()
    val left: Int = (sizePx - width) / 2
    val top: Int = (sizePx - height) / 2
    return Rect(left, top, left + width, top + height)
}
