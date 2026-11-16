package core.data.launches

import core.data.room.dao.LaunchCountDao
import core.data.time.IClockSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ILaunchCountRepositoryImpl(
    private val launchCountDao: LaunchCountDao,
    private val clockSource: IClockSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ILaunchCountRepository {
    override val counts: Flow<List<LaunchCountRecord>> = launchCountDao.observeAll()

    override suspend fun recordLaunch(key: String) {
        withContext(dispatcher) {
            launchCountDao.record(key = key, launchedAt = clockSource.now().toEpochMilli())
        }
    }
}
