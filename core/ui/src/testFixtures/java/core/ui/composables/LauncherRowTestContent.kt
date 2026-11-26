package core.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasRequestFocusAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Lock
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.RailSide
import core.ui.popups.PopupRow
import core.ui.testing.createAppListRowState
import core.ui.testing.createLauncherItemActions
import core.ui.testing.createTestAppList
import core.ui.testing.testFirstApp
import core.ui.testing.testSecondApp

const val LAUNCHER_ROW_TEST_TAG: String = "launcherRow"
const val LAUNCHER_ACTION_ROW_TEST_TAG: String = "launcherActionRow"
const val PRIVATE_SPACE_ROW_TEST_TAG: String = "privateSpaceRow"

/** The width shared by Launcher row and content fixtures. */
private val LAUNCHER_ROW_TEST_WIDTH: Dp = 360.dp

/** The height shared by Launcher row and content fixtures. */
private val LAUNCHER_ROW_TEST_HEIGHT: Dp = 600.dp

/** The fixed size shared by Launcher row and content fixtures. */
val LAUNCHER_ROW_TEST_MODIFIER: Modifier = Modifier.size(width = LAUNCHER_ROW_TEST_WIDTH, height = LAUNCHER_ROW_TEST_HEIGHT)

/** Sets two Home suggestion rows over the contrasting test background. */
fun ComposeContentTestRule.setHomeSuggestionRows(itemActions: LauncherItemActions) {
    setContent {
        RowTestContent {
            HomeSuggestionRowsTestContent(itemActions = itemActions)
        }
    }
}

/** Sets Launcher row content over the shared contrasting background and test size. */
fun ComposeContentTestRule.setLauncherRowTestContent(content: @Composable (Modifier) -> Unit) {
    setContent {
        RowTestContent {
            content(LAUNCHER_ROW_TEST_MODIFIER)
        }
    }
}

/** Sets bare App List group rows in a Column over the contrasting test background. */
fun ComposeContentTestRule.setAppListGroupRows(
    itemActions: LauncherItemActions,
    appList: List<LaunchableGroup.Listed> = createTestAppList()
) {
    setLauncherRowTestContent { modifier ->
        Column(modifier = modifier) {
            appList.forEach { group ->
                LauncherItemGroup(
                    group = group,
                    rowState = createAppListRowState(),
                    itemActions = itemActions
                )
            }
        }
    }
}

/** Sets one tagged Launcher row over the contrasting test background. */
fun ComposeContentTestRule.setLauncherItemRow(
    item: Launchable,
    itemActions: LauncherItemActions = createLauncherItemActions(),
    popupRow: PopupRow = PopupRow.Home,
    isRowFocusEnabled: Boolean = true
) {
    setContent {
        RowTestContent {
            LauncherItemTestContent(
                item = item,
                itemActions = itemActions,
                popupRow = popupRow,
                isRowFocusEnabled = isRowFocusEnabled
            )
        }
    }
}

/** A column of Home suggestions, sized by [modifier]. */
@Composable
fun HomeSuggestionRowsTestContent(
    itemActions: LauncherItemActions,
    suggestions: List<Launchable> = listOf(testFirstApp, testSecondApp),
    isPopupSwipeEnabled: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Column(modifier = modifier) {
        suggestions.forEach { suggestion ->
            HomeSuggestionRow(
                suggestion = suggestion,
                railSide = RailSide.DEFAULT,
                itemActions = itemActions,
                isPopupSwipeEnabled = isPopupSwipeEnabled
            )
        }
    }
}

/** One Launcher row composed for tests, tagged so tests can inspect and drive it. */
@Composable
fun LauncherItemTestContent(
    item: Launchable,
    itemActions: LauncherItemActions = createLauncherItemActions(),
    popupRow: PopupRow = PopupRow.Home,
    isRowFocusEnabled: Boolean = true
) {
    LauncherItem(
        item = item,
        isEditingFavorites = false,
        isFavorite = false,
        isRowFocusEnabled = isRowFocusEnabled,
        isPopupSwipeEnabled = false,
        popupRow = popupRow,
        itemActions = itemActions,
        modifier = Modifier.testTag(LAUNCHER_ROW_TEST_TAG)
    )
}

/** The Lock action row composed for tests, tagged so tests can inspect and drive it. */
@Composable
fun LauncherActionRowTestContent(onClick: () -> Unit = {}) {
    LauncherActionRow(
        imageVector = FontAwesomeIcons.Solid.Lock,
        label = "Lock Private Space",
        onClick = onClick,
        modifier = Modifier.testTag(LAUNCHER_ACTION_ROW_TEST_TAG)
    )
}

/** The locked Private Space container row composed for tests, tagged so tests can inspect and drive it. */
@Composable
fun PrivateSpaceRowTestContent(isLocked: Boolean = true, onUnlock: () -> Unit = {}) {
    PrivateSpaceRow(
        isLocked = isLocked,
        onUnlock = onUnlock,
        modifier = Modifier.testTag(PRIVATE_SPACE_ROW_TEST_TAG)
    )
}

/** Finds the focusable row containing [name] in the unmerged semantics tree. */
fun ComposeContentTestRule.row(name: String): SemanticsNodeInteraction = onNode(
    hasRequestFocusAction() and hasAnyDescendant(hasText(name)),
    useUnmergedTree = true
)
