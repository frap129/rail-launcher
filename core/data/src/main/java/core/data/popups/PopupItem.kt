package core.data.popups

import core.data.launchables.model.Launchable as LaunchableModel
import core.data.shortcuts.ShortcutLaunchable

/** One Launchable or Shortcut shown in a Pop-up. */
sealed interface PopupItem {
    val launchable: LaunchableModel

    /** An App or Rail Launchable the user added to the Pop-up. */
    data class Launchable(override val launchable: LaunchableModel) : PopupItem

    /** A Shortcut the user added to the Pop-up. */
    data class Shortcut(override val launchable: ShortcutLaunchable, val isEnabled: Boolean) : PopupItem
}
