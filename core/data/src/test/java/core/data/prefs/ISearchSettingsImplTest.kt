package core.data.prefs

import core.data.testing.createContactSearchSourceMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createSearchSettings
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ISearchSettingsImplTest {
    @Test
    fun `persists every Search preference`() {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val searchSettings: ISearchSettings = createSearchSettings(
            mockPreferencesRepository = mockPreferencesRepository,
            mockContactSearchSource = createContactSearchSourceMock(hasPermission = MutableStateFlow(true))
        )

        searchSettings.setOpenSearchOnSwipeUp(false)
        searchSettings.setSearchSuggestions(false)
        searchSettings.setSearchWebSuggestions(true)
        searchSettings.setSearchCalculatorRow(false)
        searchSettings.setSearchContacts(true)

        verify(exactly = 1) { mockPreferencesRepository.setOpenSearchOnSwipeUp(false) }
        verify(exactly = 1) { mockPreferencesRepository.setSearchSuggestions(false) }
        verify(exactly = 1) { mockPreferencesRepository.setSearchWebSuggestions(true) }
        verify(exactly = 1) { mockPreferencesRepository.setSearchCalculatorRow(false) }
        verify(exactly = 1) { mockPreferencesRepository.setSearchContacts(true) }
    }

    @Test
    fun `turns Contact results off without asking for the permission`() {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val searchSettings: ISearchSettings = createSearchSettings(mockPreferencesRepository = mockPreferencesRepository)

        val actualOutcome: ContactResultsOutcome = searchSettings.setSearchContacts(false)

        assertEquals(ContactResultsOutcome.NoAction, actualOutcome)
        verify(exactly = 1) { mockPreferencesRepository.setSearchContacts(false) }
    }

    @Test
    fun `requests the Contacts permission without turning Contact results on`() {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val searchSettings: ISearchSettings = createSearchSettings(
            mockPreferencesRepository = mockPreferencesRepository,
            mockContactSearchSource = createContactSearchSourceMock(hasPermission = MutableStateFlow(false))
        )

        val actualOutcome: ContactResultsOutcome = searchSettings.setSearchContacts(true)

        assertEquals(ContactResultsOutcome.RequestContactsPermission, actualOutcome)
        verify(exactly = 0) { mockPreferencesRepository.setSearchContacts(any()) }
    }

    @Test
    fun `persists Contact results from a grant`() {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val searchSettings: ISearchSettings = createSearchSettings(mockPreferencesRepository = mockPreferencesRepository)

        val actualOutcome: ContactResultsOutcome = searchSettings.onContactsPermissionResult(granted = true, canAskAgain = true)

        assertEquals(ContactResultsOutcome.NoAction, actualOutcome)
        verify(exactly = 1) { mockPreferencesRepository.setSearchContacts(true) }
    }

    @Test
    fun `keeps Contact results off from a denial`() {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val searchSettings: ISearchSettings = createSearchSettings(mockPreferencesRepository = mockPreferencesRepository)

        val actualOutcome: ContactResultsOutcome = searchSettings.onContactsPermissionResult(granted = false, canAskAgain = true)

        assertEquals(ContactResultsOutcome.NoAction, actualOutcome)
        verify(exactly = 1) { mockPreferencesRepository.setSearchContacts(false) }
    }

    @Test
    fun `reports a permanent denial and keeps Contact results off`() {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val searchSettings: ISearchSettings = createSearchSettings(mockPreferencesRepository = mockPreferencesRepository)

        val actualOutcome: ContactResultsOutcome = searchSettings.onContactsPermissionResult(granted = false, canAskAgain = false)

        assertEquals(ContactResultsOutcome.ContactsPermissionDenied, actualOutcome)
        verify(exactly = 1) { mockPreferencesRepository.setSearchContacts(false) }
    }

    @Test
    fun `shows Contact results only while the setting is on and the permission is granted`() {
        val inputSettings: Settings = Settings(searchContacts = true)

        assertTrue(inputSettings.isContactResultsEnabled(hasContactsPermission = true))
        assertFalse(inputSettings.isContactResultsEnabled(hasContactsPermission = false))
        assertFalse(inputSettings.copy(searchContacts = false).isContactResultsEnabled(hasContactsPermission = true))
    }
}
