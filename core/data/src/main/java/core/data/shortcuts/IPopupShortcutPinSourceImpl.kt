package core.data.shortcuts

import core.data.apps.model.AppProfile
import core.data.room.dao.PopupDao

/** Reads every stored Pop-up Shortcut item through [popupDao]. */
class IPopupShortcutPinSourceImpl(private val popupDao: PopupDao) : IPopupShortcutPinSource {
    override suspend fun pinSets(): Map<AppProfile, Set<String>> =
        popupDao.getAllItems().map { record -> record.itemKey }.toShortcutPinSets()
}
