package feature.launcher

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import core.data.launchables.model.Launchable
import core.data.prefs.RailSide
import core.data.search.SearchResult
import core.data.testing.TEST_SDK
import core.data.testing.TEST_WEB_SUGGESTION
import core.data.testing.TEST_WEB_SUGGESTION_URL
import core.data.testing.createRailLaunchable
import core.data.testing.createSearchContact
import core.data.testing.createWebSuggestionRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val QUERY: String = "example.com"
private const val INPUT_URL: String = "https://example.com"
private const val INPUT_WEB_URL: String = "https://www.google.com/search?q=example.com"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class LauncherSearchTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val itemActions: LauncherItemActions = LauncherItemActions(
        onItemTap = { _, _ -> },
        onItemLongPress = {},
        onToggleFavorite = {},
        onItemOpenPopup = { _, _ -> },
        onItemAnchorChanged = { _, _ -> },
        privateSpaceActions = PrivateSpaceActions(onUnlock = {}, onLock = {}, onSettings = null),
        railBounds = { null },
        railSide = RailSide.DEFAULT
    )

    @Test
    fun `opens the typed URL when its row is tapped`() {
        var actualRow: SearchResult.ExternalRow? = null
        setSearchContent(results = listOf(SearchResult.TypedUrlRow(url = INPUT_URL)), onOpenSearchRow = { row -> actualRow = row })

        composeRule.onNodeWithText(context.getString(R.string.search_open_url, INPUT_URL)).performClick()

        composeRule.runOnIdle { assertEquals(SearchResult.TypedUrlRow(url = INPUT_URL), actualRow) }
    }

    @Test
    fun `opens the web search when its row is tapped`() {
        var actualRow: SearchResult.ExternalRow? = null
        setSearchContent(results = listOf(SearchResult.WebSearchRow(url = INPUT_WEB_URL)), onOpenSearchRow = { row -> actualRow = row })

        composeRule.onNodeWithText(context.getString(R.string.search_web_action)).performClick()

        composeRule.runOnIdle { assertEquals(SearchResult.WebSearchRow(url = INPUT_WEB_URL), actualRow) }
    }

    @Test
    fun `opens the Web suggestion when its row is tapped`() {
        val inputRow: SearchResult.WebSuggestionRow = createWebSuggestionRow(
            suggestion = TEST_WEB_SUGGESTION,
            url = TEST_WEB_SUGGESTION_URL
        )
        var actualRow: SearchResult.ExternalRow? = null
        setSearchContent(results = listOf(inputRow), onOpenSearchRow = { row -> actualRow = row })

        composeRule.onNodeWithText(inputRow.suggestion).performClick()

        composeRule.runOnIdle { assertEquals(inputRow, actualRow) }
    }

    @Test
    fun `keeps the scroll position when Web suggestions arrive`() {
        val inputResults: MutableState<List<SearchResult>> = mutableStateOf(
            (1..20).map { index -> SearchResult.LaunchableResult(createRailLaunchable("Item $index")) }
        )
        composeRule.setContent {
            LauncherSearch(
                modifier = Modifier,
                search = LauncherUiState.Search(
                    query = "item",
                    results = inputResults.value,
                    topResult = null,
                    isShowingSuggestions = false,
                    isBottomAnchored = false,
                    autoLaunchResult = null
                ),
                shouldAutoLaunch = { true },
                itemActions = itemActions,
                onQueryChange = {},
                onSearchGo = { null },
                onCopyCalculatorRow = {},
                onOpenSearchRow = {},
                onContactRowAction = { _, _ -> }
            )
        }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(19)
        composeRule.onAllNodesWithText("Item 20").onFirst().assertIsDisplayed()

        composeRule.runOnIdle {
            inputResults.value = inputResults.value + createWebSuggestionRow(
                suggestion = TEST_WEB_SUGGESTION,
                url = TEST_WEB_SUGGESTION_URL
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Item 20").onFirst().assertIsDisplayed()
    }

    @Test
    fun `opens the Play Store row through its own action`() {
        val inputRow: SearchResult.PlayStoreRow = SearchResult.PlayStoreRow(
            marketUrl = "market://search?q=example.com",
            webUrl = "https://play.google.com/store/search?q=example.com&c=apps"
        )
        var actualRow: SearchResult.ExternalRow? = null
        setSearchContent(results = listOf(inputRow), onOpenSearchRow = { row -> actualRow = row })

        composeRule.onNodeWithText(context.getString(R.string.search_play_store_action)).performClick()

        composeRule.runOnIdle { assertEquals(inputRow, actualRow) }
    }

    @Test
    fun `shows the Top result nearest the keyboard when results are bottom-anchored`() {
        val inputFirst: Launchable = createRailLaunchable("Alpha")
        val inputSecond: Launchable = createRailLaunchable("Beta")
        setSearchContent(
            results = listOf(SearchResult.LaunchableResult(inputFirst), SearchResult.LaunchableResult(inputSecond)),
            isBottomAnchored = true
        )

        val actualTopBottom: Float = composeRule.onAllNodesWithText(inputFirst.name).onFirst().fetchSemanticsNode().boundsInRoot.bottom
        val actualSecondBottom: Float = composeRule.onAllNodesWithText(inputSecond.name).onFirst().fetchSemanticsNode().boundsInRoot.bottom

        assertTrue(actualTopBottom > actualSecondBottom)
    }

    @Test
    fun `opens an Auto-launch result through its row tap`() {
        val inputLaunchable: Launchable = createRailLaunchable("Alpha")
        val inputResult: SearchResult.LaunchableResult = SearchResult.LaunchableResult(inputLaunchable)
        var actualTapCount: Int = 0
        val inputItemActions: LauncherItemActions = itemActions.copy(onItemTap = { _, _ -> actualTapCount++ })
        setSearchContent(
            results = listOf(inputResult),
            autoLaunchResult = inputResult,
            itemActions = inputItemActions
        )

        composeRule.runOnIdle { assertEquals(1, actualTapCount) }
    }

    @Test
    fun `opens a Contact row through its row tap`() {
        val inputRow: SearchResult.ContactRow = contactRow()
        var actualAction: ContactRowAction? = null
        setSearchContent(
            results = listOf(inputRow),
            onContactRowAction = { _, action -> actualAction = action }
        )

        composeRule.onNodeWithText(inputRow.contact.name).performClick()

        composeRule.runOnIdle { assertEquals(ContactRowAction.Open, actualAction) }
    }

    @Test
    fun `dials a Contact row through its call button`() {
        val inputRow: SearchResult.ContactRow = contactRow()
        var actualAction: ContactRowAction? = null
        setSearchContent(
            results = listOf(inputRow),
            onContactRowAction = { _, action -> actualAction = action }
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_contact_call, inputRow.contact.name))
            .performClick()

        composeRule.runOnIdle { assertEquals(ContactRowAction.Dial, actualAction) }
    }

    @Test
    fun `messages a Contact row through its message button`() {
        val inputRow: SearchResult.ContactRow = contactRow()
        var actualAction: ContactRowAction? = null
        setSearchContent(
            results = listOf(inputRow),
            onContactRowAction = { _, action -> actualAction = action }
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_contact_message, inputRow.contact.name))
            .performClick()

        composeRule.runOnIdle { assertEquals(ContactRowAction.Message, actualAction) }
    }

    @Test
    fun `hides the call and message buttons for a contact without a number`() {
        val inputRow: SearchResult.ContactRow = SearchResult.ContactRow(contact = createSearchContact(phoneNumber = null))
        setSearchContent(results = listOf(inputRow))

        composeRule.onNodeWithText(inputRow.contact.name).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.search_contact_call, inputRow.contact.name))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.search_contact_message, inputRow.contact.name))
            .assertDoesNotExist()
    }

    private fun contactRow(): SearchResult.ContactRow = SearchResult.ContactRow(contact = createSearchContact())

    private fun setSearchContent(
        results: List<SearchResult>,
        isBottomAnchored: Boolean = false,
        autoLaunchResult: SearchResult.LaunchableResult? = null,
        itemActions: LauncherItemActions = this.itemActions,
        onOpenSearchRow: (SearchResult.ExternalRow) -> Unit = {},
        onContactRowAction: (SearchResult.ContactRow, ContactRowAction) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            LauncherSearch(
                modifier = Modifier,
                search = LauncherUiState.Search(
                    query = QUERY,
                    results = results,
                    topResult = results.firstOrNull(),
                    isShowingSuggestions = false,
                    isBottomAnchored = isBottomAnchored,
                    autoLaunchResult = autoLaunchResult
                ),
                shouldAutoLaunch = { true },
                itemActions = itemActions,
                onQueryChange = {},
                onSearchGo = { null },
                onCopyCalculatorRow = {},
                onOpenSearchRow = onOpenSearchRow,
                onContactRowAction = onContactRowAction
            )
        }
    }
}
