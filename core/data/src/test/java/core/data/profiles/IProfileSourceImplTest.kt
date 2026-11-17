package core.data.profiles

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import app.cash.turbine.test
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.TEST_SDK_36
import core.data.testing.TimberLogRecorder
import core.data.testing.WORK_USER_ID
import core.data.testing.createIntentSender
import core.data.testing.createUserHandle
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val OTHER_USER_ID: Int = 12
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"

/** Well past the window after the initial load in which a resume is the activity's startup resume. */
private const val STARTUP_RESUME_WINDOW_PASSED_MS: Long = 60_000L
private const val INITIAL_LOAD_FAILURES: Int = 2

// Two failed reads wait 1 s, then 2 s, before the third read succeeds.
private const val BACKED_OFF_LOAD_TIME_MS: Long = 3_000L

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IProfileSourceImplTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val personalUser: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)
    private val workUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
    private val privateUser: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
    private val otherUser: UserHandle = createUserHandle(userId = OTHER_USER_ID)
    private var elapsedRealtimeMs: Long = 0L

    @Test
    fun `constructs without calling the platform`() = runTest {
        val mockContext: Context = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        val actualSource: IProfileSourceImpl = createSource(context = mockContext)

        assertFalse(actualSource.isLoaded.value)
        assertTrue(actualSource.profiles.value.isEmpty())
        verify(exactly = 0) { mockContext.getSystemService(LauncherApps::class.java) }
        verify(exactly = 0) { mockContext.getSystemService(UserManager::class.java) }
        verify(exactly = 0) { mockContext.packageManager }
        assertFalse(actualSource.isLoaded.value)
    }

    @Test
    fun `loads the Profiles asynchronously and reports them loaded`() = runTest {
        val inputProfiles: List<UserHandle> = listOf(personalUser, workUser)
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns inputProfiles
        every { mockLauncherApps.getLauncherUserInfo(any()) } answers {
            launcherUserInfo(user = firstArg(), userType = userTypeFor(user = firstArg()))
        }
        val actualSource: IProfileSourceImpl = createSource()

        assertFalse(actualSource.isLoaded.value)
        assertTrue(actualSource.profiles.value.isEmpty())

        actualSource.awaitLoaded()

        assertTrue(actualSource.isLoaded.value)
        assertEquals(inputProfiles, actualSource.profiles.value.map { profile -> profile.user })
    }

    @Suppress("DEPRECATION")
    @Test
    fun `maps LauncherUserInfo types to ProfileKind`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser, workUser, privateUser, otherUser)
        every { mockLauncherApps.getLauncherUserInfo(any()) } answers {
            launcherUserInfo(user = firstArg(), userType = userTypeFor(user = firstArg()))
        }
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()

        assertEquals(
            mapOf(
                personalUser to ProfileKind.Personal,
                workUser to ProfileKind.Work,
                privateUser to ProfileKind.Private,
                otherUser to ProfileKind.Other
            ),
            source.profiles.value.associate { profile -> profile.user to profile.kind }
        )
    }

    @Test
    fun `does not reload the Profiles inside the startup resume window`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser)
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()

        source.refreshProfiles()

        verify(exactly = 1) { mockLauncherApps.profiles }
    }

    @Test
    fun `joins the initial load when refreshed while it is in flight`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser)
        val source: IProfileSourceImpl = createSource()

        val firstRefresh: Deferred<Unit> = async { source.refreshProfiles() }
        val secondRefresh: Deferred<Unit> = async { source.refreshProfiles() }
        firstRefresh.await()
        secondRefresh.await()

        assertTrue(source.isLoaded.value)
        verify(exactly = 1) { mockLauncherApps.profiles }
    }

    @Test
    fun `reloads the Profiles once the startup resume window has passed`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser)
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()
        leaveStartupResumeWindow()
        every { mockLauncherApps.profiles } returns listOf(personalUser, workUser)

        source.refreshProfiles()

        assertEquals(listOf(personalUser, workUser), source.profiles.value.map { profile -> profile.user })
    }

    @Test
    fun `retries the initial load until it succeeds`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        var isFirstRead: Boolean = true
        every { mockLauncherApps.profiles } answers {
            if (isFirstRead) {
                isFirstRead = false
                throw IllegalStateException("Profiles unavailable")
            }
            listOf(personalUser)
        }
        val source: IProfileSourceImpl = createSource()

        source.awaitLoaded()

        assertEquals(listOf(personalUser), source.profiles.value.map { profile -> profile.user })
    }

    @Test
    fun `backs off between failed initial loads`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        var failedReads: Int = 0
        every { mockLauncherApps.profiles } answers {
            if (failedReads < INITIAL_LOAD_FAILURES) {
                failedReads++
                throw IllegalStateException("Profiles unavailable")
            }
            listOf(personalUser)
        }
        val source: IProfileSourceImpl = createSource()

        advanceTimeBy(BACKED_OFF_LOAD_TIME_MS - 1L)
        runCurrent()
        assertFalse(source.isLoaded.value)
        advanceTimeBy(1L)
        runCurrent()

        assertTrue(source.isLoaded.value)
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `refreshes the Profiles and publishes a Profile the platform started reporting`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser)
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()
        leaveStartupResumeWindow()
        assertEquals(listOf(personalUser), source.profiles.value.map { profile -> profile.user })

        source.changes.test {
            every { mockLauncherApps.profiles } returns listOf(personalUser, privateUser)
            source.refreshProfiles()

            assertEquals(listOf(personalUser, privateUser), source.profiles.value.map { profile -> profile.user })
            assertEquals(ProfileChange.ProfileAdded(user = privateUser), awaitItem())
        }
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `refreshes the Profiles and publishes a Profile the platform stopped reporting`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser, privateUser)
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()
        leaveStartupResumeWindow()
        assertEquals(listOf(personalUser, privateUser), source.profiles.value.map { profile -> profile.user })

        source.changes.test {
            every { mockLauncherApps.profiles } returns listOf(personalUser)
            source.refreshProfiles()

            assertEquals(listOf(personalUser), source.profiles.value.map { profile -> profile.user })
            assertEquals(ProfileChange.ProfileRemoved(user = privateUser), awaitItem())
        }
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `publishes availability changes when the Private Space locks and unlocks on refresh`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser, privateUser)
        every { mockLauncherApps.getLauncherUserInfo(privateUser) } returns
            launcherUserInfo(user = privateUser, userType = UserManager.USER_TYPE_PROFILE_PRIVATE)
        val mockUserManager: UserManager = installUserManager()
        every { mockUserManager.isQuietModeEnabled(privateUser) } returns true
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()
        leaveStartupResumeWindow()

        source.changes.test {
            every { mockUserManager.isQuietModeEnabled(privateUser) } returns false
            source.refreshProfiles()
            assertEquals(ProfileChange.ProfileAvailable(user = privateUser), awaitItem())

            every { mockUserManager.isQuietModeEnabled(privateUser) } returns true
            source.refreshProfiles()
            assertEquals(ProfileChange.ProfileUnavailable(user = privateUser), awaitItem())
        }
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `publishes no changes when the refreshed Profiles are unchanged`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser, workUser)
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()
        leaveStartupResumeWindow()

        source.changes.test {
            source.refreshProfiles()

            expectNoEvents()
        }
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `refreshes the Profiles off the main thread`() = runTest {
        val backgroundCallRecorder: BackgroundCallRecorder = BackgroundCallRecorder()
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } answers {
            backgroundCallRecorder.record()
            listOf(personalUser)
        }
        val source: IProfileSourceImpl = createSource(dispatcher = Dispatchers.IO)
        source.awaitLoaded()
        leaveStartupResumeWindow()
        backgroundCallRecorder.reset()

        source.refreshProfiles()

        backgroundCallRecorder.assertOffMainThread()
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `reads the Private Space settings off the main thread`() = runTest {
        val backgroundCallRecorder: BackgroundCallRecorder = BackgroundCallRecorder()
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser)
        every { mockLauncherApps.privateSpaceSettingsIntent } answers {
            backgroundCallRecorder.record()
            createIntentSender(context = application)
        }
        val source: IProfileSourceImpl = createSource()

        assertNotNull(source.privateSpaceSettingsIntent())

        backgroundCallRecorder.assertOffMainThread()
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `keeps the last Profiles when reading them fails`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser, privateUser)
        val callbackSlot: CapturingSlot<LauncherApps.Callback> = slot()
        every { mockLauncherApps.registerCallback(capture(callbackSlot), any()) } just Runs
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()
        val inputProfiles: List<ProfileInfo> = source.profiles.value

        every { mockLauncherApps.profiles } throws IllegalStateException("Profiles unavailable")
        callbackSlot.captured.onUserConfigChanged(mockk(relaxed = true))

        assertEquals(inputProfiles, source.profiles.value)
    }

    @Test
    @Config(sdk = [TEST_SDK_36])
    fun `reads the Private Space entrypoint flag again when the user config changes`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(privateUser)
        every { mockLauncherApps.privateSpaceSettingsIntent } returns createIntentSender(context = application)
        val callbackSlot: CapturingSlot<LauncherApps.Callback> = slot()
        every { mockLauncherApps.registerCallback(capture(callbackSlot), any()) } just Runs
        val mockUserInfo: LauncherUserInfo = launcherUserInfo(user = privateUser, userType = UserManager.USER_TYPE_PROFILE_PRIVATE)
        every { mockUserInfo.userConfig } returns entrypointConfig(hidden = false)
        every { mockLauncherApps.getLauncherUserInfo(any()) } returns mockUserInfo
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()

        assertFalse(source.profiles.value.single().isEntrypointHidden)

        every { mockUserInfo.userConfig } returns entrypointConfig(hidden = true)
        callbackSlot.captured.onUserConfigChanged(mockUserInfo)

        assertTrue(source.profiles.value.single().isEntrypointHidden)
        assertNotNull(source.privateSpaceSettingsIntent())
    }

    @Test
    fun `offers no Private Space settings below Android 16`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns emptyList<UserHandle>()
        every { mockLauncherApps.privateSpaceSettingsIntent } returns createIntentSender(context = application)
        val source: IProfileSourceImpl = createSource()

        assertNull(source.privateSpaceSettingsIntent())
    }

    @Test
    fun `loads an activity icon for a non-personal Profile`() = runTest {
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val expectedIcon: Drawable = ColorDrawable(Color.RED)
        val mockActivityInfo: LauncherActivityInfo = mockk(relaxed = true)
        every { mockActivityInfo.componentName } returns inputComponentName
        every { mockActivityInfo.getIcon(any()) } returns expectedIcon
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.getActivityList(APP_PACKAGE_NAME, workUser) } returns listOf(mockActivityInfo)
        val source: IProfileSourceImpl = createSource()

        val actualIcon: Drawable? = source.activityIcon(componentName = inputComponentName, user = workUser)

        assertEquals(expectedIcon, actualIcon)
        verify(exactly = 1) { mockActivityInfo.getIcon(application.resources.displayMetrics.densityDpi) }
    }

    @Test
    fun `marks Apps removable unless they are unmodified system Apps`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.getActivityList(null, workUser) } returns listOf(
            mockActivityInfo(applicationInfo = ApplicationInfo()),
            mockActivityInfo(
                applicationInfo = ApplicationInfo().apply {
                    flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                }
            ),
            mockActivityInfo(
                applicationInfo = ApplicationInfo().apply {
                    flags = ApplicationInfo.FLAG_SYSTEM
                }
            )
        )
        val source: IProfileSourceImpl = createSource()

        val actualRemovability: List<Boolean> = source.activities(workUser).map { activity -> activity.isRemovable }

        assertEquals(listOf(true, true, false), actualRemovability)
    }

    @Test
    fun `carries each activity's package suspension state`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.getActivityList(null, workUser) } returns listOf(
            mockActivityInfo(applicationInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SUSPENDED }),
            mockActivityInfo(applicationInfo = ApplicationInfo())
        )
        val source: IProfileSourceImpl = createSource()

        val actualSuspension: List<Boolean> = source.activities(workUser).map { activity -> activity.isSuspended }

        assertEquals(listOf(true, false), actualSuspension)
    }

    @Test
    fun `publishes package suspension changes`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        val callbackSlot: CapturingSlot<LauncherApps.Callback> = slot()
        every { mockLauncherApps.registerCallback(capture(callbackSlot), any()) } just Runs
        val source: IProfileSourceImpl = createSource()
        source.awaitLoaded()

        source.changes.test {
            callbackSlot.captured.onPackagesSuspended(arrayOf(APP_PACKAGE_NAME), workUser)
            assertEquals(
                ProfileChange.PackagesSuspended(user = workUser, packageNames = listOf(APP_PACKAGE_NAME), isSuspended = true),
                awaitItem()
            )
            callbackSlot.captured.onPackagesUnsuspended(arrayOf(APP_PACKAGE_NAME), workUser)
            assertEquals(
                ProfileChange.PackagesSuspended(user = workUser, packageNames = listOf(APP_PACKAGE_NAME), isSuspended = false),
                awaitItem()
            )
        }
    }

    @Test
    fun `hides uninstall when a Profile restricts it or its restrictions cannot be read`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(personalUser, workUser, privateUser, otherUser)
        val mockUserManager: UserManager = installUserManager()
        every { mockUserManager.getUserRestrictions(personalUser) } returns Bundle().apply {
            putBoolean(UserManager.DISALLOW_APPS_CONTROL, true)
        }
        every { mockUserManager.getUserRestrictions(privateUser) } returns Bundle().apply {
            putBoolean(UserManager.DISALLOW_UNINSTALL_APPS, true)
        }
        every { mockUserManager.getUserRestrictions(otherUser) } returns Bundle()
        every { mockUserManager.getUserRestrictions(workUser) } throws SecurityException()
        val source: IProfileSourceImpl = createSource()

        assertTrue(source.canUninstall(otherUser))
        assertFalse(source.canUninstall(personalUser))
        assertFalse(source.canUninstall(privateUser))
        assertFalse(source.canUninstall(workUser))
    }

    @Test
    fun `loads an application icon for a non-personal Profile`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.getApplicationInfo(APP_PACKAGE_NAME, 0, workUser) } returns application.applicationInfo
        val source: IProfileSourceImpl = createSource()

        val actualIcon: Drawable? = source.applicationIcon(packageName = APP_PACKAGE_NAME, user = workUser)

        assertNotNull(actualIcon)
        verify(exactly = 1) { mockLauncherApps.getApplicationInfo(APP_PACKAGE_NAME, 0, workUser) }
    }

    @Test
    fun `logs activity failures without the App package or activity`() = runTest {
        val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every {
            mockLauncherApps.getActivityList(APP_PACKAGE_NAME, workUser)
        } throws PackageManager.NameNotFoundException(APP_PACKAGE_NAME)
        val source: IProfileSourceImpl = createSource()
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.record {
            assertNull(source.activityIcon(componentName = inputComponentName, user = workUser))
            assertTrue(source.activities(packageName = APP_PACKAGE_NAME, user = workUser).isEmpty())
        }

        logRecorder.assertLoggedWithout(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
    }

    @Test
    fun `maps the quiet request outcomes`() = runTest {
        val mockLauncherApps: LauncherApps = installLauncherApps()
        every { mockLauncherApps.profiles } returns listOf(workUser)
        val mockUserManager: UserManager = installUserManager()
        val source: IProfileSourceImpl = createSource()

        every { mockUserManager.requestQuietModeEnabled(true, workUser) } returns true
        assertEquals(QuietRequestResult.Changed, source.requestQuiet(user = workUser, quiet = true))

        every { mockUserManager.requestQuietModeEnabled(false, workUser) } returns false
        assertEquals(QuietRequestResult.AwaitingCredential, source.requestQuiet(user = workUser, quiet = false))

        every { mockUserManager.requestQuietModeEnabled(true, workUser) } throws SecurityException()
        assertEquals(QuietRequestResult.Failed, source.requestQuiet(user = workUser, quiet = true))

        every { mockUserManager.requestQuietModeEnabled(true, workUser) } throws IllegalArgumentException()
        assertEquals(QuietRequestResult.Failed, source.requestQuiet(user = workUser, quiet = true))
    }

    private fun TestScope.createSource(
        context: Context = application,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        elapsedRealtime: () -> Long = { elapsedRealtimeMs }
    ): IProfileSourceImpl = IProfileSourceImpl(
        context = context,
        scope = backgroundScope,
        dispatcher = dispatcher,
        elapsedRealtime = elapsedRealtime
    )

    private fun installLauncherApps(): LauncherApps {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        shadowOf(application).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        return mockLauncherApps
    }

    private fun installUserManager(): UserManager {
        val mockUserManager: UserManager = mockk(relaxed = true)
        shadowOf(application).setSystemService(Context.USER_SERVICE, mockUserManager)
        return mockUserManager
    }

    private fun leaveStartupResumeWindow() {
        elapsedRealtimeMs = STARTUP_RESUME_WINDOW_PASSED_MS
    }

    private fun mockActivityInfo(applicationInfo: ApplicationInfo): LauncherActivityInfo {
        val mockActivityInfo: LauncherActivityInfo = mockk(relaxed = true)
        every { mockActivityInfo.applicationInfo } returns applicationInfo
        return mockActivityInfo
    }

    private fun launcherUserInfo(user: UserHandle, userType: String): LauncherUserInfo {
        val mockUserInfo: LauncherUserInfo = mockk(relaxed = true)
        every { mockUserInfo.userType } returns userType
        return mockUserInfo
    }

    private fun entrypointConfig(hidden: Boolean): Bundle = Bundle().apply {
        putBoolean(LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, hidden)
    }

    private fun userTypeFor(user: UserHandle): String = when (user) {
        workUser -> UserManager.USER_TYPE_PROFILE_MANAGED
        privateUser -> UserManager.USER_TYPE_PROFILE_PRIVATE
        else -> UserManager.USER_TYPE_PROFILE_CLONE
    }
}

private class BackgroundCallRecorder {
    private var isMainThread: Boolean = true

    fun record() {
        isMainThread = Looper.myLooper() == Looper.getMainLooper()
    }

    fun reset() {
        isMainThread = true
    }

    fun assertOffMainThread() {
        assertFalse(isMainThread)
    }
}
