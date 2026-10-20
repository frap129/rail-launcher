package feature.launcher

import core.data.favorites.FavoriteRecord
import core.data.room.dao.FavoriteDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val FIRST_POSITION: Int = 0

class FakeFavoriteDao(initialRecords: List<FavoriteRecord> = emptyList()) : FavoriteDao {
    private val storedRecords: MutableStateFlow<List<FavoriteRecord>> = MutableStateFlow(initialRecords)
    val records: List<FavoriteRecord> get() = storedRecords.value

    override fun observeAll(): Flow<List<FavoriteRecord>> = storedRecords

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
}
