package feature.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.layout.compactIconSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class LauncherAppListTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val itemActions: LauncherItemActions = createLauncherItemActions()

    @Test
    fun `sizes the Search icon to the shared compact icon size`() {
        setAppListContent()

        val actualBounds: DpRect = searchIconBounds()

        assertEquals(compactIconSize, actualBounds.width)
        assertEquals(compactIconSize, actualBounds.height)
    }

    @Test
    fun `shows the Search label inside the screen beside the icon`() {
        setAppListContent()

        val actualLabel = composeRule.onNodeWithText(
            RuntimeEnvironment.getApplication().getString(CoreUiR.string.search),
            useUnmergedTree = true
        )
        actualLabel.assertIsDisplayed()
        val actualLabelBounds: DpRect = actualLabel.getUnclippedBoundsInRoot()
        val actualIconBounds: DpRect = searchIconBounds()
        val actualScreenBounds: DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

        assertHorizontallyInsideScreen(actualLabelBounds, actualScreenBounds, "Search label must be inside the screen")
        assertTrue("Search label must sit to the right of the icon", actualLabelBounds.left >= actualIconBounds.right)
        val actualIconCenterY: Dp = actualIconBounds.top + actualIconBounds.height / 2f
        assertTrue(
            "Search icon must be vertically centered on its label",
            actualIconCenterY >= actualLabelBounds.top && actualIconCenterY <= actualLabelBounds.bottom
        )
    }

    private fun searchIconBounds(): DpRect =
        composeRule.onNodeWithTag(SEARCH_BUTTON_ICON_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun setAppListContent() {
        composeRule.setContent {
            LauncherAppList(
                modifier = Modifier.fillMaxSize(),
                appList = emptyList(),
                lazyListState = rememberLazyListState(),
                rowState = AppListRowState(isEditingFavorites = false, favoriteKeys = emptySet(), isPopupSwipeEnabled = false),
                itemActions = itemActions,
                folderSummaries = emptyMap(),
                topOffset = 0.dp,
                bottomOffset = 0.dp,
                onSearchOpen = {}
            )
        }
    }
}
