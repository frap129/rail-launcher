package feature.launcher

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
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
        setSearchContent(
            results = listOf(SearchResult.TypedUrlRow(url = INPUT_URL)),
            onOpenSearchRow = { row -> actualRow = row }
        )

        composeRule.onNodeWithText(context.getString(R.string.search_open_url, INPUT_URL)).performClick()

        composeRule.runOnIdle { assertEquals(SearchResult.TypedUrlRow(url = INPUT_URL), actualRow) }
    }

    @Test
    fun `opens the web search when its row is tapped`() {
        var actualRow: SearchResult.ExternalRow? = null
        setSearchContent(
            results = listOf(SearchResult.WebSearchRow(url = INPUT_WEB_URL)),
            onOpenSearchRow = { row -> actualRow = row }
        )

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
        setSearchContent(resultsState = inputResults, query = mutableStateOf("item"))

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
            results = listOf(
                SearchResult.LaunchableResult(inputFirst),
                SearchResult.LaunchableResult(inputSecond)
            ),
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

    @Test
    fun `puts the cursor after a starting query`() {
        val inputQuery: MutableState<String> = mutableStateOf(QUERY)
        setSearchContent(query = inputQuery)

        typeIntoQuery("a")

        assertEquals(QUERY + "a", inputQuery.value)
    }

    @Test
    fun `appends the next character after an external query change`() {
        val inputQuery: MutableState<String> = mutableStateOf("g")
        setSearchContent(query = inputQuery)

        composeRule.runOnIdle { inputQuery.value = "gm" }
        typeIntoQuery("a")

        assertEquals("gma", inputQuery.value)
    }

    @Test
    fun `keeps every character typed while an earlier echo arrives`() {
        val inputQuery: MutableState<String> = mutableStateOf(QUERY)
        val actualQueries: MutableList<String> = mutableListOf()
        setSearchContent(
            query = inputQuery,
            onQueryChange = { text -> actualQueries += text }
        )

        typeIntoQuery("a")
        typeIntoQuery("b")
        composeRule.runOnIdle { inputQuery.value = QUERY + "a" }
        typeIntoQuery("c")

        assertEquals(listOf(QUERY + "a", QUERY + "ab", QUERY + "abc"), actualQueries)
    }

    @Test
    fun `clears the field in one press when the echo and clear coalesce`() {
        val inputQuery: MutableState<String> = mutableStateOf(QUERY)
        setSearchContent(query = inputQuery)

        typeIntoQuery("a")
        composeRule.runOnIdle {
            inputQuery.value = QUERY + "a"
            inputQuery.value = ""
        }
        composeRule.waitForIdle()

        assertEquals("", queryFieldText())
    }

    @Test
    fun `keeps the typed text and cursor when a stale echo arrives after an edit`() {
        val inputQuery: MutableState<String> = mutableStateOf("")
        setSearchContent(query = inputQuery, onQueryChange = {})

        typeIntoQuery("gmail")
        queryField().performTextReplacement("gxail")
        queryField().performTextInputSelection(TextRange(2))
        composeRule.runOnIdle { inputQuery.value = "gmail" }
        composeRule.waitForIdle()

        assertEquals("gxail", queryFieldText())
        assertEquals(TextRange(2), queryFieldSelection())
    }

    @Test
    fun `adopts an external truncation that is not an echo`() {
        val inputQuery: MutableState<String> = mutableStateOf("g")
        setSearchContent(query = inputQuery, onQueryChange = {})

        typeIntoQuery("mail")
        composeRule.runOnIdle { inputQuery.value = "gma" }
        composeRule.waitForIdle()

        assertEquals("gma", queryFieldText())
    }

    @Test
    fun `passes the field text to Go`() {
        var actualGoQuery: String? = null
        setSearchContent(
            query = mutableStateOf(""),
            onSearchGo = { query ->
                actualGoQuery = query
                null
            }
        )

        typeIntoQuery("a")
        queryField().performImeAction()

        composeRule.runOnIdle { assertEquals("a", actualGoQuery) }
    }

    private fun contactRow(): SearchResult.ContactRow = SearchResult.ContactRow(contact = createSearchContact())

    private fun queryField(): SemanticsNodeInteraction = composeRule.onNode(hasSetTextAction())

    private fun typeIntoQuery(text: String) {
        queryField().performTextInput(text)
    }

    private fun queryFieldText(): String = queryField()
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    private fun queryFieldSelection(): TextRange = queryField()
        .fetchSemanticsNode()
        .config[SemanticsProperties.TextSelectionRange]

    private fun setSearchContent(
        results: List<SearchResult> = emptyList(),
        resultsState: MutableState<List<SearchResult>> = mutableStateOf(results),
        isBottomAnchored: Boolean = false,
        autoLaunchResult: SearchResult.LaunchableResult? = null,
        itemActions: LauncherItemActions = this.itemActions,
        onOpenSearchRow: (SearchResult.ExternalRow) -> Unit = {},
        onContactRowAction: (SearchResult.ContactRow, ContactRowAction) -> Unit = { _, _ -> },
        query: MutableState<String> = mutableStateOf(QUERY),
        onQueryChange: (String) -> Unit = { text -> query.value = text },
        onSearchGo: (String) -> SearchResult? = { null }
    ) {
        composeRule.setContent {
            val inputResults: List<SearchResult> = resultsState.value
            LauncherSearch(
                modifier = Modifier,
                search = LauncherUiState.Search(
                    query = query.value,
                    results = inputResults,
                    topResult = inputResults.firstOrNull(),
                    isShowingSuggestions = false,
                    isBottomAnchored = isBottomAnchored,
                    autoLaunchResult = autoLaunchResult
                ),
                shouldAutoLaunch = { true },
                itemActions = itemActions,
                onQueryChange = onQueryChange,
                onSearchGo = onSearchGo,
                onCopyCalculatorRow = {},
                onOpenSearchRow = onOpenSearchRow,
                onContactRowAction = onContactRowAction
            )
        }
    }
}
