package core.data.search

import core.data.apps.model.AppProfile
import core.data.popups.IPopupRepository
import core.data.shortcuts.IShortcutPickerSource
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.shortcuts.shortcutsOrEmpty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Offers the Shortcuts [shortcutPickerSource] reads from every Package representative, marking
 * each with its publishing App and whether [popupRepository] records it as hidden.
 *
 * A Paused Profile keeps offering the last Shortcuts it published during this process, marked
 * with its publishing App's paused Profile, so Search greys them like its Apps. A Profile that
 * has not published Shortcuts in this process offers none while Paused. A Locked Private Space
 * offers none, because [shortcutPickerSource] only lists its Apps while it is Unlocked.
 */
class IShortcutSearchSourceImpl(private val shortcutPickerSource: IShortcutPickerSource, private val popupRepository: IPopupRepository) :
    IShortcutSearchSource {
    private val lastAvailableShortcuts: MutableMap<AppProfile, List<ShortcutLaunchable>> = mutableMapOf()

    override val shortcuts: Flow<List<SearchShortcut>> = combine(
        shortcutPickerSource.appsWithShortcuts(),
        popupRepository.hiddenShortcutsByOwner
    ) { apps, hiddenShortcutIdsByOwner ->
        apps.flatMap { appShortcuts ->
            val app = appShortcuts.app
            val owner: AppProfile = AppProfile(packageName = app.packageName, profile = app.profile)
            val result: ShortcutsResult = appShortcuts.result
            val publishedShortcuts: List<ShortcutLaunchable> = result.shortcutsOrEmpty()
            if (result is ShortcutsResult.Available) {
                lastAvailableShortcuts[owner] = publishedShortcuts
            }
            val offeredShortcuts: List<ShortcutLaunchable> = if (result is ShortcutsResult.ProfilePaused) {
                lastAvailableShortcuts[owner].orEmpty()
            } else {
                publishedShortcuts
            }
            val hiddenShortcutIds: Set<String> = hiddenShortcutIdsByOwner[app.key].orEmpty()
            offeredShortcuts.map { shortcut ->
                SearchShortcut(
                    shortcut = shortcut.copy(profileInfo = app.profileInfo),
                    publisherName = app.name,
                    isHidden = shortcut.shortcutId in hiddenShortcutIds
                )
            }
        }
    }
}
