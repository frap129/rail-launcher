package feature.launcher

import core.ui.model.data.favoriteKey
import core.ui.model.data.placeKey
import feature.launcher.widgets.MEDIA_PLAYER_KEY
import feature.launcher.widgets.WIDGET_AREA_OFFSET_KEY
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
    fun `resolves place rows to place indices when the Media Player offsets the list`() {
        val inputRows: List<ReorderRow> = listOf(
            ReorderRow(listIndex = 0, key = MEDIA_PLAYER_KEY),
            ReorderRow(listIndex = 1, key = placeKey(7)),
            ReorderRow(listIndex = 2, key = placeKey(9)),
            ReorderRow(listIndex = 3, key = placeKey(11))
        )
        val expectedReorder: HomeReorder.Place = HomeReorder.Place(fromIndex = 0, toIndex = 1)
        val actualReorder: HomeReorder.Place? = resolvePlaceReorder(
            rows = inputRows,
            fromListIndex = 1,
            toListIndex = 2,
            placeIds = placeIds
        )
        assertEquals(expectedReorder, actualReorder)
    }

    @Test
    fun `rejects place rows dropped on rows that hold no place`() {
        val inputRows: List<ReorderRow> = listOf(
            ReorderRow(listIndex = 0, key = MEDIA_PLAYER_KEY),
            ReorderRow(listIndex = 1, key = WIDGET_AREA_OFFSET_KEY),
            ReorderRow(listIndex = 2, key = placeKey(7))
        )
        assertNull(resolvePlaceReorder(inputRows, fromListIndex = 2, toListIndex = 0, placeIds = placeIds))
        assertNull(resolvePlaceReorder(inputRows, fromListIndex = 2, toListIndex = 1, placeIds = placeIds))
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
