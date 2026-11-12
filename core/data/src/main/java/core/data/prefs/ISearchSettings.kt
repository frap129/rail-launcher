package core.data.prefs

import kotlinx.coroutines.flow.StateFlow

/** What the Contacts permission gate decided for Contact results. */
sealed interface ContactResultsOutcome {
    data object NoAction : ContactResultsOutcome

    data object RequestContactsPermission : ContactResultsOutcome

    data object ContactsPermissionDenied : ContactResultsOutcome
}

/** Whether Search shows Contact rows: Contact results is on and the permission is granted. */
fun Settings.isContactResultsEnabled(hasContactsPermission: Boolean): Boolean = searchContacts && hasContactsPermission

/** The Search preferences Rail lets the user change. */
interface ISearchSettings {
    /** Whether Rail may read contacts. */
    val hasContactsPermission: StateFlow<Boolean>

    fun setOpenSearchOnSwipeUp(enabled: Boolean)

    fun setSearchSuggestions(enabled: Boolean)

    fun setSearchWebSuggestions(enabled: Boolean)

    fun setSearchCalculatorRow(enabled: Boolean)

    /** Persists whether Search shows Contact rows, asking for the Contacts permission first when it turns on. */
    fun setSearchContacts(enabled: Boolean): ContactResultsOutcome

    /** Persists Contact results from the permission result and reports a permanent denial. */
    fun onContactsPermissionResult(granted: Boolean, canAskAgain: Boolean): ContactResultsOutcome

    /** Rechecks the Contacts permission so the setting follows a change made outside Rail. */
    fun refreshContactsPermission()
}
