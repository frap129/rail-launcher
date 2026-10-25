package core.data.testing

import core.data.popups.PopupHiddenShortcut
import core.data.popups.PopupItemRecord
import core.data.room.dao.PopupDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePopupDao(initialRecords: List<PopupHiddenShortcut> = emptyList(), initialItems: List<PopupItemRecord> = emptyList()) : PopupDao {
    private val storedRecords: MutableStateFlow<List<PopupHiddenShortcut>> = MutableStateFlow(initialRecords)
    private val storedItems: MutableStateFlow<List<PopupItemRecord>> = MutableStateFlow(initialItems)
    val records: List<PopupHiddenShortcut> get() = storedRecords.value
    val items: List<PopupItemRecord> get() = storedItems.value

    override fun observeHiddenShortcuts(ownerKey: String): Flow<List<String>> = storedRecords.map { records ->
        records.filter { record -> record.ownerKey == ownerKey }.map { record -> record.shortcutId }
    }

    override fun observeAllOwnerKeys(): Flow<List<String>> = storedRecords.map { records ->
        records.map { record -> record.ownerKey }.distinct()
    }

    override suspend fun insertHiddenShortcut(ownerKey: String, shortcutId: String) {
        storedRecords.update { records ->
            val record: PopupHiddenShortcut = PopupHiddenShortcut(ownerKey = ownerKey, shortcutId = shortcutId)
            if (record in records) records else records + record
        }
    }

    override suspend fun deleteHiddenShortcut(ownerKey: String, shortcutId: String) {
        storedRecords.update { records ->
            records.filterNot { record -> record.ownerKey == ownerKey && record.shortcutId == shortcutId }
        }
    }

    override suspend fun deleteHiddenForOwner(ownerKey: String) {
        storedRecords.update { records -> records.filterNot { record -> record.ownerKey == ownerKey } }
    }

    override fun observeItems(ownerKey: String): Flow<List<PopupItemRecord>> = storedItems.map { items ->
        items.filter { item -> item.ownerKey == ownerKey }
    }

    override fun observeAllItems(): Flow<List<PopupItemRecord>> = storedItems

    override suspend fun nextItemPosition(ownerKey: String): Int {
        val maxPosition: Int? = storedItems.value.filter { item -> item.ownerKey == ownerKey }.maxOfOrNull { item -> item.position }
        return (maxPosition ?: -1) + 1
    }

    override suspend fun insertItems(items: List<PopupItemRecord>) {
        storedItems.update { stored ->
            val result: MutableList<PopupItemRecord> = stored.toMutableList()
            for (item in items) {
                if (result.none { existing -> existing.ownerKey == item.ownerKey && existing.itemKey == item.itemKey }) {
                    result += item
                }
            }
            result
        }
    }

    override suspend fun removeItems(ownerKey: String, itemKeys: List<String>) {
        storedItems.update { items ->
            items.filterNot { item -> item.ownerKey == ownerKey && item.itemKey in itemKeys }
        }
    }

    override suspend fun updatePositions(items: List<PopupItemRecord>) {
        val positionsByKey: Map<Pair<String, String>, Int> = items.associate { item ->
            (item.ownerKey to item.itemKey) to item.position
        }
        storedItems.update { stored ->
            stored.map { item -> positionsByKey[item.ownerKey to item.itemKey]?.let { position -> item.copy(position = position) } ?: item }
        }
    }

    override suspend fun deleteItemsForOwner(ownerKey: String) {
        storedItems.update { items -> items.filterNot { item -> item.ownerKey == ownerKey } }
    }

    override suspend fun deleteItemsByKey(itemKeys: List<String>) {
        storedItems.update { items -> items.filterNot { item -> item.itemKey in itemKeys } }
    }
}
