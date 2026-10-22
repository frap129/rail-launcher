package feature.launcher

import core.ui.model.data.favoriteKey
import core.ui.model.data.placeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeReorderTest {
    private val placeIds: List<Long> = listOf(7L, 9L, 11L)
    private val favoriteKeys: List<String> = listOf("one", "two")

    @Test
    fun `resolves place keys to place indices`() {
        assertEquals(
            HomeReorder.Place(fromIndex = 0, toIndex = 2),
            resolveHomeReorder(placeKey(7), placeKey(11), placeIds, favoriteKeys)
        )
    }

    @Test
    fun `resolves favorite keys to favorite indices`() {
        assertEquals(
            HomeReorder.Favorite(fromIndex = 1, toIndex = 0),
            resolveHomeReorder(favoriteKey("two"), favoriteKey("one"), placeIds, favoriteKeys)
        )
    }

    @Test
    fun `rejects dragging a place onto a favorite`() {
        assertNull(resolveHomeReorder(placeKey(7), favoriteKey("one"), placeIds, favoriteKeys))
    }

    @Test
    fun `rejects dragging a favorite onto a place`() {
        assertNull(resolveHomeReorder(favoriteKey("one"), placeKey(7), placeIds, favoriteKeys))
    }

    @Test
    fun `rejects keys that are no longer in either list`() {
        assertNull(resolveHomeReorder(placeKey(99), placeKey(7), placeIds, favoriteKeys))
        assertNull(resolveHomeReorder(favoriteKey("gone"), favoriteKey("one"), placeIds, favoriteKeys))
    }
}
