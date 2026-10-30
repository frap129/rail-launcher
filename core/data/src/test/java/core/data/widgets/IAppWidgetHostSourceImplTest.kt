package core.data.widgets

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createIconRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PERSONAL_PACKAGE: String = "com.example.personal.widget"
private const val WORK_PACKAGE: String = "com.example.work.widget"
private const val PRIVATE_PACKAGE: String = "com.example.private.widget"
private const val WORK_SERIAL: Long = 10L
private const val APP_WIDGET_ID: Int = 1

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IAppWidgetHostSourceImplTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val personalUser: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)
    private val workUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
    private val privateUser: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
    private val personalProfile: ProfileInfo = ProfileInfo(
        user = personalUser,
        serial = PERSONAL_USER_ID.toLong(),
        kind = ProfileKind.Personal,
        isQuiet = false
    )
    private val workProfile: ProfileInfo = ProfileInfo(
        user = workUser,
        serial = WORK_SERIAL,
        kind = ProfileKind.Work,
        isQuiet = false
    )
    private val privateProfile: ProfileInfo = ProfileInfo(
        user = privateUser,
        serial = PRIVATE_USER_ID.toLong(),
        kind = ProfileKind.Private,
        isQuiet = false
    )

    @Test
    fun `lists providers from personal, work and unlocked Private Space Profiles`() = runTest {
        val inputState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(personalProfile, workProfile, privateProfile)
        )
        val source: IAppWidgetHostSourceImpl = createSourceWithAllProviders(state = inputState)

        val actualProviders: List<WidgetProviderInfo> = source.providers.first()

        assertEquals(
            listOf(PERSONAL_PACKAGE, WORK_PACKAGE, PRIVATE_PACKAGE),
            actualProviders.map { provider -> provider.provider.packageName }
        )
        assertEquals(listOf(false, true, false), actualProviders.map { provider -> provider.isWorkProfile })
    }

    @Test
    fun `hides providers from a Locked Private Space`() = runTest {
        val inputState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(personalProfile, workProfile, privateProfile.copy(isQuiet = true))
        )
        val source: IAppWidgetHostSourceImpl = createSourceWithAllProviders(state = inputState)

        val actualProviders: List<WidgetProviderInfo> = source.providers.first()

        assertEquals(listOf(PERSONAL_PACKAGE, WORK_PACKAGE), actualProviders.map { provider -> provider.provider.packageName })
    }

    @Test
    fun `reports a replacing package as updating`() = runTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(workProfile))
        val source: IAppWidgetHostSourceImpl = createSource(state = inputState, applicationInfo = { _, _ -> null })
        backgroundScope.launch { source.providerChanges.collect() }
        runCurrent()

        inputState.changes.emit(
            ProfileChange.PackagesUnavailable(
                user = workUser,
                packageNames = listOf(WORK_PACKAGE),
                replacing = true
            )
        )
        runCurrent()

        assertEquals(
            WidgetAvailability.Updating,
            source.availability(AppWidgetId(APP_WIDGET_ID), provider(packageName = WORK_PACKAGE).toProvider(), WORK_SERIAL)
        )
    }

    @Test
    fun `reports an unavailable package as gone when it is not replacing`() = runTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(workProfile))
        val source: IAppWidgetHostSourceImpl = createSource(state = inputState, applicationInfo = { _, _ -> null })

        assertEquals(
            WidgetAvailability.Gone,
            source.availability(AppWidgetId(APP_WIDGET_ID), provider(packageName = WORK_PACKAGE).toProvider(), WORK_SERIAL)
        )
    }

    @Test
    fun `reports a paused work Profile as paused`() = runTest {
        val inputState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(workProfile.copy(isQuiet = true))
        )
        val source: IAppWidgetHostSourceImpl = createSource(state = inputState, applicationInfo = { _, _ -> enabledApplicationInfo() })

        assertEquals(
            WidgetAvailability.ProfilePaused,
            source.availability(AppWidgetId(APP_WIDGET_ID), provider(packageName = WORK_PACKAGE).toProvider(), WORK_SERIAL)
        )
    }

    @Suppress("DEPRECATION")
    private fun TestScope.createSource(
        state: ProfileSourceState,
        applicationInfo: (String, UserHandle) -> ApplicationInfo?
    ): IAppWidgetHostSourceImpl {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.getApplicationInfo(any(), any(), any()) } answers {
            applicationInfo(firstArg(), thirdArg()) ?: throw PackageManager.NameNotFoundException()
        }
        shadowOf(application).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = state)
        return IAppWidgetHostSourceImpl(
            context = application,
            appWidgetHost = mockk<AppWidgetHost>(relaxed = true),
            iconRepository = createIconRepositoryMock(inputIcon = Icon.ApplicationIcon(packageName = "com.example.icon")),
            preferencesRepository = createPreferencesRepositoryMock(),
            callbacks = AppWidgetHostCallbacks(),
            profileSource = mockProfileSource,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    private fun TestScope.createSourceWithAllProviders(state: ProfileSourceState): IAppWidgetHostSourceImpl {
        val source: IAppWidgetHostSourceImpl = createSource(state = state, applicationInfo = { _, _ -> enabledApplicationInfo() })
        val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(application)
        shadowOf(appWidgetManager).addInstalledProvidersForProfile(personalUser, provider(packageName = PERSONAL_PACKAGE))
        shadowOf(appWidgetManager).addInstalledProvidersForProfile(workUser, provider(packageName = WORK_PACKAGE))
        shadowOf(appWidgetManager).addInstalledProvidersForProfile(privateUser, provider(packageName = PRIVATE_PACKAGE))
        return source
    }

    private fun provider(packageName: String): AppWidgetProviderInfo {
        val mockProvider: AppWidgetProviderInfo = mockk(relaxed = true)
        mockProvider.provider = ComponentName(packageName, "$packageName.Provider")
        mockProvider.widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        mockProvider.minWidth = 1
        mockProvider.minHeight = 1
        every { mockProvider.loadLabel(any()) } returns "Widget"
        every { mockProvider.loadDescription(any()) } returns null
        return mockProvider
    }

    private fun AppWidgetProviderInfo.toProvider(): WidgetProvider = WidgetProvider(
        packageName = provider.packageName,
        className = provider.className
    )

    private fun enabledApplicationInfo(): ApplicationInfo = ApplicationInfo().apply {
        enabled = true
    }
}
