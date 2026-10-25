package core.popups

import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult

/** The owner shown at the top of a Pop-up. */
data class PopupHeader(val name: String, val icon: Icon)

/** Why a Pop-up has no shortcut rows. */
enum class PopupEmptyReason {
    None,
    Empty,
    ShortcutsUnavailable,
    ProfilePaused
}

/** Everything shown inside an App Pop-up. */
data class PopupContent(val header: PopupHeader, val shortcuts: List<ShortcutLaunchable>, val emptyReason: PopupEmptyReason)

/** Turns a shortcut result into the content of [owner]'s Pop-up. */
object PopupContentResolver {
    fun resolve(owner: Launchable, shortcuts: ShortcutsResult, hiddenShortcutIds: Set<String>): PopupContent {
        val header: PopupHeader = PopupHeader(name = owner.name, icon = owner.icon)
        return when (shortcuts) {
            is ShortcutsResult.Available -> {
                val shownShortcuts: List<ShortcutLaunchable> =
                    shortcuts.shortcuts.filterNot { shortcut -> shortcut.shortcutId in hiddenShortcutIds }
                PopupContent(
                    header = header,
                    shortcuts = shownShortcuts,
                    emptyReason = if (shownShortcuts.isEmpty()) PopupEmptyReason.Empty else PopupEmptyReason.None
                )
            }

            ShortcutsResult.Unavailable -> PopupContent(
                header = header,
                shortcuts = emptyList(),
                emptyReason = PopupEmptyReason.ShortcutsUnavailable
            )

            ShortcutsResult.ProfilePaused -> PopupContent(
                header = header,
                shortcuts = emptyList(),
                emptyReason = PopupEmptyReason.ProfilePaused
            )
        }
    }
}
