package core.data.prefs

import core.data.search.IContactSearchSource
import kotlinx.coroutines.flow.StateFlow

/** Persists the Search preferences and gates Contact results on the Contacts permission. */
class ISearchSettingsImpl(
    private val preferencesRepository: IPreferencesRepository,
    private val contactSearchSource: IContactSearchSource
) : ISearchSettings {
    override val hasContactsPermission: StateFlow<Boolean> = contactSearchSource.hasPermission

    override fun setOpenSearchOnSwipeUp(enabled: Boolean) = preferencesRepository.setOpenSearchOnSwipeUp(enabled)

    override fun setSearchSuggestions(enabled: Boolean) = preferencesRepository.setSearchSuggestions(enabled)

    override fun setSearchWebSuggestions(enabled: Boolean) = preferencesRepository.setSearchWebSuggestions(enabled)

    override fun setSearchCalculatorRow(enabled: Boolean) = preferencesRepository.setSearchCalculatorRow(enabled)

    override fun setSearchContacts(enabled: Boolean): ContactResultsOutcome {
        if (!enabled) {
            preferencesRepository.setSearchContacts(false)
            return ContactResultsOutcome.NoAction
        }
        contactSearchSource.refreshPermission()
        if (contactSearchSource.hasPermission.value) {
            preferencesRepository.setSearchContacts(true)
            return ContactResultsOutcome.NoAction
        }
        return ContactResultsOutcome.RequestContactsPermission
    }

    override fun onContactsPermissionResult(granted: Boolean, canAskAgain: Boolean): ContactResultsOutcome {
        contactSearchSource.refreshPermission()
        preferencesRepository.setSearchContacts(granted)
        if (!granted && !canAskAgain) {
            return ContactResultsOutcome.ContactsPermissionDenied
        }
        return ContactResultsOutcome.NoAction
    }

    override fun refreshContactsPermission() = contactSearchSource.refreshPermission()
}
