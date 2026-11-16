package core.data.testing

import core.data.launches.ILaunchCountRepository
import core.data.launches.ILaunchCountRepositoryImpl
import core.data.room.dao.LaunchCountDao
import core.data.time.IClockSource
import kotlinx.coroutines.CoroutineDispatcher

/** The production [ILaunchCountRepository] over [launchCountDao] and [clockSource], writing on [dispatcher]. */
fun createLaunchCountRepository(
    launchCountDao: LaunchCountDao,
    clockSource: IClockSource,
    dispatcher: CoroutineDispatcher
): ILaunchCountRepository = ILaunchCountRepositoryImpl(
    launchCountDao = launchCountDao,
    clockSource = clockSource,
    dispatcher = dispatcher
)
