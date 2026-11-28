package core.data.shortcuts

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.homerole.IHomeRoleSource
import core.data.testing.FIRST_PACKAGE_NAME
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.FakeHomeRoleSource
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.SECOND_PACKAGE_NAME
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.TEST_SDK
import core.data.testing.THIRD_PACKAGE_NAME
import core.data.testing.TimberLogRecorder
import core.data.testing.WORK_USER_ID
import core.data.testing.createPrivateApp
import core.data.testing.createUserHandle
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    private val source: IShortcutSourceImpl = createShortcutSource()
    private val firstApp: AppProfile = AppProfile(packageName = FIRST_PACKAGE_NAME, profile = profile)
    private val secondApp: AppProfile = AppProfile(packageName = SECOND_PACKAGE_NAME, profile = profile)

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
        useLauncherApps(mockLauncherApps = mockLauncherApps)
        val source: IShortcutSourceImpl = createShortcutSource()
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
        useLauncherApps(mockLauncherApps = mockLauncherApps)
        val source: IShortcutSourceImpl = createShortcutSource()
        val inputApp: AppProfile = AppProfile(packageName = inputPrivateApp.packageName, profile = inputPrivateApp.profile)
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.recordSuspending {
            val actualResult: ShortcutsResult = source.shortcuts(app = inputApp).first()
            assertEquals(ShortcutsResult.Unavailable, actualResult)
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.packageName, inputPrivateApp.key)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `given Rail does not hold the Home role, when granted, then Shortcuts reload without a new collector`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val mockHomeRoleSource: FakeHomeRoleSource = FakeHomeRoleSource(isHomeRoleHeld = false)
        val inputSource: IShortcutSourceImpl = createListeningSource(
            mockLauncherApps = mockLauncherApps,
            homeRoleSource = mockHomeRoleSource
        )
        every { mockLauncherApps.hasShortcutHostPermission() } returns false

        inputSource.shortcuts(app = firstApp).test {
            assertEquals(ShortcutsResult.Unavailable, awaitItem())
            every { mockLauncherApps.hasShortcutHostPermission() } returns true
            mockHomeRoleSource.setHomeRoleHeld(isHeld = true)
            runCurrent()

            assertEquals(ShortcutsResult.Available(shortcuts = emptyList()), awaitItem())
            verify(exactly = 1) { mockLauncherApps.getActivityList(FIRST_PACKAGE_NAME, profile) }
        }
    }

    @Test
    fun `listening to many apps registers one callback and unregisters it once when the last stops`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val source: IShortcutSourceImpl = createListeningSource(mockLauncherApps = mockLauncherApps)
        val inputThirdApp: AppProfile = AppProfile(packageName = THIRD_PACKAGE_NAME, profile = profile)
        val inputKeys: Set<ShortcutKey> = setOf(ShortcutKey(packageName = THIRD_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile))

        source.shortcuts(app = firstApp).test {
            awaitItem()
            source.shortcuts(app = secondApp).test {
                awaitItem()
                source.shortcuts(app = inputThirdApp).test {
                    awaitItem()
                    source.resolve(keys = inputKeys).test {
                        awaitItem()
                        verify(exactly = 1) { mockLauncherApps.registerCallback(any(), any()) }
                    }
                    verify(exactly = 0) { mockLauncherApps.unregisterCallback(any()) }
                }
                verify(exactly = 0) { mockLauncherApps.unregisterCallback(any()) }
            }
            verify(exactly = 0) { mockLauncherApps.unregisterCallback(any()) }
        }
        verify(exactly = 1) { mockLauncherApps.unregisterCallback(any()) }
    }

    @Test
    fun `registers the listener with the main looper`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val handlerSlot: CapturingSlot<Handler> = slot()
        val source: IShortcutSourceImpl = createListeningSource(mockLauncherApps = mockLauncherApps, handlerSlot = handlerSlot)

        source.shortcuts(app = firstApp).test { awaitItem() }

        assertSame(Looper.getMainLooper(), handlerSlot.captured.looper)
    }

    @Test
    fun `refreshes only the app a package is added to`() = runTest {
        assertRefreshesOnlyChangedApp { callback -> callback.onPackageAdded(SECOND_PACKAGE_NAME, profile) }
    }

    @Test
    fun `refreshes only the app a package is removed from`() = runTest {
        assertRefreshesOnlyChangedApp { callback -> callback.onPackageRemoved(SECOND_PACKAGE_NAME, profile) }
    }

    @Test
    fun `refreshes only the app a package change names`() = runTest {
        assertRefreshesOnlyChangedApp { callback -> callback.onPackageChanged(SECOND_PACKAGE_NAME, profile) }
    }

    @Test
    fun `refreshes only the app whose packages become available`() = runTest {
        assertRefreshesOnlyChangedApp { callback ->
            callback.onPackagesAvailable(arrayOf(SECOND_PACKAGE_NAME), profile, true)
        }
    }

    @Test
    fun `refreshes only the app whose packages become unavailable`() = runTest {
        assertRefreshesOnlyChangedApp { callback ->
            callback.onPackagesUnavailable(arrayOf(SECOND_PACKAGE_NAME), profile, true)
        }
    }

    @Test
    fun `refreshes only the app whose Shortcuts changed`() = runTest {
        assertRefreshesOnlyChangedApp { callback -> callback.onShortcutsChanged(SECOND_PACKAGE_NAME, emptyList(), profile) }
    }

    @Test
    fun `refreshes only the app whose Profile a change names`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val callbackSlot: CapturingSlot<LauncherApps.Callback> = slot()
        val source: IShortcutSourceImpl = createListeningSource(mockLauncherApps = mockLauncherApps, callbackSlot = callbackSlot)
        val inputPersonalApp: AppProfile = AppProfile(
            packageName = FIRST_PACKAGE_NAME,
            profile = createUserHandle(userId = PERSONAL_USER_ID)
        )
        val inputWorkApp: AppProfile = AppProfile(
            packageName = FIRST_PACKAGE_NAME,
            profile = createUserHandle(userId = WORK_USER_ID)
        )

        source.shortcuts(app = inputPersonalApp).test {
            awaitItem()
            source.shortcuts(app = inputWorkApp).test {
                awaitItem()

                callbackSlot.captured.onShortcutsChanged(FIRST_PACKAGE_NAME, emptyList(), inputWorkApp.profile)
                awaitItem()
                verify(exactly = 1) { mockLauncherApps.getActivityList(FIRST_PACKAGE_NAME, inputPersonalApp.profile) }
                verify(exactly = 2) { mockLauncherApps.getActivityList(FIRST_PACKAGE_NAME, inputWorkApp.profile) }
                expectNoEvents()
            }
            expectNoEvents()

            callbackSlot.captured.onShortcutsChanged(FIRST_PACKAGE_NAME, emptyList(), inputPersonalApp.profile)
            awaitItem()
            verify(exactly = 2) { mockLauncherApps.getActivityList(FIRST_PACKAGE_NAME, inputPersonalApp.profile) }
            verify(exactly = 2) { mockLauncherApps.getActivityList(FIRST_PACKAGE_NAME, inputWorkApp.profile) }
            expectNoEvents()
        }
        verify(exactly = 1) { mockLauncherApps.unregisterCallback(any()) }
    }

    @Test
    fun `resolve refreshes on a change for its app and not another`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val callbackSlot: CapturingSlot<LauncherApps.Callback> = slot()
        val source: IShortcutSourceImpl = createListeningSource(mockLauncherApps = mockLauncherApps, callbackSlot = callbackSlot)
        val inputKeys: Set<ShortcutKey> = setOf(ShortcutKey(packageName = FIRST_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile))

        source.resolve(keys = inputKeys).test {
            awaitItem()
            callbackSlot.captured.onShortcutsChanged(THIRD_PACKAGE_NAME, emptyList(), profile)
            expectNoEvents()

            callbackSlot.captured.onShortcutsChanged(FIRST_PACKAGE_NAME, emptyList(), profile)
            awaitItem()
            verify(exactly = 2) { mockLauncherApps.getShortcuts(any(), profile) }
        }
    }

    @Test
    fun `registers again after a failed unregister`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val source: IShortcutSourceImpl = createListeningSource(mockLauncherApps = mockLauncherApps)
        every { mockLauncherApps.unregisterCallback(any()) } throws IllegalStateException("No listener")

        source.shortcuts(app = firstApp).test { awaitItem() }
        source.shortcuts(app = secondApp).test { awaitItem() }

        verify(exactly = 2) { mockLauncherApps.registerCallback(any(), any()) }
    }

    @Test
    fun `reports shortcuts unavailable when the listener cannot be registered`() = runTest {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val source: IShortcutSourceImpl = createListeningSource(mockLauncherApps = mockLauncherApps)
        every { mockLauncherApps.registerCallback(any(), any()) } throws IllegalStateException("No listener")

        val actualResult: ShortcutsResult = source.shortcuts(app = firstApp).first()

        assertEquals(ShortcutsResult.Unavailable, actualResult)
        verify(exactly = 0) { mockLauncherApps.unregisterCallback(any()) }
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

    private suspend fun TestScope.assertRefreshesOnlyChangedApp(change: (LauncherApps.Callback) -> Unit) {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        val callbackSlot: CapturingSlot<LauncherApps.Callback> = slot()
        val source: IShortcutSourceImpl = createListeningSource(mockLauncherApps = mockLauncherApps, callbackSlot = callbackSlot)

        source.shortcuts(app = firstApp).test {
            awaitItem()
            source.shortcuts(app = secondApp).test {
                awaitItem()
                change(callbackSlot.captured)
                awaitItem()
                verify(exactly = 1) { mockLauncherApps.getActivityList(FIRST_PACKAGE_NAME, profile) }
                verify(exactly = 2) { mockLauncherApps.getActivityList(SECOND_PACKAGE_NAME, profile) }
                expectNoEvents()
            }
            expectNoEvents()
        }
    }

    private fun createShortcutSource(
        shortcutListener: IShortcutListener = IShortcutListenerImpl(context = context),
        homeRoleSource: IHomeRoleSource = FakeHomeRoleSource(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): IShortcutSourceImpl = IShortcutSourceImpl(
        context = context,
        shortcutListener = shortcutListener,
        homeRoleSource = homeRoleSource,
        dispatcher = dispatcher
    )

    private fun TestScope.createListeningSource(
        mockLauncherApps: LauncherApps,
        callbackSlot: CapturingSlot<LauncherApps.Callback> = slot(),
        handlerSlot: CapturingSlot<Handler> = slot(),
        homeRoleSource: IHomeRoleSource = FakeHomeRoleSource()
    ): IShortcutSourceImpl {
        every { mockLauncherApps.hasShortcutHostPermission() } returns true
        every { mockLauncherApps.registerCallback(capture(callbackSlot), capture(handlerSlot)) } just Runs
        every { mockLauncherApps.unregisterCallback(any()) } just Runs
        useLauncherApps(mockLauncherApps = mockLauncherApps)
        return createShortcutSource(homeRoleSource = homeRoleSource, dispatcher = StandardTestDispatcher(testScheduler))
    }

    private fun useLauncherApps(mockLauncherApps: LauncherApps) {
        shadowOf(context).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
    }
}
