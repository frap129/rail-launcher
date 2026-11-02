package core.data.shortcuts

import kotlinx.coroutines.flow.Flow

/** The Standalone shortcuts another app asked Rail to add. */
interface IStandaloneShortcutRepository {
    /** Every stored Standalone shortcut the platform still publishes, in the order it was added. */
    val shortcuts: Flow<List<ShortcutLaunchable>>

    /**
     * Runs [accept] under the Shortcut pin lock, then stores [key] and adds it to the Favorites in
     * one transaction when it succeeds. A failed or thrown [accept] stores and pins nothing. Once
     * [accept] succeeds, the write is not cancellable, and a failed write releases the pin.
     *
     * Adding an existing key accepts again, keeps its row and added order, and adds it back to the
     * Favorites without moving the existing Favorites.
     *
     * @return whether the shortcut was added; adding an existing key is idempotent.
     */
    suspend fun add(key: ShortcutKey, accept: suspend () -> Boolean): Boolean

    /**
     * Removes [keys] from the Standalone shortcuts, the Favorites, the Header tap actions and the
     * name customizations in one transaction, then reapplies pins for their Profiles.
     */
    suspend fun remove(keys: List<String>)

    /**
     * Removes only the Standalone shortcut rows for [keys], then reapplies pins for their Profiles.
     * Stale-key cleanup uses this so each stored row kind deletes only its own rows.
     */
    suspend fun delete(keys: List<String>)
}
