package core.data.icons

import android.graphics.drawable.Drawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import core.data.icons.model.Icon

class IconFetcher private constructor(private val icon: Icon, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val source: IconSource = icon.toSource()
        val drawable: Drawable = source.loadDrawable(options.context) ?: error("Unable to load $icon")
        return ImageFetchResult(
            image = drawable.asImage(shareable = true),
            isSampled = false,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<Icon> {
        override fun create(data: Icon, options: Options, imageLoader: ImageLoader): Fetcher = IconFetcher(data, options)
    }
}
