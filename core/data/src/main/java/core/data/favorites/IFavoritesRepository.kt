package core.data.favorites

import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The Favorites in the user's order and whether they have resolved; a loaded empty list is a Home with no Favorites. */
data class FavoritesState(val favorites: List<Launchable>, val isLoaded: Boolean)

interface IFavoritesRepository {
    /**
     * The Favorites in the user's order, resolved to their Launchables.
     * A Favorite whose Launchable no longer exists is removed by the repository and never emitted.
     * Collecting is a pure read.
     */
    val favorites: Flow<List<Launchable>>

    /**
     * [favorites] with whether they reflect the stored Favorites, emitted together so a reader
     * never pairs a stale list with a ready state. False until Home's Launchables load and the
     * stored records resolve; after that, an empty list is a loaded Home with no Favorites.
     */
    val state: StateFlow<FavoritesState>

    /**
     * Whether [launchable] is a Favorite. False while the Launchables have not loaded,
     * and false for a stored record whose Launchable cannot currently be resolved.
     */
    fun isFavorite(launchable: Launchable): Flow<Boolean>

    /**
     * Appends [launchable] to the end of the Favorites. Does nothing if it already is one.
     * Waiting for the change to become visible to [isFavorite] is best-effort and time-bounded.
     */
    suspend fun add(launchable: Launchable)

    /**
     * Removes [launchable] from the Favorites. Does nothing if it is not one.
     * Waiting for the change to become visible to [isFavorite] is best-effort and time-bounded.
     */
    suspend fun remove(launchable: Launchable)

    /** Stores [favorites] as the Favorites' order. Launchables that are not Favorites are ignored. */
    suspend fun reorder(favorites: List<Launchable>)
}
