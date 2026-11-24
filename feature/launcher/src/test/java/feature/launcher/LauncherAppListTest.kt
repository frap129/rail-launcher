package feature.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.prefs.RailSide
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createRailLaunchable
import core.ui.composables.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
                LauncherAppListTestContent(
                    appList = listOf(inputGroup),
                    lazyListState = inputReleasedListState,
                    topOffset = inputTopOffset,
                    rowState = rowState,
                    itemActions = itemActions
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        val actualHeaderTops: List<Dp> = composeRule.onAllNodesWithText(inputLabel, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> with(composeRule.density) { node.boundsInRoot.top.toDp() } }
        assertEquals("Released App List must keep the scrubbed group in place", actualHeaderTops.first(), actualHeaderTops.last())
    }

    @Test
    fun `drops the Rail gutter from the App List when Show Rail is off`() {
        composeRule.setContent {
            Column {
                LauncherAppListTestContent(
                    modifier = Modifier.weight(1f),
                    appList = listOf(groupOf(label = "Shown", name = "Alpha")),
                    lazyListState = LazyListState(),
                    topOffset = 0.dp,
                    rowState = rowState,
                    itemActions = itemActions,
                    railSide = RailSide.START,
                    showRail = true
                )
                LauncherAppListTestContent(
                    modifier = Modifier.weight(1f),
                    appList = listOf(groupOf(label = "Hidden", name = "Beta")),
                    lazyListState = LazyListState(),
                    topOffset = 0.dp,
                    rowState = rowState,
                    itemActions = itemActions,
                    railSide = RailSide.START,
                    showRail = false
                )
            }
        }

        val actualShownStart: Dp = headerStart(label = "Shown")
        val actualHiddenStart: Dp = headerStart(label = "Hidden")

        assertEquals(railGutterWidth.value, (actualShownStart - actualHiddenStart).value, 1f)
    }

    @Test
    fun `announces the Recently installed title as a heading`() {
        val inputSection: RecentlyInstalledSection = RecentlyInstalledSection(apps = listOf(createApp(name = "Fresh")))
        val expectedTitle: String = getString(R.string.recently_installed)

        composeRule.setContent {
            LauncherAppListTestContent(
                appList = emptyList(),
                recentlyInstalled = inputSection,
                lazyListState = LazyListState(),
                topOffset = 0.dp,
                rowState = rowState,
                itemActions = itemActions
            )
        }

        composeRule.onNodeWithText(expectedTitle, useUnmergedTree = true).assert(isHeading())
    }

    private fun groupOf(label: String, name: String): LaunchableGroup.InitialGroup =
        LaunchableGroup.InitialGroup(label = label, items = listOf(createRailLaunchable(name)))

    private fun headerStart(label: String): Dp = with(composeRule.density) {
        composeRule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.left.toDp()
    }

    private companion object {
        /** The 48dp that the Rail's touch column adds beyond the plain edge padding. */
        val railGutterWidth: Dp = 48.dp
    }
}
