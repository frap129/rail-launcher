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
import core.data.profiles.IProfileSource

class IconFetcher private constructor(private val icon: Icon, private val options: Options, private val profileSource: IProfileSource) :
    Fetcher {
    override suspend fun fetch(): FetchResult {
        val source: IconSource = icon.toSource(profileSource = profileSource)
        val drawable: Drawable = source.loadDrawable(options.context) ?: error("Unable to load an icon")
        return ImageFetchResult(
            image = drawable.asImage(shareable = true),
            isSampled = false,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory(private val profileSource: IProfileSource) : Fetcher.Factory<Icon> {
        override fun create(data: Icon, options: Options, imageLoader: ImageLoader): Fetcher =
            IconFetcher(icon = data, options = options, profileSource = profileSource)
    }
}
