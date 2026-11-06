package core.data.launchables.model

import core.data.icons.model.CustomIcon

/** Custom icons by Launchable key. */
@JvmInline
value class LaunchableIcons(private val iconsByKey: Map<String, CustomIcon?>) {
    operator fun get(key: String): CustomIcon? = iconsByKey[key]
}
