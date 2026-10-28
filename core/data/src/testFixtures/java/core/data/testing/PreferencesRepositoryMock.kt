package core.data.testing

import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IPreferencesRepository] exposing [customizations] and [settings]. */
fun createPreferencesRepositoryMock(
    customizations: List<LaunchableCustomization> = emptyList(),
    settings: Settings = Settings()
): IPreferencesRepository {
    val mockPreferencesRepository: IPreferencesRepository = mockk()
    every { mockPreferencesRepository.launchableCustomizations } returns MutableStateFlow(customizations)
    every { mockPreferencesRepository.settings } returns MutableStateFlow(settings)
    return mockPreferencesRepository
}
