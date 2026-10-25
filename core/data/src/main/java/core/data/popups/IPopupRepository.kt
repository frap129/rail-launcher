package core.data.popups

import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow

/** Per-App Pop-up preferences. */
interface IPopupRepository {
    /** The ids of the shortcuts hidden from [ownerKey]'s Pop-up. */
    fun hiddenShortcuts(ownerKey: String): Flow<Set<String>>

    /** Hides or shows the shortcut [shortcutId] in [ownerKey]'s Pop-up. */
    suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean)

    /**
     * The Launchables added to [ownerKey]'s Pop-up, in the user's order.
     * An item whose Launchable no longer exists is removed by the repository and never emitted.
     * Collecting is a pure read.
     */
    fun items(ownerKey: String): Flow<List<Launchable>>

    /** Adds [add] to the end of [ownerKey]'s Pop-up in order, and removes [remove]. */
    suspend fun setItems(ownerKey: String, add: List<String>, remove: Set<String>)

    /** Removes the item [itemKey] from [ownerKey]'s Pop-up. Does nothing if it is not one. */
    suspend fun removeItem(ownerKey: String, itemKey: String)

    /** Moves the item at [from] to [to] in [ownerKey]'s order. */
    suspend fun moveItem(ownerKey: String, from: Int, to: Int)

    /** Deletes the stored Pop-up preferences of [ownerKey], which no longer exists. */
    suspend fun deleteOwner(ownerKey: String)
}
