package core.ui

import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.ui.model.data.favoriteKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val FIRST_FAVORITE: Launchable = createApp(name = "First", packageName = "com.example.first")
private val SECOND_FAVORITE: Launchable = createApp(name = "Second", packageName = "com.example.second")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class SettledHomeIconsTest {
    @Test
    fun `draws a Home with no visible Favorite icon at once`() {
        val settledHomeIcons: SettledHomeIcons = SettledHomeIcons()
        val inputVisibleFavoriteKeys: List<String> = emptyList()
        val actualHasSettledEvery: Boolean = settledHomeIcons.hasSettledEvery(visibleFavoriteKeys = inputVisibleFavoriteKeys)
        assertTrue(actualHasSettledEvery)
    }

    @Test
    fun `waits for every visible Favorite icon to draw or fail`() {
        val settledHomeIcons: SettledHomeIcons = SettledHomeIcons()
        val inputVisibleFavoriteKeys: List<String> = listOf(FIRST_FAVORITE, SECOND_FAVORITE).map { favorite ->
            favoriteKey(key = favorite.key)
        }
        assertFalse(settledHomeIcons.hasSettledEvery(visibleFavoriteKeys = inputVisibleFavoriteKeys))
        settledHomeIcons.settle(favorite = FIRST_FAVORITE)
        assertFalse(settledHomeIcons.hasSettledEvery(visibleFavoriteKeys = inputVisibleFavoriteKeys))
        settledHomeIcons.settle(favorite = SECOND_FAVORITE)
        assertTrue(settledHomeIcons.hasSettledEvery(visibleFavoriteKeys = inputVisibleFavoriteKeys))
    }

    @Test
    fun `ignores Favorite icons Home does not show`() {
        val settledHomeIcons: SettledHomeIcons = SettledHomeIcons()
        settledHomeIcons.settle(favorite = SECOND_FAVORITE)
        val actualHasSettledEvery: Boolean = settledHomeIcons.hasSettledEvery(
            visibleFavoriteKeys = listOf(favoriteKey(key = FIRST_FAVORITE.key))
        )
        assertFalse(actualHasSettledEvery)
    }
}
