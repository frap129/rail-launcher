package core.ui.composables

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import coil3.BitmapImage
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import core.data.apps.model.App
import core.data.icons.IHomeIconCache
import core.data.icons.IconKeyer
import core.data.icons.identity
import core.data.icons.model.Icon
import core.data.testing.REPORT_TIMEOUT_MILLIS
import core.data.testing.TEST_APP_UPDATE_TIME
import core.data.testing.TEST_ICON_CENTER_PX
import core.data.testing.TEST_ICON_SIZE_PX
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val FIRST_RELOAD_COUNT: Int = 2

/** A Coil fetcher serving each icon's identity from [colors], standing in for the disk cache a reload reads. */
private class ColorIconFetcher(private val icon: Icon, private val colors: Map<String, Int>) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap: Bitmap = Bitmap.createBitmap(TEST_ICON_SIZE_PX, TEST_ICON_SIZE_PX, Bitmap.Config.ARGB_8888).apply {
            eraseColor(colors.getValue(icon.identity))
        }
        return ImageFetchResult(image = bitmap.asImage(shareable = true), isSampled = false, dataSource = DataSource.DISK)
    }

    class Factory(private val colors: () -> Map<String, Int>) : Fetcher.Factory<Icon> {
        override fun create(data: Icon, options: Options, imageLoader: ImageLoader): Fetcher =
            ColorIconFetcher(icon = data, colors = colors())
    }
}

/** Records the colour of every icon Coil drew, by icon identity. */
private class IconSuccessRecorder : EventListener() {
    private val colorsByIdentity: ConcurrentHashMap<String, CopyOnWriteArrayList<Int>> = ConcurrentHashMap()

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        val icon: Icon = request.data as? Icon ?: return
        val bitmap: Bitmap = (result.image as? BitmapImage)?.bitmap ?: return
        colorsByIdentity.computeIfAbsent(icon.identity) { CopyOnWriteArrayList() }
            .add(bitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
    }

    fun colors(icon: Icon): List<Int> = colorsByIdentity[icon.identity]?.toList().orEmpty()
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class LaunchableIconRevalidationTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val inputContext: Application = RuntimeEnvironment.getApplication()
    private val inputColors: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
    private val inputSuccesses: IconSuccessRecorder = IconSuccessRecorder()
    private val inputGenerations: MutableStateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
    private val mockHomeIconCache: IHomeIconCache = mockk {
        every { generations } returns inputGenerations
        every { generation(any()) } answers { inputGenerations.value[firstArg<String>()] ?: 0L }
        every { configurationGeneration } returns 0L
    }
    private val inputImageLoader: ImageLoader = ImageLoader.Builder(inputContext)
        .components {
            add(IconKeyer(homeIconCache = mockHomeIconCache))
            add(ColorIconFetcher.Factory(colors = { inputColors }))
        }
        .eventListener(inputSuccesses)
        .build()

    @Before
    fun useImageLoader() {
        SingletonImageLoader.setUnsafe(inputImageLoader)
    }

    @After
    fun releaseImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `reloads a drawn icon when its stored icon is replaced`() {
        val inputApp: App = createApp(name = "First", packageName = "com.example.first", lastUpdateTime = TEST_APP_UPDATE_TIME)
        val inputOtherApp: App = createApp(name = "Second", packageName = "com.example.second", lastUpdateTime = TEST_APP_UPDATE_TIME)
        inputColors[inputApp.icon.identity] = Color.RED
        inputColors[inputOtherApp.icon.identity] = Color.GREEN
        composeRule.setContent {
            CompositionLocalProvider(LocalHomeIconCache provides mockHomeIconCache) {
                Column {
                    LaunchableIcon(launchable = inputApp)
                    LaunchableIcon(launchable = inputOtherApp)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = REPORT_TIMEOUT_MILLIS) {
            inputSuccesses.colors(inputApp.icon).size == 1 && inputSuccesses.colors(inputOtherApp.icon).size == 1
        }
        inputColors[inputApp.icon.identity] = Color.BLUE
        composeRule.runOnIdle {
            inputGenerations.value = mapOf(inputApp.icon.identity to 1L)
        }

        composeRule.waitUntil(timeoutMillis = REPORT_TIMEOUT_MILLIS) {
            inputSuccesses.colors(inputApp.icon).size == FIRST_RELOAD_COUNT
        }
        assertEquals(listOf(Color.RED, Color.BLUE), inputSuccesses.colors(inputApp.icon))
        assertEquals(listOf(Color.GREEN), inputSuccesses.colors(inputOtherApp.icon))
    }
}
