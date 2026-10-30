package core.data.popups

import core.util.launchLogged
import kotlinx.coroutines.CoroutineScope

/** Removes [itemKey] from [ownerKey]'s Pop-up, logging a failure. */
fun CoroutineScope.removeAddedItem(popupRepository: IPopupRepository, ownerKey: String, itemKey: String) {
    launchLogged(message = "Failed to remove an item from its Pop-up") {
        popupRepository.removeItem(ownerKey = ownerKey, itemKey = itemKey)
    }
}
