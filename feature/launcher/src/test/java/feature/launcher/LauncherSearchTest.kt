package feature.launcher

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.prefs.RailSide
import core.data.prefs.SearchEngine
import core.data.profiles.ProfileKind
import core.data.search.SearchResult
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.TEST_SDK
import core.data.testing.TEST_WEB_SUGGESTION
import core.data.testing.TEST_WEB_SUGGESTION_URL
import core.data.testing.WORK_USER_ID
import core.data.testing.createProfileInfo
import core.data.testing.createRailLaunchable
import core.data.testing.createSearchContact
import core.data.testing.createShortcut
import core.data.testing.createWebSuggestionRow
import core.ui.R as CoreUiR
import core.ui.layout.homeEdgePadding
import core.ui.layout.homeRailStartInset
import core.ui.layout.minTouchTargetSize
import core.ui.search.SearchContactsUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

private const val QUERY: String = "example.com"
private const val INPUT_URL: String = "https://example.com"
private const val INPUT_WEB_URL: String = "https://www.google.com/search?q=example.com"
private const val CLOSE_SHEET: String = "Close sheet"

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class LauncherSearchTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val itemActions: LauncherItemActions = createLauncherItemActions()

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
    fun `hints that Search covers more than apps and folders`() {
        setSearchContent(query = mutableStateOf(""))

        composeRule
            .onNodeWithText("Search apps, folders, Shortcuts and more", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `announces the Top result row with the Enter-opens marker`() {
        val inputLaunchable: Launchable = createRailLaunchable("Alpha")
        setSearchContent(results = listOf(SearchResult.LaunchableResult(inputLaunchable)))

        assertRowDescription(
            rowText = inputLaunchable.name,
            expectedDescription = "Alpha, Top result, Enter opens"
        )
        composeRule.onNodeWithText("Top result").assertDoesNotExist()
    }

    @Test
    fun `announces a Shortcut Top result with its publisher and Work name`() {
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.gmail",
            shortLabel = "Mail",
            profileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        ).copy(publisherName = "Gmail", badgedLabel = "Work Mail")
        setSearchContent(results = listOf(SearchResult.LaunchableResult(inputShortcut)))

        assertRowDescription(
            rowText = inputShortcut.name,
            expectedDescription = "Work Mail, Gmail, Top result, Enter opens"
        )
    }

    @Test
    fun `announces a non-Top Shortcut with its publisher and Work name`() {
        val inputTop: Launchable = createRailLaunchable("Alpha")
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.gmail",
            shortLabel = "Mail",
            profileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        ).copy(publisherName = "Gmail", badgedLabel = "Work Mail")
        setSearchContent(
            results = listOf(
                SearchResult.LaunchableResult(inputTop),
                SearchResult.LaunchableResult(inputShortcut)
            )
        )

        assertRowDescription(
            rowText = inputShortcut.name,
            expectedDescription = "Work Mail, Gmail"
        )
    }

    @Test
    fun `announces a Folder Top result with the Enter-opens marker`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "Games")
        setSearchContent(results = listOf(SearchResult.LaunchableResult(inputFolder)))

        assertRowDescription(
            rowText = inputFolder.name,
            expectedDescription = "Games, Top result, Enter opens"
        )
    }

    @Test
    fun `announces a Web search Top result with the Enter-opens marker`() {
        setSearchContent(results = listOf(SearchResult.WebSearchRow(url = INPUT_WEB_URL)))

        assertRowDescription(
            rowText = "Search the web",
            expectedDescription = "Search the web, Top result, Enter opens"
        )
    }

    @Test
    fun `announces a Calculator Top result as copied by Enter`() {
        val inputRow: SearchResult.CalculatorRow = SearchResult.CalculatorRow(expression = "12*7", value = "84")
        setSearchContent(results = listOf(inputRow))

        assertRowDescription(
            rowText = "12*7 = 84",
            expectedDescription = "12*7 = 84, Top result, Enter copies"
        )
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

    @Test
    fun `opens the Top result through Go`() {
        val inputRow: SearchResult.WebSearchRow = SearchResult.WebSearchRow(url = INPUT_WEB_URL)
        var actualRow: SearchResult.ExternalRow? = null
        setSearchContent(
            results = listOf(inputRow),
            onSearchGo = { inputRow },
            onOpenSearchRow = { row -> actualRow = row }
        )

        queryField().performImeAction()

        composeRule.runOnIdle { assertEquals(inputRow, actualRow) }
    }

    @Test
    fun `clears the query when the clear button is tapped`() {
        val inputQuery: MutableState<String> = mutableStateOf(QUERY)
        setSearchContent(query = inputQuery)

        composeRule.onNodeWithContentDescription(context.getString(R.string.clear_search)).performClick()

        composeRule.runOnIdle {
            assertEquals("", inputQuery.value)
            assertEquals("", queryFieldText())
        }
    }

    @Test
    fun `sizes the clear Search icon to the shared compact icon size`() {
        setSearchContent(query = mutableStateOf(QUERY))

        assertCompactIconSize(searchFieldControl(contentDescriptionResId = R.string.clear_search, useUnmergedTree = true))
    }

    @Test
    fun `keeps the clear Search button at the shared touch target size`() {
        setSearchContent(query = mutableStateOf(QUERY))

        assertSearchFieldTouchTarget(searchFieldControl(contentDescriptionResId = R.string.clear_search))
    }

    @Test
    fun `sizes the Search options icon to the shared compact icon size`() {
        setSearchContent(query = mutableStateOf(""))

        assertCompactIconSize(searchFieldControl(contentDescriptionResId = R.string.search_options, useUnmergedTree = true))
    }

    @Test
    fun `keeps the Search options button at the shared touch target size`() {
        setSearchContent(query = mutableStateOf(""))

        assertSearchFieldTouchTarget(searchFieldControl(contentDescriptionResId = R.string.search_options))
    }

    @Test
    fun `keeps the query field at the search bar height with and without text`() {
        setSearchContent(query = mutableStateOf(""))

        queryField().assertHeightIsEqualTo(SearchBarDefaults.InputFieldHeight)
        typeIntoQuery("g")
        queryField().assertHeightIsEqualTo(SearchBarDefaults.InputFieldHeight)
    }

    @Test
    fun `starts the query field 16dp from the screen edge`() {
        setSearchContent(query = mutableStateOf(""))

        val actualFieldBounds: DpRect = queryField().getUnclippedBoundsInRoot()
        val actualScreenBounds: DpRect = composeRule.screenBounds()

        assertEquals(16.dp, actualFieldBounds.left - actualScreenBounds.left)
        assertEquals(16.dp, actualScreenBounds.right - actualFieldBounds.right)
    }

    @Test
    fun `keeps the result row horizontal inset for an End Rail`() {
        assertResultRowHorizontalInset(railSide = RailSide.END)
    }

    @Test
    fun `keeps the result row horizontal inset for a Start Rail`() {
        assertResultRowHorizontalInset(railSide = RailSide.START)
    }

    @Test
    fun `styles every Search field state with the search bar container and no indicator`() {
        val actualColors: MutableState<TextFieldColors?> = mutableStateOf(null)
        val actualColorScheme: MutableState<ColorScheme?> = mutableStateOf(null)
        composeRule.setContent {
            actualColors.value = searchFieldColors()
            actualColorScheme.value = MaterialTheme.colorScheme
        }

        val inputColorScheme: ColorScheme = requireNotNull(actualColorScheme.value)
        val actualSearchFieldColors: TextFieldColors = requireNotNull(actualColors.value)
        val actualContainerColors: List<Color> = actualSearchFieldColors.containerColors()
        val actualIndicatorColors: List<Color> = actualSearchFieldColors.indicatorColors()

        assertEquals(List(actualContainerColors.size) { inputColorScheme.surfaceContainerHigh }, actualContainerColors)
        assertEquals(List(actualIndicatorColors.size) { Color.Transparent }, actualIndicatorColors)
    }

    @Test
    fun `focuses the query field when Search opens`() {
        setSearchContent(query = mutableStateOf(""))

        queryField().assertIsFocused()
    }

    @Test
    fun `shows the Clear button only while the query is not empty`() {
        val inputQuery: MutableState<String> = mutableStateOf("")
        setSearchContent(query = inputQuery)

        composeRule.onNodeWithContentDescription(context.getString(R.string.clear_search)).assertDoesNotExist()

        composeRule.runOnIdle { inputQuery.value = QUERY }
        composeRule.onNodeWithContentDescription(context.getString(R.string.clear_search)).assertIsDisplayed()
    }

    @Test
    fun `opens the Search options sheet from its button`() {
        setSearchContent(query = mutableStateOf(""))

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_options)).performClick()

        composeRule.onNodeWithText(context.getString(CoreUiR.string.search)).assertExists()
        listOf(
            CoreUiR.string.search_web_suggestions,
            CoreUiR.string.search_contacts,
            CoreUiR.string.search_suggestions,
            CoreUiR.string.search_calculator_row,
            CoreUiR.string.search_swipe_up,
            R.string.search_settings
        ).forEach { label ->
            composeRule.onNodeWithText(context.getString(label)).assertExists()
        }
    }

    @Test
    fun `shows Live web suggestions as unavailable without a suggestion engine`() {
        setSearchContent(
            query = mutableStateOf(""),
            options = inputSearchOptions.copy(searchEngine = SearchEngine.None)
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_options)).performClick()

        composeRule.onNodeWithText(context.getString(CoreUiR.string.search_web_suggestions_unavailable)).assertExists()
        composeRule.onNodeWithText(context.getString(CoreUiR.string.search_web_suggestions)).assertIsNotEnabled()
    }

    @Test
    fun `opens the Search settings screen from the last sheet row`() {
        var actualOpenedSettings: Boolean = false
        setSearchContent(
            query = mutableStateOf(""),
            searchOptionsActions = inputSearchOptionsActions.copy(onOpenSearchSettings = { actualOpenedSettings = true })
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_options)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.search_settings)).performScrollTo().performClick()

        composeRule.runOnIdle { assertTrue(actualOpenedSettings) }
    }

    @Test
    fun `keeps the query and focus after the Search options sheet closes`() {
        setSearchContent(query = mutableStateOf(QUERY))

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_options)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.search_settings)).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(QUERY, queryFieldText())
        queryField().assertIsFocused()
    }

    @Test
    fun `keeps the query and focus after Back closes the Search options sheet`() {
        setSearchContent(query = mutableStateOf(QUERY))

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_options)).performClick()
        composeRule.runOnIdle { requireNotNull(ShadowDialog.getLatestDialog()).onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(QUERY, queryFieldText())
        queryField().assertIsFocused()
    }

    @Test
    fun `keeps the query and focus after a tap outside closes the Search options sheet`() {
        setSearchContent(query = mutableStateOf(QUERY))

        composeRule.onNodeWithContentDescription(context.getString(R.string.search_options)).performClick()
        composeRule.onNodeWithContentDescription(CLOSE_SHEET).performClick()
        composeRule.waitForIdle()

        assertEquals(QUERY, queryFieldText())
        queryField().assertIsFocused()
    }

    private fun TextFieldColors.containerColors(): List<Color> = listOf(
        focusedContainerColor,
        unfocusedContainerColor,
        disabledContainerColor,
        errorContainerColor
    )

    private fun TextFieldColors.indicatorColors(): List<Color> = listOf(
        focusedIndicatorColor,
        unfocusedIndicatorColor,
        disabledIndicatorColor,
        errorIndicatorColor
    )

    private fun assertRowDescription(rowText: String, expectedDescription: String) {
        val actualNode: SemanticsNode = composeRule.onNodeWithText(rowText).fetchSemanticsNode()
        val actualDescriptions: List<String> = actualNode.config[SemanticsProperties.ContentDescription]
        assertEquals(listOf(expectedDescription), actualDescriptions)
    }

    private fun searchFieldControl(@StringRes contentDescriptionResId: Int, useUnmergedTree: Boolean = false): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription(
            context.getString(contentDescriptionResId),
            useUnmergedTree = useUnmergedTree
        )

    private fun assertSearchFieldTouchTarget(control: SemanticsNodeInteraction) {
        control.assertTouchWidthIsEqualTo(minTouchTargetSize)
        control.assertTouchHeightIsEqualTo(minTouchTargetSize)
    }

    private fun assertResultRowHorizontalInset(railSide: RailSide) {
        val inputLaunchable: Launchable = createRailLaunchable("Alpha")
        setSearchContent(
            results = listOf(SearchResult.LaunchableResult(inputLaunchable)),
            itemActions = createLauncherItemActions(railSide = railSide)
        )

        val actualRowBounds: DpRect = composeRule.onAllNodesWithText(inputLaunchable.name).onFirst().getUnclippedBoundsInRoot()
        val actualScreenBounds: DpRect = composeRule.screenBounds()

        assertEquals(homeRailStartInset(railSide = railSide), actualRowBounds.left - actualScreenBounds.left)
        assertEquals(homeEdgePadding, actualScreenBounds.right - actualRowBounds.right)
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
        options: SearchOptions = inputSearchOptions,
        contactsEvents: Flow<SearchContactsUiEvent> = emptyFlow(),
        searchOptionsActions: SearchOptionsActions = inputSearchOptionsActions,
        onQueryChange: (String) -> Unit = { text -> query.value = text },
        onSearchGo: (String) -> SearchResult? = { null }
    ) {
        composeRule.setContent {
            val inputResults: List<SearchResult> = resultsState.value
            LauncherSearch(
                search = LauncherUiState.Search(
                    query = query.value,
                    results = inputResults,
                    topResult = inputResults.firstOrNull(),
                    isShowingSuggestions = false,
                    isBottomAnchored = isBottomAnchored,
                    autoLaunchResult = autoLaunchResult,
                    options = options
                ),
                shouldAutoLaunch = { true },
                itemActions = itemActions,
                contactsEvents = contactsEvents,
                searchOptionsActions = searchOptionsActions,
                onQueryChange = onQueryChange,
                onSearchGo = onSearchGo,
                onCopyCalculatorRow = {},
                onOpenSearchRow = onOpenSearchRow,
                onContactRowAction = onContactRowAction
            )
        }
    }
}
