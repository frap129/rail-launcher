package core.data.testing

import core.data.popups.PopupHiddenShortcut
import core.data.room.dao.PopupDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePopupDao(initialRecords: List<PopupHiddenShortcut> = emptyList()) : PopupDao {
    private val storedRecords: MutableStateFlow<List<PopupHiddenShortcut>> = MutableStateFlow(initialRecords)
    val records: List<PopupHiddenShortcut> get() = storedRecords.value

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
}
