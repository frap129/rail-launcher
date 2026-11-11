package core.data.search

import core.util.normalizedForSearch

/**
 * A contact Search may offer: its stored identity, display name and the number its call and
 * message actions use, or null when it has none. Only contacts of the current profile are read.
 * [normalizedName] is [name] folded once when the contact is created, so matching and ordering
 * never fold it again.
 */
data class SearchContact(val contactId: Long, val lookupKey: String, val name: String, val phoneNumber: String?) {
    val normalizedName: String = name.normalizedForSearch()
}

/** One phone number of a contact, with the flags that choose between the contact's numbers. */
internal data class ContactNumber(val number: String, val isSuperPrimary: Boolean, val isPrimary: Boolean)

/** The number a Contact row uses: the super primary, then the primary, then the first one, if any. */
internal fun preferredContactNumber(numbers: List<ContactNumber>): String? = numbers
    .firstOrNull { number -> number.isSuperPrimary }
    ?.number
    ?: numbers.firstOrNull { number -> number.isPrimary }?.number
    ?: numbers.firstOrNull()?.number
