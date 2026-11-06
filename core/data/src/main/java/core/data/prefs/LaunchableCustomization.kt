package core.data.prefs

import androidx.room.Entity
import androidx.room.PrimaryKey
import core.data.icons.model.CustomIcon
import core.data.launchables.model.LaunchableIcons
import core.data.launchables.model.LaunchableNames

@Entity(tableName = "launchable_customizations")
data class LaunchableCustomization(@PrimaryKey val key: String, val name: String? = null, val icon: CustomIcon? = null)

/** The customized names by Launchable key. */
internal fun List<LaunchableCustomization>.toLaunchableNames(): LaunchableNames = LaunchableNames(
    associate { customization -> customization.key to customization.name }
)

/** The Custom icons by Launchable key. */
internal fun List<LaunchableCustomization>.toLaunchableIcons(): LaunchableIcons = LaunchableIcons(
    associate { customization -> customization.key to customization.icon }
)
