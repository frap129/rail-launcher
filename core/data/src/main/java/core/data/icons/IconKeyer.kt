package core.data.icons

import coil3.key.Keyer
import coil3.request.Options
import core.data.icons.model.Icon

class IconKeyer : Keyer<Icon> {
    override fun key(data: Icon, options: Options): String = data.toSource().cacheKey
}
