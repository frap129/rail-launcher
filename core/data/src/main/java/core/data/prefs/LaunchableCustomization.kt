package core.data.prefs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import core.data.icons.model.CustomIcon
import core.data.launchables.model.LaunchableIcons
import core.data.launchables.model.LaunchableNames

@Entity(tableName = LaunchableCustomization.TABLE_NAME)
data class LaunchableCustomization(
    @PrimaryKey val key: String,
    val name: String? = null,
    val icon: CustomIcon? = null,
    @ColumnInfo(name = COLUMN_HIDDEN, defaultValue = "0") val isHidden: Boolean = false
) {
    companion object {
        const val TABLE_NAME: String = "launchable_customizations"
        const val COLUMN_HIDDEN: String = "hidden"
    }
}

/** The customized names by Launchable key. */
internal fun List<LaunchableCustomization>.toLaunchableNames(): LaunchableNames = LaunchableNames(
    associate { customization -> customization.key to customization.name }
)

/** The Custom icons by Launchable key. */
internal fun List<LaunchableCustomization>.toLaunchableIcons(): LaunchableIcons = LaunchableIcons(
    associate { customization -> customization.key to customization.icon }
)

/** The keys of the Hidden apps. */
internal fun List<LaunchableCustomization>.toHiddenKeys(): Set<String> =
    filter { customization -> customization.isHidden }.mapTo(mutableSetOf(), LaunchableCustomization::key)
