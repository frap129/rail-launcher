package core.data.search

import core.data.shortcuts.ShortcutLaunchable

/**
 * A Shortcut the Shortcut search source can offer, with the name of the App that publishes it and
 * whether the user hid it from that App's Pop-up.
 */
data class SearchShortcut(val shortcut: ShortcutLaunchable, val publisherName: String, val isHidden: Boolean)
