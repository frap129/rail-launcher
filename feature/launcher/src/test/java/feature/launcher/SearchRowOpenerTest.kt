package feature.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import core.data.search.SearchResult
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowPackageManager

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
class SearchRowOpenerTest {
    private val context: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val shadowContext: ShadowApplication = shadowOf(RuntimeEnvironment.getApplication())
    private val shadowActivity: ShadowActivity = shadowOf(context)

    @Before
    fun handleOnlyRegisteredActivities() {
        shadowContext.checkActivities(true)
        shadowContext.clearNextStartedActivities()
    }

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
    fun `opens the Play Store market search first`() {
        registerMarketHandler()

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

    private fun registerBrowserHandler() {
        val manager: ShadowPackageManager = shadowOf(context.packageManager)
        val component: ComponentName = ComponentName(BROWSER_PACKAGE, BROWSER_ACTIVITY)
        manager.addActivityIfNotPresent(component)
        manager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme(WEB_SCHEME)
            }
        )
    }

    private fun registerMarketHandler() {
        val manager: ShadowPackageManager = shadowOf(context.packageManager)
        val component: ComponentName = ComponentName(STORE_PACKAGE, STORE_ACTIVITY)
        manager.addActivityIfNotPresent(component)
        manager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme(MARKET_SCHEME)
            }
        )
    }

    private fun playStoreRow(): SearchResult.PlayStoreRow = SearchResult.PlayStoreRow(marketUrl = MARKET_URL, webUrl = WEB_FALLBACK_URL)
}
