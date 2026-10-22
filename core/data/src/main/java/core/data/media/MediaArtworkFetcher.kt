package core.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** Loads artwork held by [MediaArtworkStore] without putting a Bitmap in UI state. */
class MediaArtworkFetcher private constructor(private val entry: MediaArtworkStore.Entry, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap: Bitmap = entry.bitmap
            ?: entry.uri?.let { uri -> decode(uri = uri, options = options) }
            ?: error("Unable to load Media artwork")
        return ImageFetchResult(
            image = bitmap.asImage(shareable = false),
            isSampled = entry.bitmap == null,
            dataSource = if (entry.bitmap != null) DataSource.MEMORY else DataSource.DISK
        )
    }

    class Factory(private val store: MediaArtworkStore) : Fetcher.Factory<MediaArtwork> {
        override fun create(data: MediaArtwork, options: Options, imageLoader: ImageLoader): Fetcher? =
            store.get(artwork = data)?.let { entry -> MediaArtworkFetcher(entry = entry, options = options) }
    }

    private companion object {
        private const val TARGET_ARTWORK_PX: Int = 256

        fun decode(uri: Uri, options: Options): Bitmap? = try {
            val resolver = options.context.contentResolver
            val sampleSize: Int = resolver.openInputStream(uri)?.use { stream ->
                val bounds: BitmapFactory.Options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, bounds)
                calculateSampleSize(width = bounds.outWidth, height = bounds.outHeight)
            } ?: 1
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
        } catch (exception: SecurityException) {
            null
        } catch (exception: IllegalArgumentException) {
            null
        } catch (exception: java.io.IOException) {
            null
        }

        fun calculateSampleSize(width: Int, height: Int): Int {
            var sampleSize: Int = 1
            while (width / (sampleSize * 2) >= TARGET_ARTWORK_PX && height / (sampleSize * 2) >= TARGET_ARTWORK_PX) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}

/** Keys Media artwork by its opaque handle so a new track never reuses a stale image. */
class MediaArtworkKeyer : Keyer<MediaArtwork> {
    override fun key(data: MediaArtwork, options: Options): String = "media-artwork:${data.key}"
}
