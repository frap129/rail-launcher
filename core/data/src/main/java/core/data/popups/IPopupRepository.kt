package core.data.popups

import kotlinx.coroutines.flow.Flow

/** Per-App Pop-up preferences. */
interface IPopupRepository {
    /** The ids of the shortcuts hidden from [ownerKey]'s Pop-up. */
    fun hiddenShortcuts(ownerKey: String): Flow<Set<String>>

    /** Hides or shows the shortcut [shortcutId] in [ownerKey]'s Pop-up. */
    suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean)

    /** Deletes the stored Pop-up preferences of [ownerKey], which no longer exists. */
    suspend fun deleteOwner(ownerKey: String)
}
