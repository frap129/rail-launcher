package core.data.popups

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.RailLaunchable
import core.data.notifications.NotificationSnapshot
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_NOTIFICATION_PACKAGE_NAME
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createNotificationAction
import core.data.testing.createNotificationProfile
import core.data.testing.createNotificationSnapshot
import core.data.testing.createOtherNotificationProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val OWNER_NAME: String = "Example"
private const val FIRST_SHORTCUT_ID: String = "first"
private const val SECOND_SHORTCUT_ID: String = "second"
private const val SECOND_PACKAGE_NAME: String = "com.example.second"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupContentResolverTest {
    private val profile: UserHandle = createNotificationProfile()
    private val inputApp: App = App(
        defaultName = OWNER_NAME,
        profile = profile,
        componentName = ComponentName(
            TEST_NOTIFICATION_PACKAGE_NAME,
            "$TEST_NOTIFICATION_PACKAGE_NAME.MainActivity"
        )
    )
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
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertEquals(PopupHeader(name = OWNER_NAME, icon = inputOwner.defaultIcon), actualContent.header)
    }

    @Test
    fun `passes the shortcuts through in their source order`() {
        val inputShortcuts: List<ShortcutLaunchable> = listOf(inputSecondShortcut, inputFirstShortcut)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = inputShortcuts),
            hiddenShortcutIds = emptySet(),
            items = emptyList()
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
            hiddenShortcutIds = setOf(FIRST_SHORTCUT_ID),
            items = emptyList()
        )
        assertEquals(listOf(inputSecondShortcut), actualContent.shortcuts)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `shows added items after shortcuts`() {
        val firstItem: RailLaunchable = createItem(name = "First item", deeplink = "rail://first")
        val secondItem: RailLaunchable = createItem(name = "Second item", deeplink = "rail://second")
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = listOf(inputFirstShortcut)),
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(firstItem), PopupItem.Launchable(secondItem))
        )
        assertEquals(listOf(inputFirstShortcut), actualContent.shortcuts)
        assertEquals(listOf(PopupItem.Launchable(firstItem), PopupItem.Launchable(secondItem)), actualContent.items)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `shows added items when shortcuts are unavailable`() {
        val item: RailLaunchable = createItem(name = "Item", deeplink = "rail://item")
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Unavailable,
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(item))
        )
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
        assertEquals(listOf(PopupItem.Launchable(item)), actualContent.items)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `shows an enabled shortcut item and hides a disabled one`() {
        val inputEnabledItem: PopupItem.Shortcut = PopupItem.Shortcut(launchable = inputFirstShortcut, isEnabled = true)
        val inputDisabledItem: PopupItem.Shortcut = PopupItem.Shortcut(launchable = inputSecondShortcut, isEnabled = false)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(inputEnabledItem, inputDisabledItem)
        )
        assertEquals(listOf(inputEnabledItem), actualContent.items)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `reports empty only when there are no shortcuts and no items`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertEquals(PopupEmptyReason.Empty, actualContent.emptyReason)
    }

    @Test
    fun `reports no shortcuts when the available list is empty`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertEquals(PopupEmptyReason.Empty, actualContent.emptyReason)
    }

    @Test
    fun `reports no shortcuts when every shortcut is hidden`() {
        val inputShortcuts: List<ShortcutLaunchable> = listOf(inputFirstShortcut, inputSecondShortcut)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = inputShortcuts),
            hiddenShortcutIds = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID),
            items = emptyList()
        )
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
        assertEquals(PopupEmptyReason.Empty, actualContent.emptyReason)
    }

    @Test
    fun `reports unavailable shortcuts when Rail is not the default launcher`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Unavailable,
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertEquals(PopupEmptyReason.ShortcutsUnavailable, actualContent.emptyReason)
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
    }

    @Test
    fun `reports a paused profile`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.ProfilePaused,
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertEquals(PopupEmptyReason.ProfilePaused, actualContent.emptyReason)
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
    }

    @Test
    fun `a Folder never shows shortcuts`() {
        val folder: Folder = Folder(folderId = 1L)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = folder,
            shortcuts = ShortcutsResult.Available(shortcuts = listOf(inputFirstShortcut)),
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertEquals(emptyList<ShortcutLaunchable>(), actualContent.shortcuts)
        assertEquals(PopupEmptyReason.FolderEmpty, actualContent.emptyReason)
    }

    @Test
    fun `an empty Folder reports Folder empty`() {
        val folder: Folder = Folder(folderId = 1L)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = folder,
            shortcuts = ShortcutsResult.Unavailable,
            hiddenShortcutIds = emptySet(),
            items = emptyList()
        )
        assertEquals(PopupHeader(name = Folder.DEFAULT_NAME, icon = folder.icon), actualContent.header)
        assertEquals(PopupEmptyReason.FolderEmpty, actualContent.emptyReason)
    }

    @Test
    fun `a Folder with items is not empty`() {
        val folder: Folder = Folder(folderId = 1L)
        val item: RailLaunchable = createItem(name = "Item", deeplink = "rail://item")
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = folder,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(item))
        )
        assertEquals(listOf(PopupItem.Launchable(item)), actualContent.items)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `shows only the App's own notifications and its other content`() {
        val inputOwnNotification: NotificationSnapshot = createNotificationSnapshot(key = "own")
        val inputOtherPackage: NotificationSnapshot = createNotificationSnapshot(key = "other-package", packageName = "com.example.other")
        val inputOtherProfile: NotificationSnapshot = createNotificationSnapshot(
            key = "other-profile",
            profile = createOtherNotificationProfile()
        )
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputApp,
            shortcuts = ShortcutsResult.Available(shortcuts = listOf(inputFirstShortcut)),
            hiddenShortcutIds = emptySet(),
            items = emptyList(),
            notifications = listOf(inputOwnNotification, inputOtherPackage, inputOtherProfile)
        )
        assertEquals(listOf(inputOwnNotification), actualContent.ownerNotifications)
        assertEquals(listOf(inputFirstShortcut), actualContent.shortcuts)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `an App with only notifications is not empty`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputApp,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = emptyList(),
            notifications = listOf(createNotificationSnapshot())
        )
        assertEquals(1, actualContent.ownerNotifications.size)
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
    }

    @Test
    fun `a notification with big text or actions is expandable`() {
        val inputBigText: NotificationSnapshot = createNotificationSnapshot(key = "big-text", expandedBody = "Longer body")
        val inputAction: NotificationSnapshot = createNotificationSnapshot(
            key = "action",
            actions = listOf(createNotificationAction())
        )
        val inputPlain: NotificationSnapshot = createNotificationSnapshot(key = "plain", body = "")
        val actualRows: List<NotificationSnapshot> = PopupContentResolver.notificationsFor(
            owner = inputApp,
            notifications = listOf(inputBigText, inputAction, inputPlain)
        )
        assertEquals(listOf(true, true, false), actualRows.map { row -> row.isExpandable })
    }

    @Test
    fun `a Folder ignores notifications`() {
        val folder: Folder = Folder(folderId = 1L)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = folder,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = emptyList(),
            notifications = listOf(createNotificationSnapshot())
        )
        assertEquals(emptyList<NotificationSnapshot>(), actualContent.ownerNotifications)
        assertEquals(PopupEmptyReason.FolderEmpty, actualContent.emptyReason)
    }

    @Test
    fun `groups each Folder member's notifications under its row`() {
        val inputFolder: Folder = Folder(folderId = 1L)
        val inputSecondApp: App = createApp(name = "Second", packageName = SECOND_PACKAGE_NAME)
        val inputFirstNotification: NotificationSnapshot = createNotificationSnapshot(key = "first", postTime = 2_000L)
        val inputSecondNotification: NotificationSnapshot = createNotificationSnapshot(
            key = "second",
            packageName = SECOND_PACKAGE_NAME,
            postTime = 1_000L
        )
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputFolder,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(inputApp), PopupItem.Launchable(inputSecondApp)),
            notifications = listOf(inputFirstNotification, inputSecondNotification)
        )
        assertEquals(
            mapOf(
                inputApp.key to listOf(inputFirstNotification),
                inputSecondApp.key to listOf(inputSecondNotification)
            ),
            actualContent.itemNotifications
        )
        assertEquals(
            listOf(PopupItem.Launchable(inputApp), PopupItem.Launchable(inputSecondApp)),
            actualContent.items
        )
    }

    @Test
    fun `groups an added item's notifications under its row`() {
        val inputNotification: NotificationSnapshot = createNotificationSnapshot()
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(inputApp)),
            notifications = listOf(inputNotification)
        )
        assertEquals(mapOf(inputApp.key to listOf(inputNotification)), actualContent.itemNotifications)
    }

    @Test
    fun `a member without notifications has no entry beneath it`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(inputApp)),
            notifications = listOf(createNotificationSnapshot(packageName = SECOND_PACKAGE_NAME))
        )
        assertEquals(emptyMap<String, List<NotificationSnapshot>>(), actualContent.itemNotifications)
    }

    @Test
    fun `a member's notifications keep their newest-first order`() {
        val inputNewer: NotificationSnapshot = createNotificationSnapshot(key = "newer", postTime = 2_000L)
        val inputOlder: NotificationSnapshot = createNotificationSnapshot(key = "older", postTime = 1_000L)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(inputApp)),
            notifications = listOf(inputNewer, inputOlder)
        )
        assertEquals(
            listOf(inputNewer, inputOlder),
            actualContent.itemNotifications.getValue(inputApp.key)
        )
    }

    @Test
    fun `a Rail item never has notifications beneath it`() {
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(inputOwner)),
            notifications = listOf(createNotificationSnapshot())
        )
        assertEquals(emptyMap<String, List<NotificationSnapshot>>(), actualContent.itemNotifications)
    }

    @Test
    fun `a Shortcut item never has notifications beneath it`() {
        val inputShortcutItem: PopupItem.Shortcut = PopupItem.Shortcut(launchable = inputFirstShortcut, isEnabled = true)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputOwner,
            shortcuts = ShortcutsResult.Available(shortcuts = emptyList()),
            hiddenShortcutIds = emptySet(),
            items = listOf(inputShortcutItem),
            notifications = listOf(createNotificationSnapshot())
        )
        assertEquals(emptyMap<String, List<NotificationSnapshot>>(), actualContent.itemNotifications)
    }

    @Test
    fun `a Folder with a member that has notifications is not empty`() {
        val inputFolder: Folder = Folder(folderId = 1L)
        val actualContent: PopupContent = PopupContentResolver.resolve(
            owner = inputFolder,
            shortcuts = ShortcutsResult.Unavailable,
            hiddenShortcutIds = emptySet(),
            items = listOf(PopupItem.Launchable(inputApp)),
            notifications = listOf(createNotificationSnapshot())
        )
        assertEquals(PopupEmptyReason.None, actualContent.emptyReason)
        assertEquals(1, actualContent.itemNotifications.getValue(inputApp.key).size)
    }

    private fun createShortcut(id: String): ShortcutLaunchable = ShortcutLaunchable(
        packageName = TEST_NOTIFICATION_PACKAGE_NAME,
        shortcutId = id,
        profile = profile,
        shortLabel = "Short $id",
        longLabel = "Long $id"
    )

    private fun createItem(name: String, deeplink: String): RailLaunchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = deeplink
    )
}
