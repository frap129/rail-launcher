package core.data.testing

import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.search.IContactSearchSource
import core.data.search.SearchContact
import core.data.search.SearchResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A valid custom Search engine template, with the placeholder after the host. */
const val TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE: String = "https://search.example.com/?q=%s"

/** Settings with Google as the Search engine, for tests that assert its web rows. */
val TEST_GOOGLE_SETTINGS: Settings = Settings(searchEngine = SearchEngine.Google)

/** A query that shows the Calculator row. */
const val TEST_CALCULATOR_QUERY: String = "12*7"

/** A query that shows the typed-URL row. */
const val TEST_TYPED_URL_QUERY: String = "example.com"

const val TEST_CONTACT_ID: Long = 1L
const val TEST_CONTACT_LOOKUP_KEY: String = "com.example.alice"
const val TEST_CONTACT_NAME: String = "Alice"
const val TEST_CONTACT_NUMBER: String = "5550100"

const val TEST_WEB_SUGGESTION: String = "railway"
const val TEST_WEB_SUGGESTION_URL: String = "https://www.google.com/search?q=railway"
const val TEST_SECOND_WEB_SUGGESTION: String = "railroad"
const val TEST_SECOND_WEB_SUGGESTION_URL: String = "https://www.google.com/search?q=railroad"
const val TEST_ALPHA_WEB_SUGGESTION: String = "alpha academy"
const val TEST_ALPHA_WEB_SUGGESTION_URL: String = "https://www.google.com/search?q=alpha%20academy"
const val TEST_BETA_WEB_SUGGESTION: String = "beta blocker"
const val TEST_BETA_WEB_SUGGESTION_URL: String = "https://www.google.com/search?q=beta%20blocker"

/** A Web suggestion row for [suggestion], which opens [url]. */
fun createWebSuggestionRow(suggestion: String, url: String): SearchResult.WebSuggestionRow =
    SearchResult.WebSuggestionRow(suggestion = suggestion, url = url)

/** A contact with the Search test defaults. */
fun createSearchContact(
    contactId: Long = TEST_CONTACT_ID,
    lookupKey: String = TEST_CONTACT_LOOKUP_KEY,
    name: String = TEST_CONTACT_NAME,
    phoneNumber: String? = TEST_CONTACT_NUMBER
): SearchContact = SearchContact(contactId = contactId, lookupKey = lookupKey, name = name, phoneNumber = phoneNumber)

/** A MockK [IContactSearchSource] emitting [contacts] and reporting [hasPermission]. */
fun createContactSearchSourceMock(
    contacts: Flow<List<SearchContact>> = MutableStateFlow(emptyList()),
    hasPermission: StateFlow<Boolean> = MutableStateFlow(false)
): IContactSearchSource {
    val mockContactSearchSource: IContactSearchSource = mockk()
    every { mockContactSearchSource.contacts } returns contacts
    every { mockContactSearchSource.hasPermission } returns hasPermission
    every { mockContactSearchSource.refreshPermission() } just Runs
    return mockContactSearchSource
}
