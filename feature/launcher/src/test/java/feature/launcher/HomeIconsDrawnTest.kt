package feature.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import core.data.testing.TEST_SDK
import core.ui.HomeIconsDrawnReport
import core.ui.IHomeIconsDrawn
import core.ui.model.data.isFavoriteKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private val HOME_WIDTH: Dp = 360.dp
private val HOME_HEIGHT: Dp = 300.dp
private const val FAVORITE_COUNT: Int = 6
private const val REPORT_TIMEOUT_MILLIS: Long = 5_000L
private const val SETTLE_MILLIS: Long = 500L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class HomeIconsDrawnTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val mockHomeIconsDrawn: IHomeIconsDrawn = mockk(relaxed = true)
    private var isReported: Boolean = false

    @Before
    fun recordReports() {
        every { mockHomeIconsDrawn.report() } answers { isReported = true }
    }

    @Before
    fun useImageLoader() {
        // Loading on the composition's thread keeps the settled write off Coil's IO threads, whose
        // snapshot changes reach Compose only through a main-looper post that waitUntil never runs.
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).coroutineContext(EmptyCoroutineContext).build()
        )
    }

    @After
    fun releaseImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `reports at once for a Home with no Favorites`() {
        // No frame may pass before the report, so the empty Home must not wait to lay out icons.
        composeRule.mainClock.autoAdvance = false
        setHome(home = createHomeState(favorites = emptyList()))
        verify(exactly = 1) { mockHomeIconsDrawn.report() }
    }

    @Test
    fun `reports when a Favorite icon fails to load`() {
        // The test ImageLoader has no fetcher for an Icon, so this Favorite's load ends in Error.
        setHome(home = createHomeState(favorites = listOf(createFavorite(index = 1))))
        composeRule.waitUntil(timeoutMillis = REPORT_TIMEOUT_MILLIS) { isReported }
        composeRule.mainClock.advanceTimeBy(milliseconds = SETTLE_MILLIS)
        verify(exactly = 1) { mockHomeIconsDrawn.report() }
    }

    @Test
    fun `waits only for the visible Favorite icons`() {
        val inputHome: LauncherUiState.Home = createHomeState(favorites = (1..FAVORITE_COUNT).map(::createFavorite))
        setHome(home = inputHome)
        composeRule.waitUntil(timeoutMillis = REPORT_TIMEOUT_MILLIS) { isReported }
        val actualVisibleFavoriteCount: Int = inputHome.lazyListState.layoutInfo.visibleItemsInfo.count { info -> isFavoriteKey(info.key) }
        assertTrue(
            "Home must show only some Favorites, not $actualVisibleFavoriteCount",
            actualVisibleFavoriteCount in 1 until FAVORITE_COUNT
        )
        verify(exactly = 1) { mockHomeIconsDrawn.report() }
    }

    private fun setHome(home: LauncherUiState.Home) {
        composeRule.setContent {
            val report: HomeIconsDrawnReport = remember { HomeIconsDrawnReport(homeIconsDrawn = mockHomeIconsDrawn) }
            val itemActions: LauncherItemActions = remember { createLauncherItemActions() }
            Box(modifier = Modifier.size(width = HOME_WIDTH, height = HOME_HEIGHT)) {
                LauncherHomeTestContent(
                    home = home,
                    itemActions = itemActions,
                    isPopupSwipeEnabled = true,
                    onFavoriteIconSettled = report.settledIcons::settle
                )
                HomeIconsDrawnEffect(home = home, isHomeDrawable = true, report = report)
            }
        }
    }
}
