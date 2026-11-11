package core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {
    @Test
    fun `allows contacts only when the setting is on and the permission is granted`() {
        assertTrue(canSearchContacts(areContactsOn = true, hasPermission = true))
        assertFalse(canSearchContacts(areContactsOn = true, hasPermission = false))
        assertFalse(canSearchContacts(areContactsOn = false, hasPermission = true))
        assertFalse(canSearchContacts(areContactsOn = false, hasPermission = false))
    }

    @Test
    fun `prefers the super primary number`() {
        val inputNumbers: List<ContactNumber> = listOf(
            ContactNumber(number = "first", isSuperPrimary = false, isPrimary = false),
            ContactNumber(number = "primary", isSuperPrimary = false, isPrimary = true),
            ContactNumber(number = "super", isSuperPrimary = true, isPrimary = true)
        )

        val actualNumber: String? = preferredContactNumber(numbers = inputNumbers)

        assertEquals("super", actualNumber)
    }

    @Test
    fun `prefers the primary number when none is super primary`() {
        val inputNumbers: List<ContactNumber> = listOf(
            ContactNumber(number = "first", isSuperPrimary = false, isPrimary = false),
            ContactNumber(number = "primary", isSuperPrimary = false, isPrimary = true)
        )

        val actualNumber: String? = preferredContactNumber(numbers = inputNumbers)

        assertEquals("primary", actualNumber)
    }

    @Test
    fun `uses the first number when none is primary`() {
        val inputNumbers: List<ContactNumber> = listOf(
            ContactNumber(number = "first", isSuperPrimary = false, isPrimary = false),
            ContactNumber(number = "second", isSuperPrimary = false, isPrimary = false)
        )

        val actualNumber: String? = preferredContactNumber(numbers = inputNumbers)

        assertEquals("first", actualNumber)
    }

    @Test
    fun `does not pick a number for a contact without one`() {
        val actualNumber: String? = preferredContactNumber(numbers = emptyList())

        assertNull(actualNumber)
    }
}
