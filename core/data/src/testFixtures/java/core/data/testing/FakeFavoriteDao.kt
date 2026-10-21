package core.data.testing

import core.data.favorites.FavoriteRecord
import core.data.room.dao.FavoriteDao
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

const val FIRST_POSITION: Int = 0
private const val REEMIT_BUFFER_CAPACITY: Int = 1

class FakeFavoriteDao(initialRecords: List<FavoriteRecord> = emptyList()) : FavoriteDao {
    private val storedRecords: MutableStateFlow<List<FavoriteRecord>> = MutableStateFlow(initialRecords)
    private val reemittedRecords: MutableSharedFlow<List<FavoriteRecord>> = MutableSharedFlow(extraBufferCapacity = REEMIT_BUFFER_CAPACITY)
    private val observeFailures: MutableSharedFlow<Throwable> = MutableSharedFlow(extraBufferCapacity = REEMIT_BUFFER_CAPACITY)
    private val observeAllSubscriptions: AtomicInteger = AtomicInteger(0)
    var failNextRemove: Throwable? = null
    var isAddEnabled: Boolean = true
    val records: List<FavoriteRecord> get() = storedRecords.value
    val observeAllSubscriptionCount: Int get() = observeAllSubscriptions.get()

    override fun observeAll(): Flow<List<FavoriteRecord>> = merge(
        storedRecords,
        reemittedRecords,
        observeFailures.map { failure -> throw failure }
    )
        .onStart { observeAllSubscriptions.incrementAndGet() }

    suspend fun reemit(records: List<FavoriteRecord>) {
        reemittedRecords.emit(records)
    }

    fun failNextObserve(failure: Throwable) {
        check(observeFailures.tryEmit(failure)) { "No uncollected observeAll failure slot available" }
    }

    override suspend fun add(key: String) {
        if (!isAddEnabled) {
            return
        }
        storedRecords.update { records ->
            if (records.any { it.key == key }) {
                records
            } else {
                records + FavoriteRecord(key = key, position = records.maxOfOrNull(FavoriteRecord::position)?.plus(1) ?: FIRST_POSITION)
            }
        }
    }

    override suspend fun remove(keys: List<String>) {
        failNextRemove?.let { failure ->
            failNextRemove = null
            throw failure
        }
        storedRecords.update { records -> records.filterNot { it.key in keys } }
    }

    override suspend fun update(records: List<FavoriteRecord>) {
        val updatesByKey: Map<String, FavoriteRecord> = records.associateBy { it.key }
        storedRecords.update { stored -> stored.map { record -> updatesByKey[record.key] ?: record } }
    }
}
