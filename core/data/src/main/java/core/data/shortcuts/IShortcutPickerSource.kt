package core.data.shortcuts

import core.data.apps.model.App
import kotlinx.coroutines.flow.Flow

/** A package representative and what its Shortcuts currently look like. */
data class AppShortcuts(val app: App, val result: ShortcutsResult)

/** Lists the Shortcuts every App publishes for the Shortcut picker. */
interface IShortcutPickerSource {
    /** Every package representative in the launchable list with its Shortcuts. Only queries while collected. */
    fun appsWithShortcuts(): Flow<List<AppShortcuts>>
}
