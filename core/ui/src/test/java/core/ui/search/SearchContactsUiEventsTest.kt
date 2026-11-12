package core.ui.search

import app.cash.turbine.test
import core.data.prefs.ContactResultsOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchContactsUiEventsTest {
    @Test
    fun `requests the Contacts permission`() = runTest {
        val searchContactsUiEvents: SearchContactsUiEvents = SearchContactsUiEvents()

        searchContactsUiEvents.events.test {
            searchContactsUiEvents.report(ContactResultsOutcome.RequestContactsPermission)

            assertEquals(SearchContactsUiEvent.RequestContactsPermission, awaitItem())
        }
    }

    @Test
    fun `shows the permanent denial`() = runTest {
        val searchContactsUiEvents: SearchContactsUiEvents = SearchContactsUiEvents()

        searchContactsUiEvents.events.test {
            searchContactsUiEvents.report(ContactResultsOutcome.ContactsPermissionDenied)

            assertEquals(SearchContactsUiEvent.ShowContactsPermissionDenied, awaitItem())
        }
    }

    @Test
    fun `queues no event without an outcome`() = runTest {
        val searchContactsUiEvents: SearchContactsUiEvents = SearchContactsUiEvents()

        searchContactsUiEvents.events.test {
            searchContactsUiEvents.report(ContactResultsOutcome.NoAction)

            expectNoEvents()
        }
    }
}
