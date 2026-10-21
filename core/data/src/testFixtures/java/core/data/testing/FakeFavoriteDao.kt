package core.data.testing

import core.data.favorites.FavoriteRecord
import core.data.room.dao.FavoriteDao
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

const val FIRST_POSITION: Int = 0
private const val REEMIT_BUFFER_CAPACITY: Int = 1

class FakeFavoriteDao(initialRecords: List<FavoriteRecord> = emptyList()) : FavoriteDao {
    private val storedRecords: MutableStateFlow<List<FavoriteRecord>> = MutableStateFlow(initialRecords)
    private val reemittedRecords: MutableSharedFlow<List<FavoriteRecord>> = MutableSharedFlow(extraBufferCapacity = REEMIT_BUFFER_CAPACITY)
    private val activeCollectors: AtomicInteger = AtomicInteger(0)
    val records: List<FavoriteRecord> get() = storedRecords.value
    val activeCollectorCount: Int get() = activeCollectors.get()

    override fun observeAll(): Flow<List<FavoriteRecord>> = merge(storedRecords, reemittedRecords)
        .onStart { activeCollectors.incrementAndGet() }
        .onCompletion { activeCollectors.decrementAndGet() }

    suspend fun reemit(records: List<FavoriteRecord>) {
        reemittedRecords.emit(records)
    }

    override suspend fun add(key: String) {
        storedRecords.update { records ->
            if (records.any { it.key == key }) {
                records
            } else {
                records + FavoriteRecord(key = key, position = records.maxOfOrNull(FavoriteRecord::position)?.plus(1) ?: FIRST_POSITION)
            }
        }
    }

    override suspend fun remove(keys: List<String>) {
        storedRecords.update { records -> records.filterNot { it.key in keys } }
    }

    override suspend fun update(records: List<FavoriteRecord>) {
        val updatesByKey: Map<String, FavoriteRecord> = records.associateBy { it.key }
        storedRecords.update { stored -> stored.map { record -> updatesByKey[record.key] ?: record } }
    }
}
