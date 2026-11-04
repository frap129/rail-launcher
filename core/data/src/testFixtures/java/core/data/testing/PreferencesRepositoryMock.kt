package core.data.testing

import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IPreferencesRepository] exposing [customizations] and [settingsFlow], with successful writes. */
fun createPreferencesRepositoryMock(
    customizations: List<LaunchableCustomization> = emptyList(),
    settings: Settings = Settings(),
    settingsFlow: Flow<Settings> = MutableStateFlow(settings)
): IPreferencesRepository {
    val mockPreferencesRepository: IPreferencesRepository = mockk()
    every { mockPreferencesRepository.launchableCustomizations } returns MutableStateFlow(customizations)
    every { mockPreferencesRepository.settings } returns settingsFlow
    every { mockPreferencesRepository.setItemName(any(), any()) } just Runs
    every { mockPreferencesRepository.setIconPack(any()) } just Runs
    every { mockPreferencesRepository.setHidePrivateSpaceWhenLocked(any()) } just Runs
    every { mockPreferencesRepository.setScrubEdges(any()) } just Runs
    every { mockPreferencesRepository.setRailSide(any()) } just Runs
    coEvery { mockPreferencesRepository.removeLaunchableNames(any()) } just Runs
    return mockPreferencesRepository
}
