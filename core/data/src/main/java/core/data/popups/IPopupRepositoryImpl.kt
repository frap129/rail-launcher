package core.data.popups

import core.data.launchables.ILaunchableRepository
import core.data.retryWithExponentialBackoff
import core.data.room.dao.PopupDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class IPopupRepositoryImpl(private val popupDao: PopupDao, launchableRepository: ILaunchableRepository, scope: CoroutineScope) :
    IPopupRepository {
    private val staleOwnerKeys: StateFlow<List<String>> = combine(
        popupDao.observeAllOwnerKeys(),
        launchableRepository.launchableState
    ) { ownerKeys, state ->
        if (state.isLoaded) {
            val launchableKeys: Set<String> = state.launchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
            ownerKeys.filterNot { ownerKey -> ownerKey in launchableKeys }
        } else {
            emptyList()
        }
    }
        .retryWithExponentialBackoff()
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    init {
        // Deleting stale rows keeps a reinstalled Launchable from restoring old Pop-up preferences.
        scope.launch {
            staleOwnerKeys.collect { ownerKeys -> deleteStaleOwners(ownerKeys) }
        }
    }

    override fun hiddenShortcuts(ownerKey: String): Flow<Set<String>> = popupDao.observeHiddenShortcuts(ownerKey)
        .map { shortcutIds -> shortcutIds.toSet() }
        .distinctUntilChanged()

    override suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean) {
        popupDao.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
    }

    override suspend fun deleteOwner(ownerKey: String) {
        popupDao.deleteHiddenForOwner(ownerKey)
    }

    private suspend fun deleteStaleOwners(ownerKeys: List<String>) {
        for (ownerKey in ownerKeys) {
            try {
                deleteOwner(ownerKey)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to remove hidden shortcuts for %s", ownerKey)
            }
        }
    }
}
