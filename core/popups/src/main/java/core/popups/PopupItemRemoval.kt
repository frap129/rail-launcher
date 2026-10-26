package core.popups

import androidx.lifecycle.ViewModel
import core.data.popups.IPopupRepository
import core.util.launchLogged

/** Removes [itemKey] from [ownerKey]'s Pop-up, logging a failure. */
internal fun ViewModel.removeAddedItem(popupRepository: IPopupRepository, ownerKey: String, itemKey: String) {
    launchLogged(message = "Failed to remove $itemKey from its Pop-up") {
        popupRepository.removeItem(ownerKey = ownerKey, itemKey = itemKey)
    }
}
