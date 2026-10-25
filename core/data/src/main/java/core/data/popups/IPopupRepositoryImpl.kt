package core.data.popups

import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.retryWithExponentialBackoff
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.widgets.AppWidgetId
import core.data.widgets.IRemoveOwnerWidgets
import core.data.widgets.IWidgetAreaRepository
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private data class ItemResolution(val itemsByOwner: Map<String, List<PopupItem>>, val staleKeys: List<String>)

private val UNLOADED_ITEMS: ItemResolution = ItemResolution(
    itemsByOwner = emptyMap(),
    staleKeys = emptyList()
)

class IPopupRepositoryImpl(
    private val popupDao: PopupDao,
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
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

    private val staleOwnerKeys: StateFlow<List<String>> = combine(
        popupDao.observeAllOwnerKeys(),
        popupDao.observeAllItems(),
        launchableRepository.launchableState,
        widgetAreaRepository.popupOwnerKeys
    ) { hiddenOwnerKeys, itemRecords, state, widgetOwnerKeys ->
        if (state.isLoaded) {
            val launchableKeys: Set<String> = state.launchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
            (hiddenOwnerKeys + itemRecords.map { record -> record.ownerKey } + widgetOwnerKeys)
                .distinct()
                .filterNot { ownerKey -> ownerKey in launchableKeys }
        } else {
            emptyList()
        }
    }
        .retryWithExponentialBackoff()
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private val itemResolution: StateFlow<ItemResolution> = popupDao.observeAllItems()
        .flatMapLatest { records ->
            val shortcutKeys: Set<ShortcutKey> = records.mapNotNullTo(mutableSetOf()) { record -> ShortcutKey.parse(record.itemKey) }
            combine(
                launchableRepository.launchableState,
                if (shortcutKeys.isEmpty()) flowOf(emptyMap()) else shortcutSource.resolve(shortcutKeys)
            ) { state, shortcuts -> resolveItems(records = records, state = state, shortcuts = shortcuts) }
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
        scope.launch {
            launchableRepository.launchableState.filter { state -> state.isLoaded }.first()
            itemMutex.withLock { reapplyAllPins() }
        }
    }

    override fun hiddenShortcuts(ownerKey: String): Flow<Set<String>> = popupDao.observeHiddenShortcuts(ownerKey)
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
        itemMutex.withLock {
            val affectedPins: Set<AppProfile> = pinsOf(additions + remove)
            if (remove.isNotEmpty()) {
                popupDao.removeItems(ownerKey = ownerKey, itemKeys = remove.toList())
            }
            if (additions.isNotEmpty()) {
                popupDao.appendItems(ownerKey = ownerKey, itemKeys = additions)
            }
            reapplyPins(affectedPins = affectedPins)
        }
    }

    override suspend fun removeItem(ownerKey: String, itemKey: String) {
        itemMutex.withLock {
            popupDao.removeItems(ownerKey = ownerKey, itemKeys = listOf(itemKey))
            reapplyPins(affectedPins = pinsOf(listOf(itemKey)))
        }
    }

    override suspend fun setItemOrder(ownerKey: String, orderedItemKeys: List<String>) {
        itemMutex.withLock {
            val records: List<PopupItemRecord> = popupDao.observeItems(ownerKey).first()
                .sortedBy { item -> item.position }
            val recordsByKey: Map<String, PopupItemRecord> = records.associateBy { record -> record.itemKey }
            val orderedRecords: List<PopupItemRecord> = orderedItemKeys.mapNotNull { key -> recordsByKey[key] }
            if (orderedRecords.isEmpty()) {
                return
            }
            val orderedKeys: Set<String> = orderedRecords.mapTo(mutableSetOf()) { record -> record.itemKey }
            var nextIndex: Int = 0
            val reordered: List<PopupItemRecord> = records.map { record ->
                if (record.itemKey in orderedKeys) orderedRecords[nextIndex++] else record
            }
            if (reordered != records) {
                popupDao.updatePositions(reordered.mapIndexed { index, record -> record.copy(position = index) })
            }
        }
    }

    override suspend fun deleteOwner(ownerKey: String): List<AppWidgetId> = itemMutex.withLock {
        val removedKeys: List<String> = popupDao.observeItems(ownerKey).first().map { item -> item.itemKey }
        popupDao.deleteHiddenForOwner(ownerKey)
        popupDao.deleteItemsForOwner(ownerKey)
        reapplyPins(affectedPins = pinsOf(removedKeys))
        removeOwnerWidgets(ownerKey).also { freedIds ->
            freedIds.forEach { appWidgetId -> removedWidgets.tryEmit(appWidgetId) }
        }
    }

    override suspend fun deleteItemsByKey(itemKeys: List<String>) {
        if (itemKeys.isEmpty()) {
            return
        }
        itemMutex.withLock {
            popupDao.deleteItemsByKey(itemKeys)
            reapplyPins(affectedPins = pinsOf(itemKeys))
        }
    }

    override val notificationsEnabled: Flow<Boolean> = popupDao.observeSettings()
        .map { settings -> settings?.notificationsEnabled ?: false }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        popupDao.upsertSettings(settings = PopupSettingsRecord(notificationsEnabled = enabled))
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
            deleteItemsByKey(staleKeys)
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

    private suspend fun reapplyPins(affectedPins: Set<AppProfile>) {
        if (affectedPins.isEmpty()) {
            return
        }
        val currentPinSets: Map<AppProfile, Set<String>> = pinSets(popupDao.observeAllItems().first())
        for (pin in affectedPins) {
            shortcutSource.pin(packageName = pin.packageName, profile = pin.profile, ids = currentPinSets[pin].orEmpty())
        }
    }

    private suspend fun reapplyAllPins() {
        for ((pin: AppProfile, ids: Set<String>) in pinSets(popupDao.observeAllItems().first())) {
            shortcutSource.pin(packageName = pin.packageName, profile = pin.profile, ids = ids)
        }
    }
}

