package core.data.testing

import core.data.launches.ILaunchCountRepository
import core.data.launches.LaunchCountRecord
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [ILaunchCountRepository] that reads [counts], with successful launch recordings by default. */
fun createLaunchCountRepositoryMock(counts: Flow<List<LaunchCountRecord>> = MutableStateFlow(emptyList())): ILaunchCountRepository {
    val mockLaunchCountRepository: ILaunchCountRepository = mockk()
    every { mockLaunchCountRepository.counts } returns counts
    coEvery { mockLaunchCountRepository.recordLaunch(any()) } just Runs
    return mockLaunchCountRepository
}
