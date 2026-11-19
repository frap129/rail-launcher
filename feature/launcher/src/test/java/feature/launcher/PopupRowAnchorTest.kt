package feature.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.turbine.test
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.testing.TEST_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Two visible rows of one recently installed App, with a Pop-up open from one of them. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupRowAnchorTest : PopupSessionTestBase() {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val rowState: AppListRowState =
        AppListRowState(isEditingFavorites = false, favoriteKeys = emptySet(), isPopupSwipeEnabled = true)

    @Test
    fun `keeps a Pop-up opened from the Initial group row on that row when the other copy relayouts`() = runTest {
        assertPopupFollowsOnlyOpeningRow(openingRow = PopupRow.Group, otherRow = PopupRow.RecentlyInstalled)
    }

    @Test
    fun `keeps a Pop-up opened from the Recently installed row on that row when the other copy relayouts`() = runTest {
        assertPopupFollowsOnlyOpeningRow(openingRow = PopupRow.RecentlyInstalled, otherRow = PopupRow.Group)
    }

    private suspend fun TestScope.assertPopupFollowsOnlyOpeningRow(openingRow: PopupRow, otherRow: PopupRow) {
        val card: PopupCardViewModel = createCard()
        val actualAnchors: MutableList<PopupAnchor> = mutableListOf()
        val topPadding: MutableState<Dp> = mutableStateOf(0.dp)
        val inputItemActions: LauncherItemActions = createLauncherItemActions().copy(
            onItemAnchorChanged = { launchable, anchor ->
                actualAnchors += anchor
                card.onAnchorChanged(launchable.key, anchor)
            }
        )
        composeRule.setContent {
            LauncherAppList(
                modifier = Modifier.fillMaxSize().padding(top = topPadding.value),
                appList = listOf(LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp))),
                recentlyInstalled = RecentlyInstalledSection(apps = listOf(inputApp)),
                lazyListState = LazyListState(),
                rowState = rowState,
                itemActions = inputItemActions,
                folderSummaries = emptyMap(),
                topOffset = 0.dp,
                bottomOffset = 0.dp,
                railSide = inputItemActions.railSide,
                showRail = true
            )
        }
        composeRule.waitForIdle()
        card.popupState.test {
            card.open(inputApp, actualAnchors.last { anchor -> anchor.row == openingRow })
            expectMostRecentItem()
            actualAnchors.clear()
            topPadding.value = 24.dp
            composeRule.waitForIdle()
            val actualOpeningAnchor: PopupAnchor = actualAnchors.last { anchor -> anchor.row == openingRow }
            val actualOtherAnchor: PopupAnchor = actualAnchors.last { anchor -> anchor.row == otherRow }
            assertNotEquals(actualOpeningAnchor.rowTop, actualOtherAnchor.rowTop)
            assertEquals(actualOpeningAnchor, (expectMostRecentItem() as PopupUiState.Open).anchor)
        }
    }
}
