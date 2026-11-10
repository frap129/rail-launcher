package core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizationTest {
    @Test
    fun `folds case`() {
        assertEquals("maps", "Maps".normalizedForSearch())
        assertEquals("maps", "MAPS".normalizedForSearch())
    }

    @Test
    fun `strips accent marks`() {
        assertEquals("cafe", "Café".normalizedForSearch())
        assertEquals("cafe", "Cafe\u0301".normalizedForSearch())
    }

    @Test
    fun `keeps unmarked non-Latin text in its own script`() {
        assertEquals("東京", "東京".normalizedForSearch())
        assertEquals("москва", "Москва".normalizedForSearch())
    }

    @Test
    fun `strips the marks of marked non-Latin scripts`() {
        assertEquals("हनद", "हिन्दी".normalizedForSearch())
    }

    @Test
    fun `splits words at punctuation and symbols`() {
        assertEquals(listOf("Google", "Maps"), "Google Maps".wordsForSearch())
        assertEquals(listOf("F", "Droid"), "F-Droid".wordsForSearch())
        assertEquals(listOf("99"), "99".wordsForSearch())
        assertEquals(listOf("a", "b", "b"), "a_b!b?".wordsForSearch())
    }

    @Test
    fun `matches a candidate that contains the folded query`() {
        assertTrue(SearchQuery("CAFÉ").matches(candidate = "Café Maps"))
        assertTrue(SearchQuery("map").matches(candidate = "Google Maps"))
        assertFalse(SearchQuery("photos").matches(candidate = "Google Maps"))
    }

    @Test
    fun `matches every candidate for a blank query`() {
        assertTrue(SearchQuery("").matches(candidate = "Maps"))
        assertTrue(SearchQuery("").matches(candidate = "東京"))
    }
}
