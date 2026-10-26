package core.popups

import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.notifications.NotificationSnapshot
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult

/** The owner shown at the top of a Pop-up. */
data class PopupHeader(val name: String, val icon: Icon)

/** Why a Pop-up has no shortcut or item rows. */
enum class PopupEmptyReason {
    None,
    Empty,
    FolderEmpty,
    ShortcutsUnavailable,
    ProfilePaused
}

/** Everything shown inside a Pop-up. */
data class PopupContent(
    val header: PopupHeader,
    val shortcuts: List<ShortcutLaunchable>,
    val items: List<PopupItem>,
    val ownerNotifications: List<NotificationSnapshot>,
    /** The notifications shown under each added item's row, keyed by that item's Launchable key. */
    val itemNotifications: Map<String, List<NotificationSnapshot>>,
    /**
     * Whether shortcuts or an App's added items keep the card open after its notifications empty.
     * A Folder's member rows do not count as added items, so a Folder showing only its members'
     * notifications closes when the last one goes.
     */
    val keepsOpenWithoutNotifications: Boolean,
    val emptyReason: PopupEmptyReason
)

/** Turns shortcut, item and notification results into the content of [owner]'s Pop-up. */
object PopupContentResolver {
    fun resolve(
        owner: Launchable,
        shortcuts: ShortcutsResult,
        hiddenShortcutIds: Set<String>,
        items: List<PopupItem>,
        notifications: List<NotificationSnapshot> = emptyList()
    ): PopupContent {
        val effectiveShortcuts: ShortcutsResult = if (owner is Folder) ShortcutsResult.Available(shortcuts = emptyList()) else shortcuts
        val shownShortcuts: List<ShortcutLaunchable> = when (effectiveShortcuts) {
            is ShortcutsResult.Available ->
                effectiveShortcuts.shortcuts.filterNot { shortcut -> shortcut.shortcutId in hiddenShortcutIds }

            ShortcutsResult.Unavailable, ShortcutsResult.ProfilePaused -> emptyList()
        }
        val shownItems: List<PopupItem> = items.filterNot { item -> item is PopupItem.Shortcut && !item.isEnabled }
        val shownNotifications: List<NotificationSnapshot> = notificationsFor(owner = owner, notifications = notifications)
        val hasShortcuts: Boolean = shownShortcuts.isNotEmpty()
        val hasItems: Boolean = shownItems.isNotEmpty()
        return PopupContent(
            header = PopupHeader(name = owner.name, icon = owner.icon),
            shortcuts = shownShortcuts,
            items = shownItems,
            ownerNotifications = shownNotifications,
            itemNotifications = itemNotifications(items = shownItems, notifications = notifications),
            keepsOpenWithoutNotifications = hasShortcuts || (owner !is Folder && hasItems),
            emptyReason = emptyReason(
                owner = owner,
                shortcuts = effectiveShortcuts,
                hasShortcuts = hasShortcuts,
                hasItems = hasItems,
                hasNotifications = shownNotifications.isNotEmpty()
            )
        )
    }

    /** The App's notifications, in the source's newest-first order. */
    fun notificationsFor(owner: Launchable, notifications: List<NotificationSnapshot>): List<NotificationSnapshot> {
        val app: App = owner as? App ?: return emptyList()
        val appProfile: AppProfile = AppProfile(packageName = app.packageName, profile = app.profile)
        return notifications.filter { snapshot -> snapshot.owner == appProfile }
    }

    /** The notifications shown under each item's row, keyed by that item's Launchable key. */
    fun itemNotifications(items: List<PopupItem>, notifications: List<NotificationSnapshot>): Map<String, List<NotificationSnapshot>> =
        items.mapNotNull { item ->
            val rows: List<NotificationSnapshot> = notificationsFor(owner = item.launchable, notifications = notifications)
            if (rows.isEmpty()) null else item.launchable.key to rows
        }.toMap()

    private fun emptyReason(
        owner: Launchable,
        shortcuts: ShortcutsResult,
        hasShortcuts: Boolean,
        hasItems: Boolean,
        hasNotifications: Boolean
    ): PopupEmptyReason = when {
        hasNotifications -> PopupEmptyReason.None
        hasItems -> PopupEmptyReason.None
        shortcuts is ShortcutsResult.Available && hasShortcuts -> PopupEmptyReason.None
        owner is Folder -> PopupEmptyReason.FolderEmpty
        shortcuts is ShortcutsResult.Available -> PopupEmptyReason.Empty
        shortcuts is ShortcutsResult.Unavailable -> PopupEmptyReason.ShortcutsUnavailable
        else -> PopupEmptyReason.ProfilePaused
    }
}
