package core.data.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The source of the contacts Search may match, read only while Contact results and its permission allow it. */
interface IContactSearchSource {
    /** Whether Rail may read contacts; Search never asks for the permission itself. */
    val hasPermission: StateFlow<Boolean>

    /** Rechecks the Contacts permission after the user changed it. */
    fun refreshPermission()

    /**
     * Every contact of the current profile, with its display name and preferred number, while
     * Contact results is on and READ_CONTACTS is granted; empty immediately otherwise. Only
     * queries, and listens for ContactsContract changes, while collected.
     */
    val contacts: Flow<List<SearchContact>>
}
