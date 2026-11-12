package feature.launcher

import android.content.Intent
import android.net.Uri
import core.data.search.SearchResult
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val WEB_URL: String = "https://www.google.com/search?q=rail"
private const val WEB_FALLBACK_URL: String = "https://play.google.com/store/search?q=rail&c=apps"
private const val MARKET_URL: String = "market://search?q=rail"
private const val BROWSER_PACKAGE: String = "com.example.browser"
private const val BROWSER_ACTIVITY: String = "com.example.browser.BrowserActivity"
private const val STORE_PACKAGE: String = "com.example.store"
private const val STORE_ACTIVITY: String = "com.example.store.StoreActivity"
private const val WEB_SCHEME: String = "https"
private const val MARKET_SCHEME: String = "market"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class SearchRowOpenerTest : IntentLaunchTestBase() {
    @Test
    fun `opens a web row in the default browser with the Browsable category`() {
        registerBrowserHandler()
        val actualOpened: Boolean = openSearchResultRow(context = context, row = SearchResult.WebSearchRow(url = WEB_URL))

        val actualIntent: Intent? = shadowActivity.nextStartedActivity
        assertTrue(actualOpened)
        assertEquals(Intent.ACTION_VIEW, actualIntent?.action)
        assertEquals(Uri.parse(WEB_URL), actualIntent?.data)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), actualIntent?.categories)
        assertNull(actualIntent?.`package`)
    }

    @Test
    fun `opens a Web suggestion row in the default browser with the Browsable category`() {
        registerBrowserHandler()

        val actualOpened: Boolean = openSearchResultRow(
            context = context,
            row = SearchResult.WebSuggestionRow(suggestion = "rail", url = WEB_URL)
        )

        val actualIntent: Intent? = shadowActivity.nextStartedActivity
        assertTrue(actualOpened)
        assertEquals(Intent.ACTION_VIEW, actualIntent?.action)
        assertEquals(Uri.parse(WEB_URL), actualIntent?.data)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), actualIntent?.categories)
        assertNull(actualIntent?.`package`)
    }

    @Test
    fun `opens the Play Store market search first`() {
        registerIntentHandler(
            packageName = STORE_PACKAGE,
            className = STORE_ACTIVITY,
            action = Intent.ACTION_VIEW,
            dataScheme = MARKET_SCHEME
        )

        val actualOpened: Boolean = openSearchResultRow(context = context, row = playStoreRow())

        val actualIntent: Intent? = shadowActivity.nextStartedActivity
        assertTrue(actualOpened)
        assertEquals(Intent.ACTION_VIEW, actualIntent?.action)
        assertEquals(Uri.parse(MARKET_URL), actualIntent?.data)
        assertFalse(actualIntent?.hasCategory(Intent.CATEGORY_BROWSABLE) == true)
    }

    @Test
    fun `falls back to the Play Store web address when the market search cannot open`() {
        registerBrowserHandler()

        val actualOpened: Boolean = openSearchResultRow(context = context, row = playStoreRow())

        val actualIntent: Intent? = shadowActivity.nextStartedActivity
        assertTrue(actualOpened)
        assertEquals(Uri.parse(WEB_FALLBACK_URL), actualIntent?.data)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), actualIntent?.categories)
    }

    @Test
    fun `reports a row that no app can open`() {
        val actualOpened: Boolean = openSearchResultRow(context = context, row = playStoreRow())

        assertFalse(actualOpened)
        assertNull(shadowActivity.nextStartedActivity)
    }

    private fun registerBrowserHandler() = registerIntentHandler(
        packageName = BROWSER_PACKAGE,
        className = BROWSER_ACTIVITY,
        action = Intent.ACTION_VIEW,
        dataScheme = WEB_SCHEME,
        categories = listOf(Intent.CATEGORY_BROWSABLE)
    )

    private fun playStoreRow(): SearchResult.PlayStoreRow = SearchResult.PlayStoreRow(marketUrl = MARKET_URL, webUrl = WEB_FALLBACK_URL)
}
