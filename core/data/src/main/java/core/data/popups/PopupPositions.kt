package core.data.popups

import core.data.room.dao.PopupDao

/** Renumbers [ownerKey]'s Pop-up items in their current order so positions stay unique. */
internal suspend fun normalizeItemPositions(popupDao: PopupDao, ownerKey: String) {
    val records: List<PopupItemRecord> = popupDao.getItems(ownerKey).sortedBy { record -> record.position }
    val normalized: List<PopupItemRecord> = records.mapIndexed { index, record -> record.copy(position = index) }
    if (normalized != records) {
        popupDao.updatePositions(normalized)
    }
}
