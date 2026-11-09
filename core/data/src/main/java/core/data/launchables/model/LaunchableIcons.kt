package core.data.launchables.model

import core.data.icons.IIconRepository
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.profiles.ProfileInfo

/** Custom icons by Launchable key. */
@JvmInline
value class LaunchableIcons(private val iconsByKey: Map<String, CustomIcon?>) {
    operator fun get(key: String): CustomIcon? = iconsByKey[key]
}

/** The icon [key]'s Custom icon resolves to, badged for [profile] when it is not null, or null when there is none. */
suspend fun LaunchableIcons.resolveCustomIcon(key: String, iconRepository: IIconRepository, profile: ProfileInfo? = null): Icon? =
    this[key]?.let { icon -> iconRepository.getCustomIcon(customIcon = icon, profile = profile) }
