package core.data.testing

import core.data.search.IContactSearchSource
import core.data.search.SearchContact
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A valid custom Search engine template, with the placeholder after the host. */
const val TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE: String = "https://search.example.com/?q=%s"

const val TEST_CONTACT_ID: Long = 1L
const val TEST_CONTACT_LOOKUP_KEY: String = "com.example.alice"
const val TEST_CONTACT_NAME: String = "Alice"
const val TEST_CONTACT_NUMBER: String = "5550100"

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
