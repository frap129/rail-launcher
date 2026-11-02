package core.data.shortcuts

import core.data.apps.model.AppProfile
import core.data.prefs.withoutTapTargets
import core.data.room.dao.FavoriteDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.StandaloneShortcutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IStandaloneShortcutRepositoryImpl(
    private val source: IStandaloneShortcutSource,
    private val standaloneShortcutDao: StandaloneShortcutDao,
    private val favoriteDao: FavoriteDao,
    private val headerSettingsDao: HeaderSettingsDao,
    private val shortcutPinCoordinator: IShortcutPinCoordinator
) : IStandaloneShortcutRepository {
    override val shortcuts: Flow<List<ShortcutLaunchable>> = source.shortcuts.map { shortcuts -> shortcuts.launchables }

    override suspend fun add(key: ShortcutKey, accept: suspend () -> Boolean): Boolean =
        shortcutPinCoordinator.runUnderPinLock(affectedProfiles = setOf(key.toAppProfile())) {
            val formattedKey: String = key.format()
            if (standaloneShortcutDao.exists(key = formattedKey)) {
                true
            } else if (!accept()) {
                false
            } else {
                standaloneShortcutDao.inTransaction {
                    standaloneShortcutDao.insert(
                        StandaloneShortcutRecord(key = formattedKey, addedAt = System.currentTimeMillis())
                    )
                    favoriteDao.add(key = formattedKey)
                }
                true
            }
        }

    override suspend fun remove(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        reapplyPinsAround(keys = keys) {
            standaloneShortcutDao.inTransaction {
                standaloneShortcutDao.delete(keys = keys)
                favoriteDao.remove(keys = keys)
                headerSettingsDao.updateIfChanged { settings -> settings.withoutTapTargets(keys = keys.toSet()) }
            }
        }
    }

    override suspend fun delete(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        reapplyPinsAround(keys = keys) {
            standaloneShortcutDao.delete(keys = keys)
        }
    }

    private suspend fun reapplyPinsAround(keys: List<String>, block: suspend () -> Unit) {
        val affectedProfiles: Set<AppProfile> = keys.mapNotNullTo(mutableSetOf()) { key ->
            ShortcutKey.parse(key)?.toAppProfile()
        }
        shortcutPinCoordinator.runUnderPinLock(affectedProfiles = affectedProfiles) { block() }
    }
}
