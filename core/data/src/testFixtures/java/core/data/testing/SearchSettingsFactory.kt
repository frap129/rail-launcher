package core.data.testing

import core.data.prefs.IPreferencesRepository
import core.data.prefs.ISearchSettings
import core.data.prefs.ISearchSettingsImpl
import core.data.search.IContactSearchSource

/** Builds the real Search settings over test doubles, as both ViewModels receive them. */
fun createSearchSettings(
    mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(),
    mockContactSearchSource: IContactSearchSource = createContactSearchSourceMock()
): ISearchSettings = ISearchSettingsImpl(
    preferencesRepository = mockPreferencesRepository,
    contactSearchSource = mockContactSearchSource
)
