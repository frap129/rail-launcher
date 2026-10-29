package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** A MockK [ILaunchableRepository] that reads [state], so tests can update the Launchables. */
fun createLaunchableRepositoryMock(state: StateFlow<LaunchableState>): ILaunchableRepository {
    val mockLaunchableRepository: ILaunchableRepository = mockk()
    every { mockLaunchableRepository.launchableState } returns state
    every { mockLaunchableRepository.launchables } returns state.map { current -> current.launchables }
    return mockLaunchableRepository
}

/** A MockK [ILaunchableRepository] loaded with [launchables] in one Initial group, plus [folders]. */
fun createLaunchableRepositoryMock(
    launchables: List<Launchable> = emptyList(),
    label: String = "A",
    folders: List<Launchable> = emptyList(),
    isLoaded: Boolean = true
): ILaunchableRepository = createLaunchableRepositoryMock(
    state = MutableStateFlow(createLoadedLaunchableState(launchables, folders, label, isLoaded))
)

/** A MockK [ILaunchableRepository] loaded from [groups] and [folders]. */
fun createLaunchableRepositoryMock(
    groups: List<LaunchableGroup>,
    folders: List<Launchable> = emptyList(),
    isLoaded: Boolean = true
): ILaunchableRepository = createLaunchableRepositoryMock(
    state = MutableStateFlow(createLaunchableState(groups, folders, isLoaded))
)
