package core.popups

import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.launchables.model.RailLaunchable
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PACKAGE_NAME: String = "com.example.app"
private const val OWNER_NAME: String = "Example"
private const val FIRST_SHORTCUT_ID: String = "first"
private const val SECOND_SHORTCUT_ID: String = "second"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupContentResolverTest {
    private val profile: UserHandle = UserHandle.getUserHandleForUid(0)
    private val inputOwner: RailLaunchable = RailLaunchable(
        defaultName = OWNER_NAME,
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = "rail-launcher://owner"
    )
    private val inputFirstShortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID)
    private val inputSecondShortcut: ShortcutLaunchable = createShortcut(id = SECOND_SHORTCUT_ID)

    @Test
    fun `shows the owner as the header`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet()
        )
        assertEquals(PopupHeader(name = OWNER_NAME, icon = inputOwner.defaultIcon), actualContent.header)
    }

    @Test
    fun `passes the shortcuts through in their source order`() {
        val inputShortcuts: List<ShortcutLaunchable> = listOf(inputSecondShortcut, inputFirstShortcut)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = inputShortcuts),
            hiddenShortcutIds = emptySet()
        )
        assertEquals(inputShortcuts, actualContent.shortcuts)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `filters out hidden shortcuts`() {
        val inputShortcuts: List<ShortcutLaunchable> = listOf(inputFirstShortcut, inputSecondShortcut)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = inputShortcuts),
            hiddenShortcutIds = setOf(FIRST_SHORTCUT_ID)
        )
        assertEquals(listOf(inputSecondShortcut), actualContent.shortcuts)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `reports no shortcuts when the available list is empty`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet()
        )
        assertEquals(PopupEmptyReason.Empty, actualContent.emptyReason)
    }

    @Test
    fun `reports no shortcuts when every shortcut is hidden`() {
        val inputShortcuts: List<ShortcutLaunchable> = listOf(inputFirstShortcut, inputSecondShortcut)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = inputShortcuts),
            hiddenShortcutIds = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)
        )
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
        assertEquals(PopupEmptyReason.Empty, actualContent.emptyReason)
    }

    @Test
    fun `reports unavailable shortcuts when Rail is not the default launcher`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Unavailable,
            hiddenShortcutIds = emptySet()
        )
        assertEquals(PopupEmptyReason.ShortcutsUnavailable, actualContent.emptyReason)
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
    }

    @Test
    fun `reports a paused profile`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.ProfilePaused,
            hiddenShortcutIds = emptySet()
        )
        assertEquals(PopupEmptyReason.ProfilePaused, actualContent.emptyReason)
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
    }

    private fun createShortcut(id: String): ShortcutLaunchable = ShortcutLaunchable(
        packageName = PACKAGE_NAME,
        shortcutId = id,
        profile = profile,
        shortLabel = "Short $id",
        longLabel = "Long $id"
    )
}
