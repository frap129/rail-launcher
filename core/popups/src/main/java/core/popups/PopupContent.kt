package core.popups

import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult

/** The owner shown at the top of a Pop-up. */
data class PopupHeader(val name: String, val icon: Icon)

/** One Launchable added to a Pop-up. */
sealed interface PopupItem {
    val launchable: core.data.launchables.model.Launchable

    /** An App or Rail Launchable the user added to the Pop-up. */
    data class Launchable(override val launchable: core.data.launchables.model.Launchable) : PopupItem
}

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
    fun resolve(owner: Launchable, shortcuts: ShortcutsResult, hiddenShortcutIds: Set<String>, items: List<Launchable>): PopupContent {
        val shownShortcuts: List<ShortcutLaunchable> = when (shortcuts) {
            is ShortcutsResult.Available -> shortcuts.shortcuts.filterNot { shortcut -> shortcut.shortcutId in hiddenShortcutIds }
            ShortcutsResult.Unavailable, ShortcutsResult.ProfilePaused -> emptyList()
        }
        val popupItems: List<PopupItem> = items.map { item -> PopupItem.Launchable(launchable = item) }
        return PopupContent(
            header = PopupHeader(name = owner.name, icon = owner.icon),
            shortcuts = shownShortcuts,
            items = popupItems,
            emptyReason = emptyReason(shortcuts = shortcuts, shownShortcuts = shownShortcuts, items = popupItems)
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
