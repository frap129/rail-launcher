package core.data.testing

import android.os.UserHandle
import core.data.apps.IAppRepository
import core.data.apps.model.App
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A MockK [IAppRepository] reading [inputAppState] and [inputEnumeratedProfiles], which tests can update. */
fun createAppRepositoryMock(
    inputAppState: StateFlow<List<App>?>,
    inputEnumeratedProfiles: StateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
): IAppRepository {
    val mockAppRepository: IAppRepository = mockk()
    every { mockAppRepository.apps } returns inputAppState
    every { mockAppRepository.enumeratedProfiles } returns inputEnumeratedProfiles
    return mockAppRepository
}

/** A MockK [IAppRepository] reading [inputApps], with no Profile enumerated unless a test says so. */
fun createAppRepositoryMock(inputApps: List<App> = emptyList()): IAppRepository =
    createAppRepositoryMock(inputAppState = MutableStateFlow(inputApps))
