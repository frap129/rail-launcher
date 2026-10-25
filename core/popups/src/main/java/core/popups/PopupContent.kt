package core.popups

import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult

/** The owner shown at the top of a Pop-up. */
data class PopupHeader(val name: String, val icon: Icon)

/** Why a Pop-up has no shortcut or item rows. */
enum class PopupEmptyReason {
    None,
    Empty,
    ShortcutsUnavailable,
    ProfilePaused
}

/** Everything shown inside an App Pop-up. */
data class PopupContent(
    val header: PopupHeader,
    val shortcuts: List<ShortcutLaunchable>,
    val items: List<PopupItem>,
    val emptyReason: PopupEmptyReason
)

/** Turns shortcut and item results into the content of [owner]'s Pop-up. */
object PopupContentResolver {
    fun resolve(owner: Launchable, shortcuts: ShortcutsResult, hiddenShortcutIds: Set<String>, items: List<PopupItem>): PopupContent {
        val shownShortcuts: List<ShortcutLaunchable> = when (shortcuts) {
            is ShortcutsResult.Available -> shortcuts.shortcuts.filterNot { shortcut -> shortcut.shortcutId in hiddenShortcutIds }
            ShortcutsResult.Unavailable, ShortcutsResult.ProfilePaused -> emptyList()
        }
        val shownItems: List<PopupItem> = items.filterNot { item -> item is PopupItem.Shortcut && !item.isEnabled }
        return PopupContent(
            header = PopupHeader(name = owner.name, icon = owner.icon),
            shortcuts = shownShortcuts,
            items = shownItems,
            emptyReason = emptyReason(shortcuts = shortcuts, shownShortcuts = shownShortcuts, items = shownItems)
        )
    }

    private fun emptyReason(
        shortcuts: ShortcutsResult,
        shownShortcuts: List<ShortcutLaunchable>,
        items: List<PopupItem>
    ): PopupEmptyReason = when {
        items.isNotEmpty() -> PopupEmptyReason.None
        shortcuts is ShortcutsResult.Available && shownShortcuts.isNotEmpty() -> PopupEmptyReason.None
        shortcuts is ShortcutsResult.Available -> PopupEmptyReason.Empty
        shortcuts is ShortcutsResult.Unavailable -> PopupEmptyReason.ShortcutsUnavailable
        else -> PopupEmptyReason.ProfilePaused
    }
}
