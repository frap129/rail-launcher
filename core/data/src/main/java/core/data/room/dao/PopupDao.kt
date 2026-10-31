package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import core.data.popups.PopupItemRecord
import core.data.popups.PopupSettingsRecord
import kotlinx.coroutines.flow.Flow

private const val ITEMS_FOR_OWNER_QUERY: String = "SELECT * FROM popup_items WHERE owner_key = :ownerKey"
private const val ALL_ITEMS_QUERY: String = "SELECT * FROM popup_items"

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

    @Transaction
    suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean) {
        if (hidden) {
            insertHiddenShortcut(ownerKey = ownerKey, shortcutId = shortcutId)
        } else {
            deleteHiddenShortcut(ownerKey = ownerKey, shortcutId = shortcutId)
        }
    }

    @Query(ITEMS_FOR_OWNER_QUERY)
    fun observeItems(ownerKey: String): Flow<List<PopupItemRecord>>

    /** A suspend variant of [observeItems] for reads that must join a caller's transaction. */
    @Query(ITEMS_FOR_OWNER_QUERY)
    suspend fun getItems(ownerKey: String): List<PopupItemRecord>

    @Query(ALL_ITEMS_QUERY)
    fun observeAllItems(): Flow<List<PopupItemRecord>>

    /** A suspend variant of [observeAllItems] for reads that must join a caller's transaction. */
    @Query(ALL_ITEMS_QUERY)
    suspend fun getAllItems(): List<PopupItemRecord>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM popup_items WHERE owner_key = :ownerKey")
    suspend fun nextItemPosition(ownerKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PopupItemRecord>)

    @Transaction
    suspend fun appendItems(ownerKey: String, itemKeys: List<String>) {
        if (itemKeys.isEmpty()) {
            return
        }
        val firstPosition: Int = nextItemPosition(ownerKey)
        insertItems(itemKeys.mapIndexed { index, itemKey -> PopupItemRecord(ownerKey, itemKey, firstPosition + index) })
    }

    @Query("DELETE FROM popup_items WHERE owner_key = :ownerKey AND item_key IN (:itemKeys)")
    suspend fun removeItems(ownerKey: String, itemKeys: List<String>)

    /** Rewrites the positions of [items] in one transaction. Records no longer stored are ignored. */
    @Update
    suspend fun updatePositions(items: List<PopupItemRecord>)

    @Query("DELETE FROM popup_items WHERE owner_key = :ownerKey")
    suspend fun deleteItemsForOwner(ownerKey: String)

    @Query("DELETE FROM popup_items WHERE item_key IN (:itemKeys)")
    suspend fun deleteItemsByKey(itemKeys: List<String>)

    @Query("SELECT * FROM popup_settings WHERE id = 0")
    fun observeSettings(): Flow<PopupSettingsRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: PopupSettingsRecord)
}
