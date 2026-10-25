package core.data.testing

import core.data.folders.FolderRecord
import core.data.room.dao.FolderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeFolderDao(initialRecords: List<FolderRecord> = emptyList()) : FolderDao {
    private val records: MutableStateFlow<List<FolderRecord>> = MutableStateFlow(initialRecords)
    private var nextId: Long = (initialRecords.maxOfOrNull { record -> record.id } ?: 0L) + 1L

    override fun observeAll(): Flow<List<FolderRecord>> = records

    override suspend fun insert(record: FolderRecord): Long {
        val id: Long = record.id.takeUnless { it == 0L } ?: nextId++
        records.update { current -> current + record.copy(id = id) }
        return id
    }

    override suspend fun setShowInAppList(id: Long, showInAppList: Boolean) {
        records.update { current ->
            current.map { record -> if (record.id == id) record.copy(showInAppList = showInAppList) else record }
        }
    }

    override suspend fun delete(id: Long) {
        records.update { current -> current.filterNot { record -> record.id == id } }
    }
}
