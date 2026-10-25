package core.data.popups

import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.retryWithExponentialBackoff
import core.data.room.dao.PopupDao
import core.data.shortcuts.ShortcutKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private data class ItemResolution(val itemsByOwner: Map<String, List<Launchable>>, val staleKeys: List<String>)

private val UNLOADED_ITEMS: ItemResolution = ItemResolution(
    itemsByOwner = emptyMap(),
    staleKeys = emptyList()
)

class IPopupRepositoryImpl(private val popupDao: PopupDao, launchableRepository: ILaunchableRepository, scope: CoroutineScope) :
    IPopupRepository {
    private val itemMutex: Mutex = Mutex()

    private val staleOwnerKeys: StateFlow<List<String>> = combine(
        popupDao.observeAllOwnerKeys(),
        popupDao.observeAllItems(),
        launchableRepository.launchableState
    ) { hiddenOwnerKeys, itemRecords, state ->
        if (state.isLoaded) {
            val launchableKeys: Set<String> = state.launchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
            (hiddenOwnerKeys + itemRecords.map { record -> record.ownerKey })
                .distinct()
                .filterNot { ownerKey -> ownerKey in launchableKeys }
        } else {
            emptyList()
        }
    }
        .retryWithExponentialBackoff()
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private val itemResolution: StateFlow<ItemResolution> = combine(
        popupDao.observeAllItems(),
        launchableRepository.launchableState
    ) { records, state ->
        if (state.isLoaded) {
            resolveItems(records = records, launchables = state.launchables)
        } else {
            UNLOADED_ITEMS
        }
    }
        .retryWithExponentialBackoff()
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = UNLOADED_ITEMS)

    init {
        // Deleting stale rows keeps a reinstalled Launchable from restoring old Pop-up preferences.
        scope.launch {
            staleOwnerKeys.collect { ownerKeys -> deleteStaleOwners(ownerKeys) }
        }
        scope.launch {
            itemResolution.collect { resolution -> deleteStaleItems(resolution.staleKeys) }
        }
    }

    override fun hiddenShortcuts(ownerKey: String): Flow<Set<String>> = popupDao.observeHiddenShortcuts(ownerKey)
        .map { shortcutIds -> shortcutIds.toSet() }
        .distinctUntilChanged()

    override suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean) {
        popupDao.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
    }

    override fun items(ownerKey: String): Flow<List<Launchable>> = itemResolution
        .map { resolution -> resolution.itemsByOwner[ownerKey].orEmpty() }
        .distinctUntilChanged()

    override suspend fun setItems(ownerKey: String, add: List<String>, remove: Set<String>) {
        val additions: List<String> = add
            .filterNot { itemKey -> itemKey == ownerKey || itemKey in remove }
            .distinct()
        if (remove.isNotEmpty()) {
            popupDao.removeItems(ownerKey = ownerKey, itemKeys = remove.toList())
        }
        if (additions.isNotEmpty()) {
            popupDao.appendItems(ownerKey = ownerKey, itemKeys = additions)
        }
    }

    override suspend fun removeItem(ownerKey: String, itemKey: String) {
        popupDao.removeItems(ownerKey = ownerKey, itemKeys = listOf(itemKey))
    }

    override suspend fun moveItem(ownerKey: String, from: Int, to: Int) {
        if (from == to) {
            return
        }
        itemMutex.withLock {
            val items: MutableList<PopupItemRecord> = popupDao.observeItems(ownerKey).first()
                .sortedBy { item -> item.position }
                .toMutableList()
            if (from !in items.indices || to !in items.indices) {
                return
            }
            val moved: PopupItemRecord = items.removeAt(from)
            items.add(to, moved)
            popupDao.updatePositions(items.mapIndexed { index, item -> item.copy(position = index) })
        }
    }

    override suspend fun deleteOwner(ownerKey: String) {
        popupDao.deleteHiddenForOwner(ownerKey)
        popupDao.deleteItemsForOwner(ownerKey)
    }

    private suspend fun deleteStaleOwners(ownerKeys: List<String>) {
        for (ownerKey in ownerKeys) {
            deleteStale(message = "Failed to remove Pop-up preferences for $ownerKey") {
                deleteOwner(ownerKey)
            }
        }
    }

    private suspend fun deleteStaleItems(staleKeys: List<String>) {
        if (staleKeys.isEmpty()) {
            return
        }
        deleteStale(message = "Failed to remove ${staleKeys.size} stale Pop-up items") {
            popupDao.deleteItemsByKey(staleKeys)
        }
    }

    private suspend fun deleteStale(message: String, action: suspend () -> Unit) {
        try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, message)
        }
    }
}

private fun resolveItems(records: List<PopupItemRecord>, launchables: List<Launchable>): ItemResolution {
    val launchablesByKey: Map<String, Launchable> = launchables.associateBy { launchable -> launchable.key }
    val itemsByOwner: Map<String, List<Launchable>> = records
        .sortedBy { record -> record.position }
        .groupBy { record -> record.ownerKey }
        .mapValues { (_, ownerRecords) ->
            ownerRecords.mapNotNull { record -> launchablesByKey[record.itemKey] }
        }
    val staleKeys: List<String> = records
        .filterNot { record -> record.itemKey in launchablesByKey || ShortcutKey.hasPrefix(record.itemKey) }
        .map { record -> record.itemKey }
        .distinct()
    return ItemResolution(
        itemsByOwner = itemsByOwner,
        staleKeys = staleKeys
    )
}
