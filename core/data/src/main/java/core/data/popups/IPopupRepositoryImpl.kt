package core.data.popups

import core.data.apps.model.AppProfile
import core.data.cleanup.IStoredKeyClassifier
import core.data.cleanup.StoredKeyState
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.isKeptKey
import core.data.reorderedPinned
import core.data.retryWithExponentialBackoff
import core.data.room.DatabaseTransaction
import core.data.room.TransactionEffects
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutPinCoordinator
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.widgets.AppWidgetId
import core.data.widgets.IRemoveOwnerWidgets
import core.data.widgets.IWidgetAreaRepository
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class ItemResolution(val itemsByOwner: Map<String, List<PopupItem>>)

private val UNLOADED_ITEMS: ItemResolution = ItemResolution(itemsByOwner = emptyMap())

class IPopupRepositoryImpl(
    private val popupDao: PopupDao,
    private val launchableRepository: ILaunchableRepository,
    private val storedKeyClassifier: IStoredKeyClassifier,
    private val shortcutPinCoordinator: IShortcutPinCoordinator,
    scope: CoroutineScope,
    private val widgetAreaRepository: IWidgetAreaRepository,
    private val removeOwnerWidgets: IRemoveOwnerWidgets
) : IPopupRepository {
    private val itemMutex: Mutex = Mutex()
    private val removedWidgets: MutableSharedFlow<AppWidgetId> = MutableSharedFlow(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val removedWidgetIds: SharedFlow<AppWidgetId> = removedWidgets.asSharedFlow()

    private val itemRecords: StateFlow<List<PopupItemRecord>?> = popupDao.observeAllItems()
        .map<List<PopupItemRecord>, List<PopupItemRecord>?> { records -> records }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    private val itemKeys: Flow<Set<String>> = itemRecords.filterNotNull()
        .map { records -> records.mapTo(mutableSetOf()) { record -> record.itemKey } }
        .distinctUntilChanged()

    private val itemResolution: StateFlow<ItemResolution> = combine(
        itemRecords.filterNotNull(),
        storedKeyClassifier.observe(itemKeys)
    ) { records, states ->
        // The classifier lags a key-set change, so wait for it to reach every stored key.
        if (states == null || records.any { record -> record.itemKey !in states }) {
            null
        } else {
            resolveItems(records = records, states = states)
        }
    }
        .filterNotNull()
        .retryWithExponentialBackoff()
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = UNLOADED_ITEMS)

    override fun hiddenShortcuts(ownerKey: String): Flow<Set<String>> = popupDao.observeHiddenShortcuts(ownerKey = ownerKey)
        .map { shortcutIds -> shortcutIds.toSet() }
        .distinctUntilChanged()

    override suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean) {
        popupDao.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
    }

    override fun items(ownerKey: String): Flow<List<PopupItem>> = itemResolution
        .map { resolution -> resolution.itemsByOwner[ownerKey].orEmpty() }
        .distinctUntilChanged()

    override val itemCounts: Flow<Map<String, Int>> = itemResolution
        .map { resolution -> resolution.itemsByOwner.mapValues { (_, items) -> items.size } }
        .distinctUntilChanged()

    override suspend fun setItems(ownerKey: String, add: List<String>, remove: Set<String>) {
        val additions: List<String> = add
            .filterNot { itemKey -> itemKey == ownerKey || itemKey in remove }
            .distinct()
        withItemLock {
            val affectedPins: Set<AppProfile> = pinsOf(additions + remove)
            if (remove.isNotEmpty()) {
                popupDao.removeItems(ownerKey = ownerKey, itemKeys = remove.toList())
            }
            if (additions.isNotEmpty()) {
                popupDao.appendItems(ownerKey = ownerKey, itemKeys = additions)
            }
            shortcutPinCoordinator.reapplyPins(affectedProfiles = affectedPins)
        }
    }

    override suspend fun removeItem(ownerKey: String, itemKey: String) {
        withItemLock {
            popupDao.removeItems(ownerKey = ownerKey, itemKeys = listOf(itemKey))
            shortcutPinCoordinator.reapplyPins(affectedProfiles = pinsOf(listOf(itemKey)))
        }
    }

    override suspend fun setItemOrder(ownerKey: String, orderedItemKeys: List<String>) {
        withItemLock {
            val records: List<PopupItemRecord> = popupDao.getItems(ownerKey)
                .sortedBy { item -> item.position }
            val state: LaunchableState = launchableRepository.launchableState.value
            val reordered: List<PopupItemRecord> = records.reorderedPinned(
                orderedKeys = orderedItemKeys,
                keyOf = { record -> record.itemKey },
                isPinned = { record -> state.isKeptKey(record.itemKey) }
            )
            if (reordered != records) {
                popupDao.updatePositions(reordered.mapIndexed { index, record -> record.copy(position = index) })
            }
        }
    }

    override suspend fun deleteOwner(ownerKey: String): List<AppWidgetId> = withItemLock {
        val removedKeys: List<String> = popupDao.getItems(ownerKey).map { item -> item.itemKey }
        popupDao.deleteHiddenForOwner(ownerKey)
        popupDao.deleteItemsForOwner(ownerKey)
        val effects: TransactionEffects? = coroutineContext[TransactionEffects.Key]
        shortcutPinCoordinator.reapplyPins(affectedProfiles = pinsOf(removedKeys))
        val freedIds: List<AppWidgetId> = if (effects == null) {
            removeOwnerWidgets(ownerKey).also { ownedIds ->
                ownedIds.forEach { appWidgetId -> removedWidgets.tryEmit(appWidgetId) }
            }
        } else {
            widgetAreaRepository.removeOwnerPlaces(ownerKey).also { ownedIds ->
                // Releasing system Widget ids cannot be undone, so it waits for the commit.
                effects.afterCommit {
                    removeOwnerWidgets.release(appWidgetIds = ownedIds)
                    ownedIds.forEach { appWidgetId -> removedWidgets.tryEmit(appWidgetId) }
                }
            }
        }
        freedIds
    }

    override suspend fun deleteItemsByKey(itemKeys: List<String>) {
        if (itemKeys.isEmpty()) {
            return
        }
        withItemLock {
            val storedItemKeys: List<String> = itemKeys.distinct()
            popupDao.deleteItemsByKey(storedItemKeys)
            shortcutPinCoordinator.reapplyPins(affectedProfiles = pinsOf(storedItemKeys))
        }
    }

    override val notificationsEnabled: Flow<Boolean> = popupDao.observeSettings()
        .map { settings -> settings?.notificationsEnabled ?: false }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        popupDao.upsertSettings(settings = PopupSettingsRecord(notificationsEnabled = enabled))
    }

    /**
     * Serializes Pop-up writes. Inside a database transaction the database already serializes them,
     * and taking the lock there could invert locks against a writer waiting for the write connection.
     */
    private suspend inline fun <T> withItemLock(block: suspend () -> T): T = if (coroutineContext[DatabaseTransaction.Key] != null) {
        block()
    } else {
        itemMutex.withLock { block() }
    }
}

private fun pinsOf(itemKeys: Iterable<String>): Set<AppProfile> = itemKeys
    .mapNotNull { itemKey -> ShortcutKey.parse(itemKey) }
    .mapTo(mutableSetOf()) { key -> key.toAppProfile() }

private fun resolveItems(records: List<PopupItemRecord>, states: Map<String, StoredKeyState>): ItemResolution {
    val itemsByOwner: MutableMap<String, MutableList<PopupItem>> = mutableMapOf()
    records.sortedBy { record -> record.position }.forEach { record ->
        val state: StoredKeyState = states[record.itemKey] ?: return@forEach
        if (state is StoredKeyState.Present) {
            itemsByOwner.getOrPut(record.ownerKey) { mutableListOf() }.add(state.toPopupItem())
        }
    }
    return ItemResolution(
        itemsByOwner = itemsByOwner.mapValues { (_, items) -> items.distinctBy { item -> item.launchable.key } }
    )
}

private fun StoredKeyState.Present.toPopupItem(): PopupItem = when (val current = launchable) {
    is ShortcutLaunchable -> PopupItem.Shortcut(launchable = current, isEnabled = current.isEnabled)
    else -> PopupItem.Launchable(launchable = current)
}
