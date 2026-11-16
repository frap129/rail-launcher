package core.data.launches

import core.data.room.dao.LaunchCountDao
import core.data.time.IClockSource
import core.data.time.withClockNow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class ILaunchCountRepositoryImpl(
    private val launchCountDao: LaunchCountDao,
    private val clockSource: IClockSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ILaunchCountRepository {
    override val counts: Flow<List<LaunchCountRecord>> = launchCountDao.observeAll()

    override suspend fun recordLaunch(key: String) {
        withClockNow(clockSource = clockSource, dispatcher = dispatcher) { launchedAt ->
            launchCountDao.record(key = key, launchedAt = launchedAt)
        }
    }
}
