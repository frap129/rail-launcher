package core.data.testing

import core.data.folders.FolderRecord
import core.data.room.dao.FolderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeFolderDao(initialRecords: List<FolderRecord> = emptyList()) : FolderDao {
    private val records: MutableStateFlow<List<FolderRecord>> = MutableStateFlow(initialRecords)
    private var nextId: Long = (initialRecords.maxOfOrNull { record -> record.id } ?: 0L) + 1L

    /** Fails the next [delete] call instead of deleting. */
    val failNextDelete: FailOnce = FailOnce()

    override fun observeAll(): Flow<List<FolderRecord>> = records

    override suspend fun insert(record: FolderRecord): Long {
        val id: Long = record.id.takeUnless { it == 0L } ?: nextId++
        records.update { current -> current + record.copy(id = id) }
        return id
    }

    override suspend fun delete(id: Long) {
        failNextDelete.throwIfPending()
        records.update { current -> current.filterNot { record -> record.id == id } }
    }
}
