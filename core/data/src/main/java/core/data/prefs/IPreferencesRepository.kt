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

    /** Stores [icon] as [item]'s Custom icon, keeping its stored name and deleting the image it replaces. */
    fun setCustomIcon(item: Launchable, icon: CustomIcon)

    /** Removes [item]'s Custom icon so it uses its normal icon again, deleting its image. */
    fun resetCustomIcon(item: Launchable)

    /** Removes every Custom icon, keeping custom names, and deletes every stored image. */
    fun resetCustomIcons()

    /** Removes the stored customizations for [keys]. */
    suspend fun removeLaunchableCustomizations(keys: List<String>)
    fun setIconPack(iconPack: IconPack)

    /** Sets whether Rail hides a Locked Private Space container. */
    fun setHidePrivateSpaceWhenLocked(hide: Boolean)

    /** Sets which screen edges scrub the Rail. */
    fun setScrubEdges(edges: ScrubEdges)

    /** Sets the screen side where the Rail is drawn. */
    fun setRailSide(side: RailSide)

    /** Sets whether swiping up on Home opens Search. */
    fun setOpenSearchOnSwipeUp(enabled: Boolean)

    /** Sets whether pressing Home on Home opens Search. */
    fun setOpenSearchOnHomePress(enabled: Boolean)

    /** Sets whether Search shows Search suggestions before typing. */
    fun setSearchSuggestions(enabled: Boolean)
}
