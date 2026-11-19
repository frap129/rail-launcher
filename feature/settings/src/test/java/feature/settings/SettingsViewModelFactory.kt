package feature.settings

import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.prefs.IPreferencesRepository
import core.data.profiles.IPrivateSpaceStatus
import core.data.search.IContactSearchSource
import core.data.search.IStoppedSuggestionRepository
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.createContactSearchSourceMock
import core.data.testing.createIconRepositoryMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateSpaceStatusMock
import core.data.testing.createSearchSettings
import core.data.testing.createStoppedSuggestionRepositoryMock

/** A [SettingsViewModel] over mocks, with the Search settings it reads and every stored preference defaulted. */
fun createSettingsViewModel(
    mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(),
    mockPrivateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatusMock(),
    mockContactSearchSource: IContactSearchSource = createContactSearchSourceMock(),
    mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(),
    mockStoppedSuggestionRepository: IStoppedSuggestionRepository = createStoppedSuggestionRepositoryMock()
): SettingsViewModel = SettingsViewModel(
    prefsRepo = mockPreferencesRepository,
    iconRepo = createIconRepositoryMock(inputIcon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID)),
    privateSpaceStatus = mockPrivateSpaceStatus,
    searchSettings = createSearchSettings(
        mockPreferencesRepository = mockPreferencesRepository,
        mockContactSearchSource = mockContactSearchSource
    ),
    launchableRepo = mockLaunchableRepository,
    stoppedSuggestionRepo = mockStoppedSuggestionRepository
)
