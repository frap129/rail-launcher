package core.data.prefs

import androidx.room.Entity
import androidx.room.PrimaryKey
import core.data.launchables.model.LaunchableNames

@Entity(tableName = "launchable_customizations")
data class LaunchableCustomization(@PrimaryKey val key: String, val name: String? = null)

/** The customized names by Launchable key, resolving each stored key through [resolveKey]. */
internal fun List<LaunchableCustomization>.toLaunchableNames(resolveKey: (String) -> String? = { key -> key }): LaunchableNames {
    val resolvedNames: MutableMap<String, String?> = mutableMapOf()
    val exactNames: MutableMap<String, String?> = mutableMapOf()
    for (customization in this) {
        val resolvedKey: String = resolveKey(customization.key) ?: continue
        if (resolvedKey == customization.key) {
            exactNames[resolvedKey] = customization.name
        } else {
            resolvedNames[resolvedKey] = customization.name
        }
    }
    return LaunchableNames(resolvedNames + exactNames)
}
