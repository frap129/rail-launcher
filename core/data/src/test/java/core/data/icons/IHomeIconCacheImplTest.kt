package core.data.icons

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.os.UserHandle
import core.data.apps.HomeApps
import core.data.apps.IAppRepository
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileActivity
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.MISSING_WORK_USER_ID
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_APP_UPDATE_TIME
import core.data.testing.TEST_DENSITY_DPI
import core.data.testing.TEST_ICON_CENTER_PX
import core.data.testing.TEST_ICON_SIZE_PX
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createAppRepositoryMock
import core.data.testing.createHomeIconCache
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import core.data.testing.nightModeToggled
import core.data.testing.orientationToggled
import core.data.testing.storedIconFiles
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val FIRST_PACKAGE_NAME: String = "com.example.first"
private const val SECOND_PACKAGE_NAME: String = "com.example.second"
private const val FIRST_COMPONENT_NAME: String = "com.example.first.FirstActivity"
private const val SECOND_COMPONENT_NAME: String = "com.example.first.SecondActivity"
private const val SECOND_PACKAGE_COMPONENT_NAME: String = "com.example.second.SecondActivity"
private const val EARLIER_HOME_ICONS_DIRECTORY: String = "home-icons-v0"
private const val CONCURRENT_WRITE_COUNT: Int = 8

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IHomeIconCacheImplTest {
    private val inputContext = RuntimeEnvironment.getApplication()
    private val inputPersonalProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
    private val inputWorkProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
    private val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPersonalProfile, inputWorkProfile))
    private val mockProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
    private val inputHomeApps: MutableStateFlow<HomeApps?> = MutableStateFlow(null)
    private val mockAppRepository: IAppRepository = createAppRepositoryMock(inputHomeApps = inputHomeApps)

    @Test
    fun `reads back a stored icon`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        assertEquals(Color.RED, inputCache.read(key = inputKey)?.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
    }

    @Test
    fun `reads nothing for an icon that was never stored`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        assertNull(inputCache.read(key = createKey()))
    }

    @Test
    fun `stores one version of an icon, replacing its earlier versions`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputEarlierKey: HomeIconCacheKey = createKey(lastUpdateTime = TEST_APP_UPDATE_TIME)
        val inputLaterKey: HomeIconCacheKey = createKey(lastUpdateTime = TEST_APP_UPDATE_TIME + 1L)
        inputCache.write(key = inputEarlierKey, bitmap = createBitmap(color = Color.RED))
        inputCache.write(key = inputLaterKey, bitmap = createBitmap(color = Color.BLUE))
        assertNull(inputCache.read(key = inputEarlierKey))
        assertEquals(Color.BLUE, inputCache.read(key = inputLaterKey)?.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
    }

    @Test
    fun `keeps the icons of each Profile apart`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputPersonalKey: HomeIconCacheKey = createKey(profile = inputPersonalProfile.user)
        val inputWorkKey: HomeIconCacheKey = createKey(profile = inputWorkProfile.user)
        inputCache.write(key = inputPersonalKey, bitmap = createBitmap(color = Color.RED))
        inputCache.write(key = inputWorkKey, bitmap = createBitmap(color = Color.BLUE))
        assertEquals(Color.RED, inputCache.read(key = inputPersonalKey)?.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
        assertEquals(Color.BLUE, inputCache.read(key = inputWorkKey)?.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
    }

    @Test
    fun `evicts every stored icon of a package`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        inputCache.write(key = createKey(), bitmap = createBitmap(color = Color.RED))
        inputCache.write(
            key = createKey(componentName = ComponentName(FIRST_PACKAGE_NAME, SECOND_COMPONENT_NAME)),
            bitmap = createBitmap(color = Color.RED)
        )
        inputCache.write(
            key = createKey(componentName = ComponentName(SECOND_PACKAGE_NAME, SECOND_PACKAGE_COMPONENT_NAME)),
            bitmap = createBitmap(color = Color.BLUE)
        )
        inputCache.evict(packageName = FIRST_PACKAGE_NAME)
        assertNull(inputCache.read(key = createKey()))
        assertNull(inputCache.read(key = createKey(componentName = ComponentName(FIRST_PACKAGE_NAME, SECOND_COMPONENT_NAME))))
        assertNotNull(inputCache.read(key = createKey(componentName = ComponentName(SECOND_PACKAGE_NAME, SECOND_PACKAGE_COMPONENT_NAME))))
    }

    @Test
    fun `evicts a package's icons when the system reports the package changed`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        inputCache.write(key = createKey(), bitmap = createBitmap(color = Color.RED))
        inputCache.write(
            key = createKey(componentName = ComponentName(SECOND_PACKAGE_NAME, SECOND_PACKAGE_COMPONENT_NAME)),
            bitmap = createBitmap(color = Color.BLUE)
        )
        inputState.changes.emit(ProfileChange.PackageChanged(user = inputWorkProfile.user, packageName = FIRST_PACKAGE_NAME))
        runCurrent()
        assertNull(inputCache.read(key = createKey()))
        assertNotNull(inputCache.read(key = createKey(componentName = ComponentName(SECOND_PACKAGE_NAME, SECOND_PACKAGE_COMPONENT_NAME))))
    }

    @Test
    fun `evicts a package's icons when the system reports the package unavailable`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        inputState.changes.emit(
            ProfileChange.PackagesUnavailable(user = inputPersonalProfile.user, packageNames = listOf(FIRST_PACKAGE_NAME), replacing = true)
        )
        runCurrent()
        assertNull(inputCache.read(key = inputKey))
    }

    @Test
    fun `evicts a package's icons when the system reports the package removed`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        inputState.changes.emit(ProfileChange.PackageRemoved(user = inputPersonalProfile.user, packageName = FIRST_PACKAGE_NAME))
        runCurrent()
        assertNull(inputCache.read(key = inputKey))
    }

    @Test
    fun `does not store a Private Space icon`(): Unit = runTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val mockPrivateProfileSource: IProfileSource =
            createProfileSourceMock(state = ProfileSourceState(profiles = listOf(inputPrivateProfile)))
        val inputCache: IHomeIconCache = createCache(mockProfileSource = mockPrivateProfileSource)
        val inputKey: HomeIconCacheKey = createKey(profile = inputPrivateProfile.user)
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        assertNull(inputCache.read(key = inputKey))
        assertTrue(storedIconFiles(context = inputContext, packageName = FIRST_PACKAGE_NAME).isEmpty())
    }

    @Test
    fun `does not store an icon of a Profile it does not know`(): Unit = runTest {
        val inputUnknownUser: UserHandle = createUserHandle(userId = MISSING_WORK_USER_ID)
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey(profile = inputUnknownUser)
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        assertNull(inputCache.read(key = inputKey))
        assertTrue(storedIconFiles(context = inputContext, packageName = FIRST_PACKAGE_NAME).isEmpty())
    }

    @Test
    fun `does not store an unbadged icon whose App is not in the personal Profile`(): Unit = runTest {
        val inputComponentName: ComponentName = ComponentName(FIRST_PACKAGE_NAME, FIRST_COMPONENT_NAME)
        inputState.activitiesByUser[inputWorkProfile.user] =
            listOf(profileActivity(componentName = inputComponentName, user = inputWorkProfile.user))
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey(componentName = inputComponentName, profile = null)
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        assertNull(inputCache.read(key = inputKey))
        assertTrue(storedIconFiles(context = inputContext, packageName = FIRST_PACKAGE_NAME).isEmpty())
    }

    @Test
    fun `stores an unbadged icon whose App is in the personal Profile`(): Unit = runTest {
        val inputComponentName: ComponentName = ComponentName(FIRST_PACKAGE_NAME, FIRST_COMPONENT_NAME)
        inputState.activitiesByUser[inputPersonalProfile.user] =
            listOf(profileActivity(componentName = inputComponentName, user = inputPersonalProfile.user))
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey(componentName = inputComponentName, profile = null)
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        assertEquals(Color.RED, inputCache.read(key = inputKey)?.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX))
    }

    @Test
    fun `deletes the directory of an earlier rendering`(): Unit = runTest {
        val inputEarlierDirectory: File = File(inputContext.cacheDir, EARLIER_HOME_ICONS_DIRECTORY).apply { mkdirs() }
        File(inputEarlierDirectory, "icon.png").writeBytes(byteArrayOf(1))
        createCache()
        runCurrent()
        assertFalse(inputEarlierDirectory.exists())
    }

    @Test
    fun `deletes the icons of packages the live App list does not have`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        inputCache.write(key = createKey(), bitmap = createBitmap(color = Color.RED))
        inputCache.write(
            key = createKey(componentName = ComponentName(SECOND_PACKAGE_NAME, SECOND_PACKAGE_COMPONENT_NAME)),
            bitmap = createBitmap(color = Color.BLUE)
        )
        inputHomeApps.value = HomeApps.Live(apps = listOf(createApp(packageName = FIRST_PACKAGE_NAME)))
        runCurrent()
        assertNotNull(inputCache.read(key = createKey()))
        assertNull(inputCache.read(key = createKey(componentName = ComponentName(SECOND_PACKAGE_NAME, SECOND_PACKAGE_COMPONENT_NAME))))
    }

    @Test
    fun `removes the temporary file when an icon cannot be replaced`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        val inputTarget: File = storedIconFiles(context = inputContext, packageName = FIRST_PACKAGE_NAME).single()
        inputTarget.delete()
        inputTarget.mkdirs()
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.BLUE))
        assertEquals(
            listOf(inputTarget.name),
            storedIconFiles(context = inputContext, packageName = FIRST_PACKAGE_NAME).map { file ->
                file.name
            }
        )
    }

    @Test
    fun `stores one complete icon when the same key is written concurrently`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache(dispatcher = Dispatchers.Default)
        val inputKey: HomeIconCacheKey = createKey()
        (1..CONCURRENT_WRITE_COUNT).map { index ->
            async(Dispatchers.Default) {
                inputCache.write(key = inputKey, bitmap = createBitmap(color = if (index % 2 == 0) Color.RED else Color.BLUE))
            }
        }.awaitAll()
        val actualBitmap: Bitmap? = inputCache.read(key = inputKey)
        assertEquals(TEST_ICON_SIZE_PX, actualBitmap?.width)
        assertTrue(
            actualBitmap?.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX) == Color.RED ||
                actualBitmap?.getPixel(TEST_ICON_CENTER_PX, TEST_ICON_CENTER_PX) == Color.BLUE
        )
    }

    @Test
    fun `claims an icon's revalidation once`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        assertTrue(inputCache.claimRevalidation(key = inputKey))
        assertFalse(inputCache.claimRevalidation(key = inputKey))
    }

    @Test
    fun `releases every revalidation claim after a configuration change that can alter system icons`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        assertTrue(inputCache.claimRevalidation(key = inputKey))
        inputContext.onConfigurationChanged(nightModeToggled(configuration = inputContext.resources.configuration))
        assertEquals(1L, inputCache.configurationGeneration)
        assertTrue(inputCache.claimRevalidation(key = inputKey))
    }

    @Test
    fun `keeps revalidation claims across a layout-only configuration change`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        assertTrue(inputCache.claimRevalidation(key = inputKey))
        inputContext.onConfigurationChanged(orientationToggled(configuration = inputContext.resources.configuration))
        assertEquals(0L, inputCache.configurationGeneration)
        assertFalse(inputCache.claimRevalidation(key = inputKey))
    }

    @Test
    fun `reads only the personal Profile's activities to store an unbadged icon`(): Unit = runTest {
        val inputComponentName: ComponentName = ComponentName(FIRST_PACKAGE_NAME, FIRST_COMPONENT_NAME)
        inputState.activitiesByUser[inputPersonalProfile.user] =
            listOf(profileActivity(componentName = inputComponentName, user = inputPersonalProfile.user))
        val inputCache: IHomeIconCache = createCache()
        inputCache.write(key = createKey(componentName = inputComponentName, profile = null), bitmap = createBitmap(color = Color.RED))
        verify(exactly = 1) { mockProfileSource.activities(FIRST_PACKAGE_NAME, inputPersonalProfile.user) }
        verify(exactly = 0) { mockProfileSource.activities(any<String>(), inputWorkProfile.user) }
    }

    @Test
    fun `counts a replaced icon as a new generation of every size`(): Unit = runTest {
        val inputCache: IHomeIconCache = createCache()
        val inputKey: HomeIconCacheKey = createKey()
        val inputOtherSizeKey: HomeIconCacheKey = createKey(sizePx = TEST_ICON_SIZE_PX / 2)
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.RED))
        inputCache.write(key = inputOtherSizeKey, bitmap = createBitmap(color = Color.RED))
        assertEquals(0L, inputCache.generation(identity = inputKey.identity))
        inputCache.write(key = inputKey, bitmap = createBitmap(color = Color.BLUE))
        assertEquals(1L, inputCache.generation(identity = inputKey.identity))
        assertEquals(1L, inputCache.generation(identity = inputOtherSizeKey.identity))
    }

    private fun TestScope.createCache(
        mockProfileSource: IProfileSource = this@IHomeIconCacheImplTest.mockProfileSource,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
    ): IHomeIconCache = createHomeIconCache(
        context = inputContext,
        profileSource = mockProfileSource,
        appRepository = mockAppRepository,
        dispatcher = dispatcher
    )

    private fun createKey(
        componentName: ComponentName = ComponentName(FIRST_PACKAGE_NAME, FIRST_COMPONENT_NAME),
        profile: UserHandle? = inputPersonalProfile.user,
        lastUpdateTime: Long = TEST_APP_UPDATE_TIME,
        densityDpi: Int = TEST_DENSITY_DPI,
        sizePx: Int = TEST_ICON_SIZE_PX
    ): HomeIconCacheKey = HomeIconCacheKey(
        identity = "activity:${componentName.flattenToString()}:profile:$profile",
        componentName = componentName,
        profile = profile,
        lastUpdateTime = lastUpdateTime,
        densityDpi = densityDpi,
        sizePx = sizePx
    )

    private fun createBitmap(color: Int): Bitmap =
        Bitmap.createBitmap(TEST_ICON_SIZE_PX, TEST_ICON_SIZE_PX, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }

    private fun profileActivity(componentName: ComponentName, user: UserHandle): ProfileActivity = ProfileActivity(
        componentName = componentName,
        user = user,
        label = "App",
        badgedLabel = "App",
        firstInstallTime = 0L,
        lastUpdateTime = TEST_APP_UPDATE_TIME,
        isRemovable = true,
        isSystemApp = false,
        isSuspended = false
    )
}
