package core.data.shortcuts

import core.data.room.dao.FavoriteDao
import core.data.room.dao.StandaloneShortcutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IStandaloneShortcutRepositoryImpl(
    private val source: IStandaloneShortcutSource,
    private val standaloneShortcutDao: StandaloneShortcutDao,
    private val favoriteDao: FavoriteDao,
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
}
