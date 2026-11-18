package core.data.testing

import core.data.apps.HomeApps
import core.data.apps.IAppRepository
import core.data.apps.model.App
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** A MockK [IAppRepository] reading [inputAppState] as the live App load, which tests can update. */
fun createAppRepositoryMock(inputAppState: StateFlow<List<App>?>): IAppRepository {
    val mockAppRepository: IAppRepository = mockk()
    every { mockAppRepository.homeApps } returns inputAppState.map { apps ->
        apps?.let { liveApps -> HomeApps.Live(apps = liveApps) }
    }
    return mockAppRepository
}

/** A MockK [IAppRepository] reading [inputApps] as the live App load. */
fun createAppRepositoryMock(inputApps: List<App> = emptyList()): IAppRepository =
    createAppRepositoryMock(inputAppState = MutableStateFlow(inputApps))

/** A MockK [IAppRepository] whose Home Apps are [inputHomeApps]. */
fun createAppRepositoryMock(inputHomeApps: Flow<HomeApps?>): IAppRepository {
    val mockAppRepository: IAppRepository = mockk()
    every { mockAppRepository.homeApps } returns inputHomeApps
    return mockAppRepository
}
