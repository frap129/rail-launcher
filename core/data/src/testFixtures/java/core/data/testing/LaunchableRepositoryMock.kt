package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableReadiness
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

/** A MockK [ILaunchableRepository] holding [launchables] in one Initial group, plus [folders], at [readiness]. */
fun createLaunchableRepositoryMock(
    launchables: List<Launchable> = emptyList(),
    label: String = "A",
    folders: List<Launchable> = emptyList(),
    readiness: LaunchableReadiness = LaunchableReadiness.AppListReady,
    hiddenKeys: Set<String> = emptySet()
): ILaunchableRepository = createLaunchableRepositoryMock(
    state = MutableStateFlow(
        createLoadedLaunchableState(
            launchables = launchables,
            folders = folders,
            label = label,
            readiness = readiness,
            hiddenKeys = hiddenKeys
        )
    )
)

/** A MockK [ILaunchableRepository] resolved from [groups] and [folders] at [readiness]. */
fun createLaunchableRepositoryMock(
    groups: List<LaunchableGroup>,
    folders: List<Launchable> = emptyList(),
    readiness: LaunchableReadiness = LaunchableReadiness.AppListReady
): ILaunchableRepository = createLaunchableRepositoryMock(
    state = MutableStateFlow(createLaunchableState(groups = groups, folders = folders, readiness = readiness))
)