private fun pinsOf(itemKeys: Iterable<String>): Set<AppProfile> = itemKeys
    .mapNotNull { itemKey -> ShortcutKey.parse(itemKey) }
    .mapTo(mutableSetOf()) { key -> AppProfile(packageName = key.packageName, profile = key.profile) }

private fun pinSets(records: List<PopupItemRecord>): Map<AppProfile, Set<String>> = records
    .mapNotNull { record -> ShortcutKey.parse(record.itemKey) }
    .groupBy({ key -> AppProfile(packageName = key.packageName, profile = key.profile) }, { key -> key.id })
    .mapValues { (_, ids) -> ids.toSet() }

private fun resolveItems(
    records: List<PopupItemRecord>,
    state: LaunchableState,
    shortcuts: Map<ShortcutKey, ShortcutLaunchable?>
): ItemResolution {
    if (!state.isLoaded) {
        return UNLOADED_ITEMS
    }
    val launchablesByKey: Map<String, Launchable> = state.launchables.associateBy { launchable -> launchable.key }
    val installedApps: Set<AppProfile> = state.launchables
        .filterIsInstance<App>()
        .mapTo(mutableSetOf()) { app -> AppProfile(packageName = app.packageName, profile = app.profile) }
    val itemsByOwner: MutableMap<String, MutableList<PopupItem>> = mutableMapOf()
    val staleKeys: MutableList<String> = mutableListOf()
    val lookup: ItemLookup = ItemLookup(
        launchablesByKey = launchablesByKey,
        installedApps = installedApps,
        shortcuts = shortcuts
    )
    records.sortedBy { record -> record.position }.forEach { record ->
        when (val resolution: RecordResolution = resolveRecord(record = record, lookup = lookup)) {
            is RecordResolution.Resolved -> itemsByOwner.getOrPut(record.ownerKey) { mutableListOf() }.add(resolution.item)
            RecordResolution.Stale -> staleKeys.add(record.itemKey)
            RecordResolution.Unavailable -> Unit
        }
    }
    return ItemResolution(
        itemsByOwner = itemsByOwner,
        staleKeys = staleKeys.distinct()
    )
}

private data class ItemLookup(
    val launchablesByKey: Map<String, Launchable>,
    val installedApps: Set<AppProfile>,
    val shortcuts: Map<ShortcutKey, ShortcutLaunchable?>
)

private sealed interface RecordResolution {
    data class Resolved(val item: PopupItem) : RecordResolution
    data object Stale : RecordResolution
    data object Unavailable : RecordResolution
}

private fun resolveRecord(record: PopupItemRecord, lookup: ItemLookup): RecordResolution {
    if (ShortcutKey.hasPrefix(record.itemKey)) {
        val key: ShortcutKey = ShortcutKey.parse(record.itemKey) ?: return RecordResolution.Stale
        if (!lookup.shortcuts.containsKey(key)) {
            return RecordResolution.Unavailable
        }
        if (AppProfile(packageName = key.packageName, profile = key.profile) !in lookup.installedApps) {
            return RecordResolution.Stale
        }
        val shortcut: ShortcutLaunchable = lookup.shortcuts[key] ?: return RecordResolution.Stale
        return RecordResolution.Resolved(PopupItem.Shortcut(launchable = shortcut, isEnabled = shortcut.isEnabled))
    }
    val launchable: Launchable = lookup.launchablesByKey[record.itemKey] ?: return RecordResolution.Stale
    return RecordResolution.Resolved(PopupItem.Launchable(launchable = launchable))
}
