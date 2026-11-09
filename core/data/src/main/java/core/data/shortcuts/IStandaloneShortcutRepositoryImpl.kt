package core.data.shortcuts

import core.data.apps.model.AppProfile
import core.data.prefs.IPreferencesRepository
import core.data.prefs.withoutTapTargets
import core.data.room.TransactionEffects
import core.data.room.dao.FavoriteDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.StandaloneShortcutDao
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class IStandaloneShortcutRepositoryImpl(
    private val source: IStandaloneShortcutSource,
    private val standaloneShortcutDao: StandaloneShortcutDao,
    private val favoriteDao: FavoriteDao,
    private val headerSettingsDao: HeaderSettingsDao,
    private val preferencesRepository: IPreferencesRepository,
    private val shortcutPinCoordinator: IShortcutPinCoordinator
) : IStandaloneShortcutRepository {
    override val shortcuts: Flow<List<ShortcutLaunchable>> = source.shortcuts.map { shortcuts -> shortcuts.launchables }

    override suspend fun add(key: ShortcutKey, accept: suspend () -> Boolean): Boolean =
        shortcutPinCoordinator.runUnderPinLock(affectedProfiles = setOf(key.toAppProfile())) {
            if (!accept()) {
                return@runUnderPinLock false
            }
            // The system accepted the pin, so store it even if the requesting dialog is gone.
            withContext(NonCancellable) {
                val formattedKey: String = key.format()
                standaloneShortcutDao.inTransaction {
                    standaloneShortcutDao.insert(
                        StandaloneShortcutRecord(key = formattedKey, addedAt = System.currentTimeMillis())
                    )
                    favoriteDao.add(key = formattedKey)
                }
            }
            true
        }

    override suspend fun remove(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        reapplyPinsAround(keys = keys) {
            val effects: TransactionEffects = TransactionEffects()
            standaloneShortcutDao.inTransaction {
                withContext(effects) {
                    standaloneShortcutDao.delete(keys = keys)
                    favoriteDao.remove(keys = keys)
                    headerSettingsDao.updateIfChanged { settings -> settings.withoutTapTargets(keys = keys.toSet()) }
                    preferencesRepository.removeLaunchableCustomizations(keys = keys)
                }
            }
            effects.run()
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
