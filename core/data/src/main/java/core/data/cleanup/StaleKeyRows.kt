package core.data.cleanup

import core.data.favorites.FavoriteRecord
import core.data.folders.Folder
import core.data.launches.LaunchCountRecord
import core.data.popups.IPopupRepository
import core.data.popups.PopupItemRecord
import core.data.prefs.HeaderTapSetting
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.withoutTapTargets
import core.data.room.dao.FavoriteDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.LaunchCountDao
import core.data.room.dao.PopupDao
import core.data.room.dao.SearchHistoryDao
import core.data.room.dao.StandaloneShortcutDao
import core.data.room.dao.StoppedSuggestionDao
import core.data.room.dao.mapToKeySet
import core.data.runContained
import core.data.search.SearchHistoryRecord
import core.data.search.StoppedSuggestionRecord
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.StandaloneShortcutRecord
import core.data.widgets.IWidgetAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A cleanup row over [keys] that removes the stale ones with [deleteKeys]. */
class KeyedCleanupRow(override val keys: Flow<Set<String>>, private val deleteKeys: suspend (Set<String>) -> Unit) : IStaleKeyRow {
    override suspend fun delete(keys: Set<String>) {
        deleteKeys(keys)
    }
}

/** The customizations stored for Launchable keys. */
fun launchableCustomizationCleanupRow(preferencesRepository: IPreferencesRepository): IStaleKeyRow = KeyedCleanupRow(
    keys = preferencesRepository.launchableCustomizations.mapToKeySet(LaunchableCustomization::key),
    deleteKeys = { keys -> preferencesRepository.removeLaunchableCustomizations(keys = keys.toList()) }
)

/** The Favorites stored for Launchable keys. */
fun favoriteCleanupRow(favoriteDao: FavoriteDao): IStaleKeyRow = KeyedCleanupRow(
    keys = favoriteDao.observeAll().mapToKeySet(FavoriteRecord::key),
    deleteKeys = { keys -> favoriteDao.remove(keys = keys.toList()) }
)

/** The Pop-up owners stored for Launchable keys. */
class PopupOwnerCleanupRow(
    private val popupDao: PopupDao,
    private val widgetAreaRepository: IWidgetAreaRepository,
    private val popupRepository: IPopupRepository
) : IStaleKeyRow {
    override val keys: Flow<Set<String>> = combine(
        popupDao.observeAllOwnerKeys(),
        popupDao.observeAllItems(),
        widgetAreaRepository.popupOwnerKeys
    ) { hiddenOwnerKeys, itemRecords, widgetOwnerKeys ->
        (hiddenOwnerKeys + itemRecords.map(PopupItemRecord::ownerKey) + widgetOwnerKeys).toSet()
    }

    override suspend fun delete(keys: Set<String>) {
        for (key in keys) {
            runContained(message = "Failed to remove a stale Pop-up owner") {
                popupRepository.deleteOwner(ownerKey = key)
            }
        }
    }
}

/** The Pop-up items stored for Launchable and Shortcut keys. */
fun popupItemCleanupRow(popupDao: PopupDao, popupRepository: IPopupRepository): IStaleKeyRow = KeyedCleanupRow(
    keys = popupDao.observeAllItems().mapToKeySet(PopupItemRecord::itemKey),
    deleteKeys = { keys -> popupRepository.deleteItemsByKey(itemKeys = keys.toList()) }
)

/** The Header tap actions stored for Launchable keys. */
class HeaderTapTargetCleanupRow(private val headerSettingsDao: HeaderSettingsDao) : IStaleKeyRow {
    override val keys: Flow<Set<String>> = headerSettingsDao.observe().map { settings ->
        buildSet {
            (settings.timeTapTarget as? HeaderTapSetting.Launchable)?.let { target -> add(target.key) }
            (settings.dateTapTarget as? HeaderTapSetting.Launchable)?.let { target -> add(target.key) }
        }
    }

    override suspend fun delete(keys: Set<String>) {
        headerSettingsDao.updateIfChanged { settings -> settings.withoutTapTargets(keys = keys) }
    }

    override fun isStale(state: StoredKeyState): Boolean =
        state is StoredKeyState.Stale || (state is StoredKeyState.Present && state.launchable is Folder)
}

/** The Search history stored for Launchable keys. */
fun searchHistoryCleanupRow(searchHistoryDao: SearchHistoryDao): IStaleKeyRow = KeyedCleanupRow(
    keys = searchHistoryDao.observeAll().mapToKeySet(SearchHistoryRecord::key),
    deleteKeys = { keys -> searchHistoryDao.remove(keys = keys.toList()) }
)

/** The Stop suggesting keys stored for Launchable keys. */
fun stoppedSuggestionCleanupRow(stoppedSuggestionDao: StoppedSuggestionDao): IStaleKeyRow = KeyedCleanupRow(
    keys = stoppedSuggestionDao.observeAll().mapToKeySet(StoppedSuggestionRecord::key),
    deleteKeys = { keys -> stoppedSuggestionDao.remove(keys = keys.toList()) }
)

/** The launch counts stored for Launchable and Shortcut keys. */
fun launchCountCleanupRow(launchCountDao: LaunchCountDao): IStaleKeyRow = KeyedCleanupRow(
    keys = launchCountDao.observeAll().mapToKeySet(LaunchCountRecord::key),
    deleteKeys = { keys -> launchCountDao.remove(keys = keys.toList()) }
)

/** The Standalone shortcuts stored for Shortcut keys. */
fun standaloneShortcutCleanupRow(
    standaloneShortcutDao: StandaloneShortcutDao,
    standaloneShortcutRepository: IStandaloneShortcutRepository
): IStaleKeyRow = KeyedCleanupRow(
    keys = standaloneShortcutDao.observeAll().mapToKeySet(StandaloneShortcutRecord::key),
    deleteKeys = { keys -> standaloneShortcutRepository.delete(keys = keys.toList()) }
)
