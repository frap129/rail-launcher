package core.popups

import core.data.folders.Folder
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
    FolderEmpty,
    ShortcutsUnavailable,
    ProfilePaused
}

/** Everything shown inside a Pop-up. */
data class PopupContent(
    val header: PopupHeader,
    val shortcuts: List<ShortcutLaunchable>,
    val items: List<PopupItem>,
    val emptyReason: PopupEmptyReason
)

/** Turns shortcut and item results into the content of [owner]'s Pop-up. */
object PopupContentResolver {
    fun resolve(owner: Launchable, shortcuts: ShortcutsResult, hiddenShortcutIds: Set<String>, items: List<PopupItem>): PopupContent {
        val effectiveShortcuts: ShortcutsResult = if (owner is Folder) ShortcutsResult.Available(shortcuts = emptyList()) else shortcuts
        val shownShortcuts: List<ShortcutLaunchable> = when (effectiveShortcuts) {
            is ShortcutsResult.Available ->
                effectiveShortcuts.shortcuts.filterNot { shortcut -> shortcut.shortcutId in hiddenShortcutIds }

            ShortcutsResult.Unavailable, ShortcutsResult.ProfilePaused -> emptyList()
        }
        val shownItems: List<PopupItem> = items.filterNot { item -> item is PopupItem.Shortcut && !item.isEnabled }
        return PopupContent(
            header = PopupHeader(name = owner.name, icon = owner.icon),
            shortcuts = shownShortcuts,
            items = shownItems,
            emptyReason = emptyReason(
                owner = owner,
                shortcuts = effectiveShortcuts,
                shownShortcuts = shownShortcuts,
                items = shownItems
            )
        )
    }

    private fun emptyReason(
        owner: Launchable,
        shortcuts: ShortcutsResult,
        shownShortcuts: List<ShortcutLaunchable>,
        items: List<PopupItem>
    ): PopupEmptyReason = when {
        items.isNotEmpty() -> PopupEmptyReason.None
        shortcuts is ShortcutsResult.Available && shownShortcuts.isNotEmpty() -> PopupEmptyReason.None
        owner is Folder -> PopupEmptyReason.FolderEmpty
        shortcuts is ShortcutsResult.Available -> PopupEmptyReason.Empty
        shortcuts is ShortcutsResult.Unavailable -> PopupEmptyReason.ShortcutsUnavailable
        else -> PopupEmptyReason.ProfilePaused
    }
}
