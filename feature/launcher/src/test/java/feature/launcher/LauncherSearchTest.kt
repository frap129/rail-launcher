package feature.launcher

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.prefs.RailSide
import core.data.search.SearchResult
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
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

    private fun setSearchContent(results: List<SearchResult>, onOpenSearchRow: (SearchResult.ExternalRow) -> Unit = {}) {
        composeRule.setContent {
            LauncherSearch(
                modifier = Modifier,
                search = LauncherUiState.Search(
                    query = QUERY,
                    results = results,
                    topResult = results.firstOrNull(),
                    isShowingSuggestions = false
                ),
                itemActions = itemActions,
                onQueryChange = {},
                onSearchGo = { null },
                onCopyCalculatorRow = {},
                onOpenSearchRow = onOpenSearchRow
            )
        }
    }
}
