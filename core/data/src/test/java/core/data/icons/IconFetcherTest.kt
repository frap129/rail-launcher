package core.data.icons

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.DisplayMetrics
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.Image
import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Size
import core.data.apps.IAppRepository
import core.data.icons.model.Icon
import core.data.icons.model.UNKNOWN_APP_UPDATE_TIME
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.MISSING_ICON_PACK_PACKAGE
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_APP_UPDATE_TIME
import core.data.testing.TEST_DENSITY_DPI
import core.data.testing.TEST_GALLERY_ICON_SIZE
import core.data.testing.TEST_ICON_CENTER_PX
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_ICON_SIZE_PX
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createAppRepositoryMock
import core.data.testing.createHomeIconCache
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import core.data.testing.homeIconCacheDirectory
import core.data.testing.nightModeToggled
import core.data.testing.storedIconFiles
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val TEST_ICON_RES_ID: Int = android.R.drawable.ic_menu_help
private const val PRIVATE_PACKAGE_NAME: String = "com.example.private"
private const val PRIVATE_ACTIVITY_NAME: String = "com.example.private.MainActivity"
private const val MISSING_DRAWABLE_NAME: String = "missing"
private const val PACK_DRAWABLE_NAME: String = "calendar"
private const val PACK_DRAWABLE_RES_ID: Int = 42
private const val GALLERY_FILE_NAME: String = "gallery-icon.png"
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val SHORTCUT_ID: String = "shortcut-id"
private const val LATER_APP_UPDATE_TIME: Long = TEST_APP_UPDATE_TIME + 1L
private val TRANSLUCENT_ICON_COLOR: Int = Color.argb(77, 201, 113, 37)

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IconFetcherTest {
    private val inputContext: Application = RuntimeEnvironment.getApplication()
    private val inputOptions: Options = Options(inputContext)
    private val inputSizedOptions: Options = Options(context = inputContext, size = Size(TEST_ICON_SIZE_PX, TEST_ICON_SIZE_PX))
    private val inputImageLoader: ImageLoader = ImageLoader.Builder(inputContext).build()
    private val mockProfileSource: IProfileSource = createProfileSourceMock()
    private val mockAppRepository: IAppRepository = createAppRepositoryMock(inputHomeApps = MutableStateFlow(null))

    @Test
    fun `factory creates a fetcher for the icon`(): Unit = runTest {
        val inputIcon: Icon = Icon.RailIcon(TEST_ICON_RES_ID)
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon)
        assertTrue(actualFetcher is IconFetcher)
    }

    @Test
    fun `fetch loads a shareable image`(): Unit = runTest {
        val inputIcon: Icon = Icon.RailIcon(TEST_ICON_RES_ID)
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon)
        val actualResult: FetchResult? = actualFetcher.fetch()
        assertTrue(actualResult is ImageFetchResult)
        val actualImage: Image = (actualResult as ImageFetchResult).image
        assertNotNull(actualImage)
        assertTrue(actualImage.shareable)
    }

    @Test
    fun `fetch loads a pack drawable by name`(): Unit = runTest {
        val expectedDrawable: Drawable = ColorDrawable(Color.GREEN)
        val mockContext: Context = mockk()
        val mockPackageManager: PackageManager = mockk()
        val mockResources: Resources = mockk()
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getResourcesForApplication(TEST_ICON_PACK_PACKAGE) } returns mockResources
        every { mockResources.getIdentifier(PACK_DRAWABLE_NAME, "drawable", TEST_ICON_PACK_PACKAGE) } returns PACK_DRAWABLE_RES_ID
        every { mockResources.getDrawable(PACK_DRAWABLE_RES_ID, null) } returns expectedDrawable
        val inputIcon: Icon = Icon.PackDrawableIcon(packPackageName = TEST_ICON_PACK_PACKAGE, drawableName = PACK_DRAWABLE_NAME)
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon, inputOptions = Options(mockContext))
        val actualResult: FetchResult? = actualFetcher.fetch()
        assertTrue(actualResult is ImageFetchResult)
        val actualImage: Image = (actualResult as ImageFetchResult).image
        assertEquals(expectedDrawable, (actualImage as DrawableImage).drawable)
    }

    @Test
    fun `fetch fails when the pack drawable is gone`(): Unit = runTest {
        val mockContext: Context = mockk()
        val mockPackageManager: PackageManager = mockk()
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getResourcesForApplication(MISSING_ICON_PACK_PACKAGE) } throws
            PackageManager.NameNotFoundException(MISSING_ICON_PACK_PACKAGE)
        val inputIcon: Icon = Icon.PackDrawableIcon(packPackageName = MISSING_ICON_PACK_PACKAGE, drawableName = MISSING_DRAWABLE_NAME)
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon, inputOptions = Options(mockContext))
        val actualException: Throwable? = runCatching { actualFetcher.fetch() }.exceptionOrNull()
        assertTrue(actualException is IllegalStateException)
        assertFalse(actualException?.message.orEmpty().contains(MISSING_ICON_PACK_PACKAGE))
    }

    @Test
    fun `fetch loads an image icon clipped to the system mask`(): Unit = runTest {
        val inputStore: IIconImageStore = IIconImageStoreImpl(context = inputContext)
        inputStore.write(
            fileName = GALLERY_FILE_NAME,
            bitmap = Bitmap.createBitmap(TEST_GALLERY_ICON_SIZE, TEST_GALLERY_ICON_SIZE, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
        )
        val inputIcon: Icon = Icon.ImageIcon(fileName = GALLERY_FILE_NAME)
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon)
        val actualResult: FetchResult? = actualFetcher.fetch()
        assertTrue(actualResult is ImageFetchResult)
        val actualDrawable: Drawable = ((actualResult as ImageFetchResult).image as DrawableImage).drawable
        actualDrawable.setBounds(0, 0, TEST_GALLERY_ICON_SIZE, TEST_GALLERY_ICON_SIZE)
        val actualBitmap: Bitmap = Bitmap.createBitmap(TEST_GALLERY_ICON_SIZE, TEST_GALLERY_ICON_SIZE, Bitmap.Config.ARGB_8888)
        actualDrawable.draw(Canvas(actualBitmap))
        assertEquals(Color.RED, actualBitmap.getPixel(TEST_GALLERY_ICON_SIZE / 2, TEST_GALLERY_ICON_SIZE / 2))
    }

    @Test
    fun `fetch fails when the image file is gone`(): Unit = runTest {
        val inputIcon: Icon = Icon.ImageIcon(fileName = "missing.png")
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon)
        val actualException: Throwable? = runCatching { actualFetcher.fetch() }.exceptionOrNull()
        assertTrue(actualException.toString(), actualException is IllegalStateException)
        assertFalse(actualException?.message.orEmpty().contains("missing.png"))
    }

    @Test
    fun `fetch loads a Profile activity icon from its own Profile`(): Unit = runTest {
        val inputComponentName: ComponentName = ComponentName(PRIVATE_PACKAGE_NAME, PRIVATE_ACTIVITY_NAME)
        val inputProfile: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
        val expectedIcon: Drawable = ColorDrawable(Color.RED)
        val inputState: ProfileSourceState = ProfileSourceState().apply {
            activityIcon = { componentName, user -> expectedIcon.takeIf { componentName == inputComponentName && user == inputProfile } }
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputIcon: Icon = Icon.ProfileIcon(
            icon = Icon.ActivityIcon(inputComponentName, UNKNOWN_APP_UPDATE_TIME),
            profile = inputProfile
        )
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon, mockProfileSource = mockPrivateProfileSource)
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
            applicationIcon = { packageName, user -> expectedIcon.takeIf { packageName == PRIVATE_PACKAGE_NAME && user == inputProfile } }
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputIcon: Icon = Icon.ProfileIcon(icon = Icon.ApplicationIcon(PRIVATE_PACKAGE_NAME), profile = inputProfile)
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon, mockProfileSource = mockPrivateProfileSource)
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
        val inputIcon: Icon = Icon.ProfileIcon(
            icon = Icon.ActivityIcon(inputComponentName, UNKNOWN_APP_UPDATE_TIME),
            profile = inputProfile
        )
        val actualFetcher: Fetcher = createFetcher(inputIcon = inputIcon)
        val actualException: Throwable? = runCatching { actualFetcher.fetch() }.exceptionOrNull()
        assertTrue(actualException is IllegalStateException)
        assertFalse(actualException?.message.orEmpty().contains(PRIVATE_PACKAGE_NAME))
    }

    @Test
    fun `serves a stored App icon without loading the App's resources`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputIcon: Icon = Icon.ProfileIcon(
            icon = Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME),
            profile = inputProfile.user
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )

        val actualStoredBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        inputState.activityIcon = { _, _ -> ColorDrawable(Color.BLUE) }
        val actualCachedBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        assertEquals(Color.RED, actualStoredBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertEquals(Color.RED, actualCachedBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertEquals(TEST_ICON_SIZE_PX, actualCachedBitmap.width)
        verify(exactly = 1) { mockAppProfileSource.activityIcon(inputComponentName, inputProfile.user) }
    }

    @Test
    fun `loads a new App icon after the App updates`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        val inputUpdatedIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, LATER_APP_UPDATE_TIME), inputProfile.user)
        inputState.activityIcon = { _, _ -> ColorDrawable(Color.BLUE) }
        val actualUpdatedBitmap: Bitmap = fetchBitmap(createFetcher(inputUpdatedIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        assertEquals(Color.BLUE, actualUpdatedBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        verify(exactly = 2) { mockAppProfileSource.activityIcon(inputComponentName, inputProfile.user) }
    }

    @Test
    fun `replaces a stored App icon when its system icon changed`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        val inputKeyer: IconKeyer = IconKeyer(homeIconCache = inputCache)
        val inputKey: String = inputKeyer.key(inputIcon, inputSizedOptions)
        inputState.activityIcon = { _, _ -> ColorDrawable(Color.BLUE) }
        val actualCachedBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        val actualRevalidatedBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        assertEquals(Color.RED, actualCachedBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertEquals(Color.BLUE, actualRevalidatedBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertNotEquals(inputKey, inputKeyer.key(inputIcon, inputSizedOptions))
        verify(exactly = 2) { mockAppProfileSource.activityIcon(inputComponentName, inputProfile.user) }
    }

    @Test
    fun `keeps a stored App icon when its system icon is unchanged`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        val inputKeyer: IconKeyer = IconKeyer(homeIconCache = inputCache)
        val inputKey: String = inputKeyer.key(inputIcon, inputSizedOptions)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        assertEquals(inputKey, inputKeyer.key(inputIcon, inputSizedOptions))
        verify(exactly = 2) { mockAppProfileSource.activityIcon(inputComponentName, inputProfile.user) }
    }

    @Test
    fun `keeps a stored App icon whose unchanged system icon is translucent`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(TRANSLUCENT_ICON_COLOR) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        val inputKeyer: IconKeyer = IconKeyer(homeIconCache = inputCache)
        val inputKey: String = inputKeyer.key(inputIcon, inputSizedOptions)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        assertEquals(inputKey, inputKeyer.key(inputIcon, inputSizedOptions))
    }

    @Test
    fun `revalidates an App icon again after a configuration change that can alter it`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        inputContext.onConfigurationChanged(nightModeToggled(configuration = inputContext.resources.configuration))
        inputState.activityIcon = { _, _ -> ColorDrawable(Color.BLUE) }
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        val actualRevalidatedBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        assertEquals(Color.BLUE, actualRevalidatedBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        verify(exactly = 3) { mockAppProfileSource.activityIcon(inputComponentName, inputProfile.user) }
    }

    @Test
    fun `fits a non-square icon into its square slot, centred`() {
        assertEquals(Rect(0, 25, 100, 75), fittedBounds(intrinsicWidth = 200, intrinsicHeight = 100, sizePx = 100))
        assertEquals(Rect(25, 0, 75, 100), fittedBounds(intrinsicWidth = 50, intrinsicHeight = 100, sizePx = 100))
    }

    @Test
    fun `fills the slot with a square icon or one without an intrinsic size`() {
        assertEquals(Rect(0, 0, 100, 100), fittedBounds(intrinsicWidth = 48, intrinsicHeight = 48, sizePx = 100))
        assertEquals(Rect(0, 0, 100, 100), fittedBounds(intrinsicWidth = -1, intrinsicHeight = -1, sizePx = 100))
    }

    @Test
    fun `revalidates an App icon only once between configuration changes`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        verify(exactly = 2) { mockAppProfileSource.activityIcon(inputComponentName, inputProfile.user) }
    }

    @Test
    fun `does not store a Private Space App icon`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockPrivateProfileSource,
            appRepository = mockAppRepository
        )

        val actualBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputSizedOptions, mockPrivateProfileSource, inputCache))
        runCurrent()
        assertEquals(Color.RED, actualBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertTrue(storedIconFiles(context = inputContext, packageName = inputComponentName.packageName).isEmpty())
    }

    @Test
    fun `stores a Work App's icon with its Profile badge`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, TEST_APP_UPDATE_TIME), inputProfile.user)
        val mockContext: Context = mockk()
        val mockPackageManager: PackageManager = mockk()
        val mockResources: Resources = mockk()
        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.resources } returns mockResources
        every { mockResources.displayMetrics } returns DisplayMetrics().apply { densityDpi = TEST_DENSITY_DPI }
        every { mockPackageManager.getUserBadgedIcon(any(), any()) } returns ColorDrawable(Color.GREEN)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        val inputOptions: Options = Options(context = mockContext, size = Size(TEST_ICON_SIZE_PX, TEST_ICON_SIZE_PX))
        val actualStoredBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputOptions, mockAppProfileSource, inputCache))
        runCurrent()
        val actualCachedBitmap: Bitmap = fetchBitmap(createFetcher(inputIcon, inputOptions, mockAppProfileSource, inputCache))
        assertEquals(Color.GREEN, actualStoredBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertEquals(Color.GREEN, actualCachedBitmap.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertTrue(actualStoredBitmap.sameAs(actualCachedBitmap))
        verify(exactly = 1) { mockPackageManager.getUserBadgedIcon(any(), any()) }
    }

    @Test
    fun `does not store an Icon pack drawable icon`(): Unit = runTest {
        val expectedDrawable: Drawable = ColorDrawable(Color.GREEN)
        val mockContext: Context = mockk()
        val mockPackageManager: PackageManager = mockk()
        val mockResources: Resources = mockk()
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getResourcesForApplication(TEST_ICON_PACK_PACKAGE) } returns mockResources
        every { mockResources.getIdentifier(PACK_DRAWABLE_NAME, "drawable", TEST_ICON_PACK_PACKAGE) } returns PACK_DRAWABLE_RES_ID
        every { mockResources.getDrawable(PACK_DRAWABLE_RES_ID, null) } returns expectedDrawable
        val inputIcon: Icon = Icon.PackDrawableIcon(packPackageName = TEST_ICON_PACK_PACKAGE, drawableName = PACK_DRAWABLE_NAME)
        fetchDrawable(createFetcher(inputIcon, inputOptions = Options(mockContext)))
        runCurrent()
        assertTrue(homeIconCacheDirectory(context = inputContext).listFiles().isNullOrEmpty())
    }

    @Test
    fun `does not store an Image icon`(): Unit = runTest {
        val inputStore: IIconImageStore = IIconImageStoreImpl(context = inputContext)
        inputStore.write(
            fileName = GALLERY_FILE_NAME,
            bitmap = Bitmap.createBitmap(TEST_GALLERY_ICON_SIZE, TEST_GALLERY_ICON_SIZE, Bitmap.Config.ARGB_8888)
        )
        val inputIcon: Icon = Icon.ImageIcon(fileName = GALLERY_FILE_NAME)
        fetchDrawable(createFetcher(inputIcon, inputOptions = inputSizedOptions))
        runCurrent()
        assertTrue(homeIconCacheDirectory(context = inputContext).listFiles().isNullOrEmpty())
    }

    @Test
    fun `does not store a Shortcut icon`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputShortcut: ShortcutInfo = mockk()
        val expectedDrawable: Drawable = ColorDrawable(Color.GREEN)
        every { inputShortcut.id } returns SHORTCUT_ID
        val mockLauncherApps: LauncherApps = mockk()
        every { mockLauncherApps.getShortcuts(any(), inputProfile.user) } returns listOf(inputShortcut)
        every { mockLauncherApps.getShortcutIconDrawable(inputShortcut, any()) } returns expectedDrawable
        shadowOf(inputContext).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val inputIcon: Icon = Icon.ShortcutIcon(packageName = APP_PACKAGE_NAME, shortcutId = SHORTCUT_ID, profile = inputProfile.user)
        val actualDrawable: Drawable = fetchDrawable(createFetcher(inputIcon, inputOptions = inputSizedOptions))
        runCurrent()
        assertEquals(expectedDrawable, actualDrawable)
        assertTrue(homeIconCacheDirectory(context = inputContext).listFiles().isNullOrEmpty())
    }

    @Test
    fun `does not store an App icon whose update time is unknown`(): Unit = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val inputIcon: Icon = Icon.ProfileIcon(Icon.ActivityIcon(inputComponentName, UNKNOWN_APP_UPDATE_TIME), inputProfile.user)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            activityIcon = { _, _ -> ColorDrawable(Color.RED) }
        }
        val mockAppProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockAppProfileSource,
            appRepository = mockAppRepository
        )
        fetchDrawable(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        runCurrent()
        inputState.activityIcon = { _, _ -> ColorDrawable(Color.BLUE) }
        val actualDrawable: Drawable = fetchDrawable(createFetcher(inputIcon, inputSizedOptions, mockAppProfileSource, inputCache))
        assertEquals(Color.BLUE, (actualDrawable as ColorDrawable).color)
        assertTrue(storedIconFiles(context = inputContext, packageName = inputComponentName.packageName).isEmpty())
        verify(exactly = 2) { mockAppProfileSource.activityIcon(inputComponentName, inputProfile.user) }
    }

    private fun TestScope.createFetcher(
        inputIcon: Icon,
        inputOptions: Options = this@IconFetcherTest.inputOptions,
        mockProfileSource: IProfileSource = this@IconFetcherTest.mockProfileSource,
        inputHomeIconCache: IHomeIconCache = createHomeIconCache(
            context = inputContext,
            profileSource = mockProfileSource,
            appRepository = mockAppRepository
        )
    ): Fetcher = IconFetcher.Factory(
        profileSource = mockProfileSource,
        homeIconCache = inputHomeIconCache,
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler)
    ).create(inputIcon, inputOptions, inputImageLoader)

    private suspend fun fetchBitmap(inputFetcher: Fetcher): Bitmap {
        val actualImage: Image = fetchImage(inputFetcher = inputFetcher)
        assertTrue(actualImage is BitmapImage)
        return (actualImage as BitmapImage).bitmap
    }

    private suspend fun fetchDrawable(inputFetcher: Fetcher): Drawable {
        val actualImage: Image = fetchImage(inputFetcher = inputFetcher)
        assertTrue(actualImage is DrawableImage)
        return (actualImage as DrawableImage).drawable
    }

    private suspend fun fetchImage(inputFetcher: Fetcher): Image {
        val actualResult: FetchResult? = inputFetcher.fetch()
        assertTrue(actualResult is ImageFetchResult)
        return (actualResult as ImageFetchResult).image
    }
}
