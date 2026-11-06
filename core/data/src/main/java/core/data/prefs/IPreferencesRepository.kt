package core.data.prefs

import core.data.icons.model.CustomIcon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow

interface IPreferencesRepository {
    val launchableCustomizations: Flow<List<LaunchableCustomization>>
    val settings: Flow<Settings>
    fun setItemName(item: Launchable, name: String)

    /** Removes [item]'s stored name so it uses its default name again. */
    fun resetItemName(item: Launchable)

    /** Stores [icon] as [item]'s Custom icon, keeping its stored name. */
    fun setCustomIcon(item: Launchable, icon: CustomIcon)

    /** Removes [item]'s Custom icon so it uses its normal icon again. */
    fun resetCustomIcon(item: Launchable)

    /** Removes the stored customizations for [keys]. */
    suspend fun removeLaunchableCustomizations(keys: List<String>)
    fun setIconPack(iconPack: IconPack)

    /** Sets whether Rail hides a Locked Private Space container. */
    fun setHidePrivateSpaceWhenLocked(hide: Boolean)

    /** Sets which screen edges scrub the Rail. */
    fun setScrubEdges(edges: ScrubEdges)

    /** Sets the screen side where the Rail is drawn. */
    fun setRailSide(side: RailSide)
}
