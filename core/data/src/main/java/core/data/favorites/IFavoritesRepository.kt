package core.data.favorites

import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow

interface IFavoritesRepository {
    /**
     * The Favorites in the user's order, resolved to their Launchables.
     * A Favorite whose Launchable no longer exists is removed.
     */
    val favorites: Flow<List<Launchable>>

    /** Whether [launchable] is a Favorite. */
    fun isFavorite(launchable: Launchable): Flow<Boolean>

    /** Appends [launchable] to the end of the Favorites. Does nothing if it already is one. */
    suspend fun add(launchable: Launchable)

    /** Removes [launchable] from the Favorites. Does nothing if it is not one. */
    suspend fun remove(launchable: Launchable)
}
