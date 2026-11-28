package core.ui.popups

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.popups.PopupContent
import core.data.popups.PopupContentResolver
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.getString
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class HomeRoleEmptyStateTest : PopupTestBase() {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `given Shortcuts are unavailable, when the editor empty state loads, then it offers Set Rail as Home`() {
        val inputState: PopupEditState.Editing = PopupEditState.Editing(
            owner = inputApp,
            shortcuts = ShortcutsResult.Unavailable,
            hiddenIds = emptySet(),
            items = emptyList(),
            shortcutSourceNames = emptyMap()
        )
        assertHomeRoleRequest { onRequestHomeRole ->
            EditPopupSheet(
                state = inputState,
                onRequestHomeRole = onRequestHomeRole,
                onFinishEdit = {},
                onShortcutHiddenChange = { _, _ -> },
                onItemOrderChange = {},
                onRemoveItem = {},
                onAddApps = {},
                onAddShortcuts = {}
            )
        }
    }

    @Test
    fun `given Shortcuts are unavailable, when the picker empty state loads, then it offers Set Rail as Home`() {
        val inputState: ShortcutPickerState.Picking = ShortcutPickerState.Picking(
            groups = emptyList(),
            selectedKeys = emptySet(),
            shortcutsUnavailable = true
        )
        assertHomeRoleRequest { onRequestHomeRole ->
            ShortcutPickerSheet(
                state = inputState,
                onRequestHomeRole = onRequestHomeRole,
                onConfirm = {},
                onDismiss = {}
            )
        }
    }

    @Test
    fun `given Shortcuts are unavailable, when the card empty state loads, then it offers Set Rail as Home`() {
        val inputContent: PopupContent = PopupContentResolver.resolve(
            owner = inputApp,
            shortcuts = ShortcutsResult.Unavailable,
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertHomeRoleRequest { onRequestHomeRole ->
            PopupCard(
                content = inputContent,
                widget = null,
                nowMillis = 0L,
                resize = null,
                expandedNotificationKeys = emptySet(),
                sentNotificationKeys = emptySet(),
                notificationActions = PopupNotificationActions(
                    onOpen = {},
                    onDismiss = {},
                    onToggleExpanded = {},
                    onAction = { _, _ -> },
                    onReply = { _, _, _ -> }
                ),
                onHeaderClick = null,
                onShortcutClick = {},
                onItemClick = {},
                onItemLongClick = {},
                onAddItems = {},
                onRequestHomeRole = onRequestHomeRole,
                onRequestQuietModeOff = {},
                onWidgetLongPress = {},
                onWidgetUnpause = {},
                onWidgetPageChanged = {},
                onResizeDrag = {},
                onResizeReset = {},
                onResizeDone = {},
                onResizeTap = {},
                onClose = {},
                onLongPress = {}
            )
        }
    }

    private fun assertHomeRoleRequest(content: @Composable (() -> Unit) -> Unit) {
        val mockOnRequestHomeRole: () -> Unit = mockk(relaxed = true)
        composeRule.setContent {
            RowTestContent {
                content(mockOnRequestHomeRole)
            }
        }
        composeRule.onNodeWithText(getString(CoreUiR.string.set_rail_as_home))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        verify(exactly = 1) { mockOnRequestHomeRole() }
    }
}
