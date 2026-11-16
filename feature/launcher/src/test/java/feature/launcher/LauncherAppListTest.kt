package feature.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import core.data.launchables.model.LaunchableGroup
import core.data.testing.TEST_SDK
import core.data.testing.createRailLaunchable
import core.ui.R as CoreUiR
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
    private val rowState: AppListRowState =
        AppListRowState(isEditingFavorites = false, favoriteKeys = emptySet(), isPopupSwipeEnabled = false)

    @Test
    fun `sizes the Search icon to the shared compact icon size`() {
        setAppListContent()

        assertCompactIconSize(searchIcon())
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
        val actualIconBounds: DpRect = searchIcon().getUnclippedBoundsInRoot()
        val actualScreenBounds: DpRect = composeRule.screenBounds()

        assertHorizontallyInsideScreen(actualLabelBounds, actualScreenBounds, "Search label must be inside the screen")
        assertTrue("Search label must sit to the right of the icon", actualLabelBounds.left >= actualIconBounds.right)
        val actualIconCenterY: Dp = actualIconBounds.top + actualIconBounds.height / 2f
        assertTrue(
            "Search icon must be vertically centered on its label",
            actualIconCenterY >= actualLabelBounds.top && actualIconCenterY <= actualLabelBounds.bottom
        )
    }

    @Test
    fun `keeps the released Rail group where the Rail scrub showed it`() {
        val inputLabel: String = "A"
        val inputGroup: LaunchableGroup.Listed = LaunchableGroup.InitialGroup(
            label = inputLabel,
            items = listOf(createRailLaunchable("Alpha"))
        )
        val inputTopOffset: Dp = 160.dp
        val inputReleasedListState: LazyListState = with(composeRule.density) {
            createReleasedAppListState(groupIndex = 0, listStartOffsetPx = inputTopOffset.roundToPx())
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                LauncherItemGroup(
                    modifier = Modifier.fillMaxSize().padding(top = inputTopOffset),
                    group = inputGroup,
                    rowState = rowState,
                    itemActions = itemActions
                )
                AppListContent(appList = listOf(inputGroup), lazyListState = inputReleasedListState, topOffset = inputTopOffset)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        val actualHeaderTops: List<Dp> = composeRule.onAllNodesWithText(inputLabel, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> with(composeRule.density) { node.boundsInRoot.top.toDp() } }
        assertEquals("Released App List must keep the scrubbed group in place", actualHeaderTops.first(), actualHeaderTops.last())
    }

    private fun searchIcon(): SemanticsNodeInteraction = composeRule.onNodeWithTag(SEARCH_BUTTON_ICON_TEST_TAG, useUnmergedTree = true)

    private fun setAppListContent() {
        composeRule.setContent {
            AppListContent(appList = emptyList(), lazyListState = rememberLazyListState(), topOffset = 0.dp)
        }
    }

    @Composable
    private fun AppListContent(appList: List<LaunchableGroup.Listed>, lazyListState: LazyListState, topOffset: Dp) {
        LauncherAppList(
            modifier = Modifier.fillMaxSize(),
            appList = appList,
            lazyListState = lazyListState,
            rowState = rowState,
            itemActions = itemActions,
            folderSummaries = emptyMap(),
            topOffset = topOffset,
            bottomOffset = 0.dp,
            onSearchOpen = {}
        )
    }
}
