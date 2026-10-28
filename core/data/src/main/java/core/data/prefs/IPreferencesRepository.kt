package core.data.prefs

import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow

interface IPreferencesRepository {
    val launchableCustomizations: Flow<List<LaunchableCustomization>>
    val settings: Flow<Settings>
    fun setItemName(item: Launchable, name: String)

    /** Removes [item]'s stored customization so it uses its default name again. */
    fun resetItemName(item: Launchable)

    /** Removes the stored name customizations for [keys]. */
    suspend fun removeLaunchableNames(keys: List<String>)
    fun setIconPack(iconPack: IconPack)

    /** Sets whether Rail hides a Locked Private Space container. */
    fun setHidePrivateSpaceWhenLocked(hide: Boolean)
}
