package core.data.search

import kotlinx.coroutines.flow.Flow

/** A source of the Shortcuts Search may match. */
interface IShortcutSearchSource {
    /**
     * Every Shortcut published by a Package representative, with the name of the App that
     * publishes it and whether it is hidden from that App's Pop-up. Only queries while collected.
     */
    val shortcuts: Flow<List<SearchShortcut>>
}
