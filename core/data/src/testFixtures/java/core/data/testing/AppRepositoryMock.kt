package core.data.testing

import core.data.apps.IAppRepository
import core.data.apps.model.App
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A MockK [IAppRepository] reading [inputAppState], which tests can update. */
fun createAppRepositoryMock(inputAppState: StateFlow<List<App>?>): IAppRepository {
    val mockAppRepository: IAppRepository = mockk()
    every { mockAppRepository.apps } returns inputAppState
    return mockAppRepository
}

/** A MockK [IAppRepository] reading [inputApps]. */
fun createAppRepositoryMock(inputApps: List<App> = emptyList()): IAppRepository =
    createAppRepositoryMock(inputAppState = MutableStateFlow(inputApps))
