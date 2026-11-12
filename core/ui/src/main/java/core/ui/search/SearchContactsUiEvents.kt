package core.ui.search

import core.data.prefs.ContactResultsOutcome
import core.util.EventChannel
import kotlinx.coroutines.flow.Flow

/** What the Contact results row must ask its host to show after a Contacts permission change. */
sealed interface SearchContactsUiEvent {
    data object RequestContactsPermission : SearchContactsUiEvent

    data object ShowContactsPermissionDenied : SearchContactsUiEvent
}

/** One host's queued Contact results permission events. */
class SearchContactsUiEvents {
    private val channel: EventChannel<SearchContactsUiEvent> = EventChannel()

    val events: Flow<SearchContactsUiEvent> = channel.flow

    /** Queues the event [outcome] needs its host to show, if any. */
    fun report(outcome: ContactResultsOutcome) {
        when (outcome) {
            ContactResultsOutcome.NoAction -> Unit
            ContactResultsOutcome.RequestContactsPermission -> channel.trySend(SearchContactsUiEvent.RequestContactsPermission)
            ContactResultsOutcome.ContactsPermissionDenied -> channel.trySend(SearchContactsUiEvent.ShowContactsPermissionDenied)
        }
    }
}
