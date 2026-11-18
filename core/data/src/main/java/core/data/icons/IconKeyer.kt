package core.data.icons

import coil3.key.Keyer
import coil3.request.Options
import core.data.icons.model.Icon

/**
 * Keys an icon by its source key, its Home icon cache generation and the cache's configuration
 * generation, so a background revalidation that replaces the stored bitmap, or a configuration
 * change that can alter system icons, makes the next load read the cache instead of Coil's memory.
 */
class IconKeyer(private val homeIconCache: IHomeIconCache) : Keyer<Icon> {
    override fun key(data: Icon, options: Options): String {
        val source: IconSource = data.toSource()
        if (data.toHomeIconCacheKey(options = options) == null) {
            return source.cacheKey
        }
        return "${source.cacheKey}:${homeIconCache.generation(identity = source.identity)}:${homeIconCache.configurationGeneration}"
    }
}
