package core.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PopupDao {
    @Query("SELECT shortcut_id FROM popup_hidden_shortcuts WHERE owner_key = :ownerKey")
    fun observeHiddenShortcuts(ownerKey: String): Flow<List<String>>

    @Query("SELECT DISTINCT owner_key FROM popup_hidden_shortcuts")
    fun observeAllOwnerKeys(): Flow<List<String>>

    @Query("INSERT OR IGNORE INTO popup_hidden_shortcuts (owner_key, shortcut_id) VALUES (:ownerKey, :shortcutId)")
    suspend fun insertHiddenShortcut(ownerKey: String, shortcutId: String)

    @Query("DELETE FROM popup_hidden_shortcuts WHERE owner_key = :ownerKey AND shortcut_id = :shortcutId")
    suspend fun deleteHiddenShortcut(ownerKey: String, shortcutId: String)

    @Query("DELETE FROM popup_hidden_shortcuts WHERE owner_key = :ownerKey")
    suspend fun deleteHiddenForOwner(ownerKey: String)

    suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean) {
        if (hidden) {
            insertHiddenShortcut(ownerKey = ownerKey, shortcutId = shortcutId)
        } else {
            deleteHiddenShortcut(ownerKey = ownerKey, shortcutId = shortcutId)
        }
    }
}
