package core.data.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import app.cash.turbine.test
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.FakeLaunchableRepository
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.TEST_SDK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLauncherApps

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IShortcutSourceImplTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val shadowLauncherApps: ShadowLauncherApps = Shadow.extract(launcherApps)
    private val profile: UserHandle = Process.myUserHandle()
    private val source: IShortcutSourceImpl = IShortcutSourceImpl(
        context = context,
        launchableRepository = FakeLaunchableRepository(groups = emptyList())
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
