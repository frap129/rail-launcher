package core.data.shortcuts

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.profiles.ProfileKind
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createApp
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createPrivateApp
import core.data.testing.createProfileInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLauncherApps

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IShortcutSourceImplTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val shadowLauncherApps: ShadowLauncherApps = Shadow.extract(launcherApps)
    private val profile: UserHandle = Process.myUserHandle()
    private val mockLaunchableRepository: ILaunchableRepository = mockk()
    private val source: IShortcutSourceImpl = IShortcutSourceImpl(
        context = context,
        launchableRepository = mockLaunchableRepository
    )

    @Test
    fun `resolve returns the shortcuts the package holds and reports the rest gone`() = runTest {
        shadowLauncherApps.setHasShortcutHostPermission(true)
        addShortcut(id = FIRST_SHORTCUT_ID)
        val inputFirstKey: ShortcutKey = shortcutKey(id = FIRST_SHORTCUT_ID)
        val inputSecondKey: ShortcutKey = shortcutKey(id = SECOND_SHORTCUT_ID)

        source.resolve(keys = setOf(inputFirstKey, inputSecondKey)).test {
            val actualResolution: ShortcutResolution = awaitItem()
            assertEquals(setOf(inputFirstKey), actualResolution.shortcuts.keys)
            assertEquals(setOf(inputSecondKey), actualResolution.goneKeys)
        }
    }

    @Test
    fun `resolve reports a single deleted shortcut gone while the package keeps another`() = runTest {
        shadowLauncherApps.setHasShortcutHostPermission(true)
        addShortcut(id = SECOND_SHORTCUT_ID)
        val inputKey: ShortcutKey = shortcutKey(id = FIRST_SHORTCUT_ID)

        source.resolve(keys = setOf(inputKey)).test {
            val actualResolution: ShortcutResolution = awaitItem()
            assertEquals(emptyMap<ShortcutKey, ShortcutLaunchable>(), actualResolution.shortcuts)
            assertEquals(setOf(inputKey), actualResolution.goneKeys)
        }
    }

    @Test
    fun `resolve leaves the shortcuts unresolved while the package holds none`() = runTest {
        shadowLauncherApps.setHasShortcutHostPermission(true)
        val inputKey: ShortcutKey = shortcutKey(id = FIRST_SHORTCUT_ID)

        source.resolve(keys = setOf(inputKey)).test {
            val actualResolution: ShortcutResolution = awaitItem()
            assertEquals(emptyMap<ShortcutKey, ShortcutLaunchable>(), actualResolution.shortcuts)
            assertEquals(emptySet<ShortcutKey>(), actualResolution.goneKeys)
        }
    }

    @Test
    fun `resolve returns a disabled pinned shortcut with its enabled state`() = runTest {
        shadowLauncherApps.setHasShortcutHostPermission(true)
        addShortcut(id = FIRST_SHORTCUT_ID, disabled = true)
        val inputKey: ShortcutKey = shortcutKey(id = FIRST_SHORTCUT_ID)

        source.resolve(keys = setOf(inputKey)).test {
            val actualResolution: ShortcutResolution = awaitItem()
            assertEquals(setOf(inputKey), actualResolution.shortcuts.keys)
            assertFalse(actualResolution.shortcuts.getValue(inputKey).isEnabled)
            assertEquals(emptySet<ShortcutKey>(), actualResolution.goneKeys)
        }
    }

    @Test
    fun `offers Shortcuts for private Apps while the Private Space is Unlocked`() = runTest {
        shadowLauncherApps.setHasShortcutHostPermission(true)
        val inputPrivateProfile = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp = createApp(name = "Secret", packageName = "com.example.secret", profileInfo = inputPrivateProfile)
        val inputOtherApp = createApp(name = "Other", packageName = "com.example.other")
        val sourceWithState: IShortcutSourceImpl = IShortcutSourceImpl(
            context = context,
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOtherApp, inputPrivateApp))
        )

        val actualApps: List<AppShortcuts> = sourceWithState.appsWithShortcuts().first()

        assertEquals(
            setOf(inputOtherApp.key, inputPrivateApp.key),
            actualApps.mapTo(mutableSetOf()) { appShortcuts ->
                appShortcuts.app.key
            }
        )
    }

    @Test
    fun `offers Shortcuts for the package representative only`() = runTest {
        shadowLauncherApps.setHasShortcutHostPermission(true)
        val inputRepresentative: App = createApp(name = "Example", packageName = "com.example.app")
        val inputOtherActivity: App = inputRepresentative.copy(
            componentName = ComponentName("com.example.app", "com.example.app.SettingsActivity"),
            isPackageRepresentative = false
        )
        val sourceWithState: IShortcutSourceImpl = IShortcutSourceImpl(
            context = context,
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputRepresentative, inputOtherActivity))
        )

        val actualApps: List<AppShortcuts> = sourceWithState.appsWithShortcuts().first()

        assertEquals(listOf(inputRepresentative.key), actualApps.map { appShortcuts -> appShortcuts.app.key })
    }

    @Test
    fun `logs private App shortcut pin and resolve failures without its package or key`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.hasShortcutHostPermission() } returns true
        every {
            mockLauncherApps.pinShortcuts(any(), any(), any())
        } throws SecurityException("No permission to pin shortcuts for ${inputPrivateApp.packageName}")
        every {
            mockLauncherApps.getShortcuts(any(), any())
        } throws SecurityException("No permission to resolve shortcuts for ${inputPrivateApp.packageName}")
        shadowOf(context).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val source: IShortcutSourceImpl = IShortcutSourceImpl(
            context = context,
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputPrivateApp))
        )
        val inputKey: ShortcutKey = ShortcutKey(
            packageName = inputPrivateApp.packageName,
            id = FIRST_SHORTCUT_ID,
            profile = inputPrivateApp.profile
        )
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.recordSuspending {
            source.pin(
                packageName = inputPrivateApp.packageName,
                profile = inputPrivateApp.profile,
                ids = setOf(inputKey.id)
            )
            val actualResolution: ShortcutResolution = source.resolve(keys = setOf(inputKey)).first()
            assertEquals(emptyMap<ShortcutKey, ShortcutLaunchable>(), actualResolution.shortcuts)
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.packageName, inputPrivateApp.key)
    }

    @Test
    fun `logs a private App shortcut permission failure without its package or key`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.hasShortcutHostPermission() } returns true
        every {
            mockLauncherApps.getActivityList(inputPrivateApp.packageName, inputPrivateApp.profile)
        } throws SecurityException("No permission to read ${inputPrivateApp.packageName}")
        shadowOf(context).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val source: IShortcutSourceImpl = IShortcutSourceImpl(
            context = context,
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputPrivateApp))
        )
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.recordSuspending {
            val actualResult: ShortcutsResult = source.shortcuts(
                packageName = inputPrivateApp.packageName,
                profile = inputPrivateApp.profile
            ).first()
            assertEquals(ShortcutsResult.Unavailable, actualResult)
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.packageName, inputPrivateApp.key)
    }

    private fun addShortcut(id: String, disabled: Boolean = false) {
        val shortcut: ShortcutInfo = ShortcutInfo.Builder(context, id)
            .setShortLabel(id)
            .setIntent(Intent(Intent.ACTION_VIEW).setPackage(context.packageName))
            .build()
        if (disabled) {
            // The public builder cannot disable a Shortcut; the platform-only flag is available here.
            val disabledFlag: Int = ShortcutInfo::class.java.getField("FLAG_DISABLED").getInt(null)
            ShortcutInfo::class.java.getMethod("addFlags", Int::class.javaPrimitiveType).invoke(shortcut, disabledFlag)
        }
        shadowLauncherApps.addDynamicShortcut(shortcut)
    }

    private fun shortcutKey(id: String): ShortcutKey = ShortcutKey(packageName = context.packageName, id = id, profile = profile)
}
