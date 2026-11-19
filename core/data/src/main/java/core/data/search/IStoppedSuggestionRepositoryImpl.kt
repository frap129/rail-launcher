package core.data.search

import core.data.room.dao.StoppedSuggestionDao
import core.data.room.dao.mapToKeySet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class IStoppedSuggestionRepositoryImpl(
    private val stoppedSuggestionDao: StoppedSuggestionDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IStoppedSuggestionRepository {
    override val keys: Flow<Set<String>> = stoppedSuggestionDao.observeAll().mapToKeySet(StoppedSuggestionRecord::key)

    override suspend fun stop(key: String) {
        withContext(dispatcher) {
            stoppedSuggestionDao.upsert(StoppedSuggestionRecord(key = key))
        }
    }
}
