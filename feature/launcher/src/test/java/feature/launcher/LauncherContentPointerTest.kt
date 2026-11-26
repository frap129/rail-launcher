package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.testing.TEST_SDK
import core.ui.composables.MenuSuggestion
import core.ui.testing.createFavorite
import core.ui.testing.createLauncherItemActions
import core.ui.testing.createLongPressRecordingActions
import core.ui.testing.testFirstApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SCROLLABLE_APP_COUNT: Int = 40
private const val MOUSE_WHEEL_DELTA: Float = 500f

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class LauncherContentPointerTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `a secondary click opens the long-press menu on a Favourite row`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setHomeFavoriteRows(
            favorites = listOf(testFirstApp),
            itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses)
        )
        composeRule.onAllNodesWithText("Alpha").onFirst().performMouseInput { rightClick() }
        assertEquals(listOf(testFirstApp to MenuSuggestion.None), actualLongPresses)
    }

    @Test
    fun `a secondary click does not open a menu while widgets are edited`() {
        assertHomeFavouriteIgnoresSecondaryClick(isEditingWidgets = true)
    }

    @Test
    fun `a secondary click does not open a menu while widgets are resized`() {
        assertHomeFavouriteIgnoresSecondaryClick(isResizing = true)
    }

    @Test
    fun `the mouse wheel scrolls Home`() {
        val inputFavorites: List<Launchable> = createScrollableApps()
        val inputLazyListState: LazyListState = LazyListState()
        composeRule.setHomeFavoriteRows(
            favorites = inputFavorites,
            itemActions = createLauncherItemActions(),
            lazyListState = inputLazyListState
        )
        assertMouseWheelScrolls(lazyListState = inputLazyListState, firstFavoriteLabel = inputFavorites.first().name)
    }

    @Test
    fun `the mouse wheel scrolls the App List`() {
        val inputApps: List<Launchable> = createScrollableApps()
        val inputLazyListState: LazyListState = LazyListState()
        composeRule.setAppListRows(
            itemActions = createLauncherItemActions(),
            appList = listOf(LaunchableGroup.InitialGroup(label = "A", items = inputApps)),
            lazyListState = inputLazyListState
        )
        assertMouseWheelScrolls(lazyListState = inputLazyListState, firstFavoriteLabel = inputApps.first().name)
    }

    private fun assertMouseWheelScrolls(lazyListState: LazyListState, firstFavoriteLabel: String) {
        composeRule.waitForIdle()
        val inputInitialItemIndex: Int = lazyListState.firstVisibleItemIndex
        val inputInitialItemOffset: Int = lazyListState.firstVisibleItemScrollOffset
        composeRule.onAllNodesWithText(firstFavoriteLabel).onFirst().performMouseInput {
            moveTo(center)
            scroll(delta = MOUSE_WHEEL_DELTA)
        }
        composeRule.waitForIdle()
        assertTrue(
            lazyListState.firstVisibleItemIndex > inputInitialItemIndex ||
                lazyListState.firstVisibleItemScrollOffset > inputInitialItemOffset
        )
    }

    private fun createScrollableApps(): List<Launchable> = (1..SCROLLABLE_APP_COUNT).map { index: Int ->
        createFavorite(index = index)
    }

    private fun assertHomeFavouriteIgnoresSecondaryClick(isResizing: Boolean = false, isEditingWidgets: Boolean = false) {
        val actualLongPresses: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeFavoriteRows(
            favorites = listOf(testFirstApp),
            itemActions = createLauncherItemActions().copy(onItemLongPress = { item, _ -> actualLongPresses += item }),
            isResizing = isResizing,
            isEditingWidgets = isEditingWidgets
        )
        composeRule.onAllNodesWithText("Alpha").onFirst().performMouseInput { rightClick() }
        composeRule.waitForIdle()
        assertTrue(actualLongPresses.isEmpty())
    }
}
