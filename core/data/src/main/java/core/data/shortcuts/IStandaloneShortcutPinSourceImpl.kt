package core.data.shortcuts

import core.data.apps.model.AppProfile
import core.data.room.dao.StandaloneShortcutDao

/** Reads every stored Standalone shortcut through [standaloneShortcutDao]. */
class IStandaloneShortcutPinSourceImpl(private val standaloneShortcutDao: StandaloneShortcutDao) : IStandaloneShortcutPinSource {
    override suspend fun pinSets(): Map<AppProfile, Set<String>> =
        standaloneShortcutDao.getAll().map { record -> record.key }.toShortcutPinSets()
}
