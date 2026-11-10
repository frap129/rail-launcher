package core.data.cleanup

import core.data.folders.Folder
import core.data.popups.IPopupRepository
import core.data.prefs.HeaderTapSetting
import core.data.prefs.IPreferencesRepository
import core.data.prefs.withoutTapTargets
import core.data.room.dao.FavoriteDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.PopupDao
import core.data.room.dao.SearchHistoryDao
import core.data.room.dao.StandaloneShortcutDao
import core.data.runCleanup
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.widgets.IWidgetAreaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The customizations stored for Launchable keys. */
class LaunchableCustomizationCleanupRow(private val preferencesRepository: IPreferencesRepository) : IStaleKeyRow {
    override val keys: Flow<Set<String>> = preferencesRepository.launchableCustomizations.map { customizations ->
        customizations.mapTo(mutableSetOf()) { customization -> customization.key }
    }

    override suspend fun delete(keys: Set<String>) {
        preferencesRepository.removeLaunchableCustomizations(keys = keys.toList())
    }
}

/** The Favorites stored for Launchable keys. */
class FavoriteCleanupRow(private val favoriteDao: FavoriteDao) : IStaleKeyRow {
    override val keys: Flow<Set<String>> = favoriteDao.observeAll().map { records ->
        records.mapTo(mutableSetOf()) { record -> record.key }
    }

    override suspend fun delete(keys: Set<String>) {
        favoriteDao.remove(keys = keys.toList())
    }
}

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
        (hiddenOwnerKeys + itemRecords.map { record -> record.ownerKey } + widgetOwnerKeys).toSet()
    }

    override suspend fun delete(keys: Set<String>) {
        for (key in keys) {
            runCleanup(message = "Failed to remove a stale Pop-up owner") {
                popupRepository.deleteOwner(ownerKey = key)
            }
        }
    }
}

/** The Pop-up items stored for Launchable and Shortcut keys. */
class PopupItemCleanupRow(private val popupDao: PopupDao, private val popupRepository: IPopupRepository) : IStaleKeyRow {
    override val keys: Flow<Set<String>> = popupDao.observeAllItems().map { records ->
        records.mapTo(mutableSetOf()) { record -> record.itemKey }
    }

    override suspend fun delete(keys: Set<String>) {
        popupRepository.deleteItemsByKey(itemKeys = keys.toList())
    }
}

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
class SearchHistoryCleanupRow(private val searchHistoryDao: SearchHistoryDao) : IStaleKeyRow {
    override val keys: Flow<Set<String>> = searchHistoryDao.observeAll().map { records ->
        records.mapTo(mutableSetOf()) { record -> record.key }
    }

    override suspend fun delete(keys: Set<String>) {
        searchHistoryDao.remove(keys = keys.toList())
    }
}

/** The Standalone shortcuts stored for Shortcut keys. */
class StandaloneShortcutCleanupRow(
    private val standaloneShortcutDao: StandaloneShortcutDao,
    private val standaloneShortcutRepository: IStandaloneShortcutRepository
) : IStaleKeyRow {
    override val keys: Flow<Set<String>> = standaloneShortcutDao.observeAll().map { records ->
        records.mapTo(mutableSetOf()) { record -> record.key }
    }

    override suspend fun delete(keys: Set<String>) {
        standaloneShortcutRepository.delete(keys = keys.toList())
    }
}
