package core.data.shortcuts

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps
import core.data.apps.model.App
import core.data.profiles.ProfileKind
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createProfileInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLauncherApps

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IShortcutPickerSourceImplTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val shadowLauncherApps: ShadowLauncherApps = Shadow.extract(launcherApps)
    private val shortcutSource: IShortcutSource = IShortcutSourceImpl(context = context)

    @Before
    fun grantShortcutHostPermission() {
        shadowLauncherApps.setHasShortcutHostPermission(true)
    }

    @Test
    fun `offers Shortcuts for private Apps while the Private Space is Unlocked`() = runTest {
        val inputPrivateProfile = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp = createApp(name = "Secret", packageName = "com.example.secret", profileInfo = inputPrivateProfile)
        val inputOtherApp = createApp(name = "Other", packageName = "com.example.other")
        val source: IShortcutPickerSourceImpl = IShortcutPickerSourceImpl(
            shortcutSource = shortcutSource,
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOtherApp, inputPrivateApp))
        )

        val actualApps: List<AppShortcuts> = source.appsWithShortcuts().first()

        assertEquals(
            setOf(inputOtherApp.key, inputPrivateApp.key),
            actualApps.mapTo(mutableSetOf()) { appShortcuts ->
                appShortcuts.app.key
            }
        )
    }

    @Test
    fun `offers Shortcuts for the package representative only`() = runTest {
        val inputRepresentative: App = createApp(name = "Example", packageName = "com.example.app")
        val inputOtherActivity: App = inputRepresentative.copy(
            componentName = ComponentName("com.example.app", "com.example.app.SettingsActivity"),
            isPackageRepresentative = false
        )
        val source: IShortcutPickerSourceImpl = IShortcutPickerSourceImpl(
            shortcutSource = shortcutSource,
            launchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputRepresentative, inputOtherActivity))
        )

        val actualApps: List<AppShortcuts> = source.appsWithShortcuts().first()

        assertEquals(listOf(inputRepresentative.key), actualApps.map { appShortcuts -> appShortcuts.app.key })
    }
}
