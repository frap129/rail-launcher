package core.data.icons

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import coil3.DrawableImage
import coil3.Image
import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import core.data.icons.model.Icon
import core.data.profiles.IProfileSource
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_SDK
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TEST_ICON_RES_ID: Int = android.R.drawable.ic_menu_help
private const val PRIVATE_PACKAGE_NAME: String = "com.example.private"
private const val PRIVATE_ACTIVITY_NAME: String = "com.example.private.MainActivity"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IconFetcherTest {
    private val inputOptions: Options = Options(RuntimeEnvironment.getApplication())
    private val inputImageLoader: ImageLoader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()
    private val mockProfileSource: IProfileSource = createProfileSourceMock()

    @Test
    fun `factory creates a fetcher for the icon`() {
        val inputIcon: Icon = Icon.RailIcon(TEST_ICON_RES_ID)
        val actualFetcher: Fetcher = IconFetcher.Factory(profileSource = mockProfileSource)
            .create(inputIcon, inputOptions, inputImageLoader)
        assertTrue(actualFetcher is IconFetcher)
    }

    @Test
    fun `fetch loads a shareable image`(): Unit = runTest {
        val inputIcon: Icon = Icon.RailIcon(TEST_ICON_RES_ID)
        val actualFetcher: Fetcher = IconFetcher.Factory(profileSource = mockProfileSource)
            .create(inputIcon, inputOptions, inputImageLoader)
        val actualResult: FetchResult? = actualFetcher.fetch()
        assertTrue(actualResult is ImageFetchResult)
        val actualImage: Image = (actualResult as ImageFetchResult).image
        assertNotNull(actualImage)
        assertTrue(actualImage.shareable)
    }

    @Test
    fun `fetch loads a Profile activity icon from its own Profile`(): Unit = runTest {
        val inputComponentName: ComponentName = ComponentName(PRIVATE_PACKAGE_NAME, PRIVATE_ACTIVITY_NAME)
        val inputProfile: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
        val expectedIcon: Drawable = ColorDrawable(Color.RED)
        val inputState: ProfileSourceState = ProfileSourceState().apply {
            activityIcon = { componentName, user ->
                expectedIcon.takeIf { componentName == inputComponentName && user == inputProfile }
            }
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputIcon: Icon = Icon.ProfileIcon(icon = Icon.ActivityIcon(inputComponentName), profile = inputProfile)
        val actualFetcher: Fetcher = IconFetcher.Factory(profileSource = mockPrivateProfileSource)
            .create(inputIcon, inputOptions, inputImageLoader)

        val actualResult: FetchResult? = actualFetcher.fetch()

        assertTrue(actualResult is ImageFetchResult)
        val actualImage: Image = (actualResult as ImageFetchResult).image
        assertEquals(expectedIcon, (actualImage as DrawableImage).drawable)
        verify(exactly = 1) { mockPrivateProfileSource.activityIcon(inputComponentName, inputProfile) }
    }

    @Test
    fun `fetch loads a Profile application icon from its own Profile`(): Unit = runTest {
        val inputProfile: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
        val expectedIcon: Drawable = ColorDrawable(Color.BLUE)
        val inputState: ProfileSourceState = ProfileSourceState().apply {
            applicationIcon = { packageName, user ->
                expectedIcon.takeIf { packageName == PRIVATE_PACKAGE_NAME && user == inputProfile }
            }
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputIcon: Icon = Icon.ProfileIcon(icon = Icon.ApplicationIcon(PRIVATE_PACKAGE_NAME), profile = inputProfile)
        val actualFetcher: Fetcher = IconFetcher.Factory(profileSource = mockPrivateProfileSource)
            .create(inputIcon, inputOptions, inputImageLoader)

        val actualResult: FetchResult? = actualFetcher.fetch()

        assertTrue(actualResult is ImageFetchResult)
        val actualImage: Image = (actualResult as ImageFetchResult).image
        assertEquals(expectedIcon, (actualImage as DrawableImage).drawable)
        verify(exactly = 1) { mockPrivateProfileSource.applicationIcon(PRIVATE_PACKAGE_NAME, inputProfile) }
    }

    @Test
    fun `fetch failure does not name the Private Space App`(): Unit = runTest {
        val inputComponentName: ComponentName = ComponentName(PRIVATE_PACKAGE_NAME, PRIVATE_ACTIVITY_NAME)
        val inputProfile: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
        val inputIcon: Icon = Icon.ProfileIcon(icon = Icon.ActivityIcon(inputComponentName), profile = inputProfile)
        val actualFetcher: Fetcher = IconFetcher.Factory(profileSource = mockProfileSource)
            .create(inputIcon, inputOptions, inputImageLoader)

        val actualException: Throwable? = runCatching { actualFetcher.fetch() }.exceptionOrNull()

        assertTrue(actualException is IllegalStateException)
        assertFalse(actualException?.message.orEmpty().contains(PRIVATE_PACKAGE_NAME))
    }
}
