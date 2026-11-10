package core.data.search

import core.data.room.dao.SearchHistoryDao
import core.data.time.IClockSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ISearchHistoryRepositoryImpl(
    private val searchHistoryDao: SearchHistoryDao,
    private val clockSource: IClockSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ISearchHistoryRepository {
    override val history: Flow<List<SearchHistoryRecord>> = searchHistoryDao.observeAll()

    override suspend fun recordLaunch(key: String) {
        withContext(dispatcher) {
            searchHistoryDao.record(key = key, launchedAt = clockSource.now().toEpochMilli())
        }
    }

    override suspend fun removeFromSuggestions(key: String) {
        withContext(dispatcher) {
            searchHistoryDao.removeFromSuggestions(key = key)
        }
    }
}
