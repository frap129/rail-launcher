package core.data.icons

import coil3.Image
import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import core.data.icons.model.Icon
import core.data.testing.TEST_SDK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TEST_ICON_RES_ID: Int = android.R.drawable.ic_menu_help

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IconFetcherTest {
    private val inputOptions: Options = Options(RuntimeEnvironment.getApplication())
    private val inputImageLoader: ImageLoader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

    @Test
    fun `factory creates a fetcher for the icon`() {
        val inputIcon: Icon = Icon.RailIcon(TEST_ICON_RES_ID)
        val actualFetcher: Fetcher = IconFetcher.Factory().create(inputIcon, inputOptions, inputImageLoader)
        assertTrue(actualFetcher is IconFetcher)
    }

    @Test
    fun `fetch loads a shareable image`(): Unit = runTest {
        val inputIcon: Icon = Icon.RailIcon(TEST_ICON_RES_ID)
        val actualFetcher: Fetcher = IconFetcher.Factory().create(inputIcon, inputOptions, inputImageLoader)
        val actualResult: FetchResult? = actualFetcher.fetch()
        assertTrue(actualResult is ImageFetchResult)
        val actualImage: Image = (actualResult as ImageFetchResult).image
        assertNotNull(actualImage)
        assertTrue(actualImage.shareable)
    }
}
