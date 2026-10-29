package core.data.popups

import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.isKeptKey
import core.data.launchables.model.Launchable
import core.data.launchables.staleKeys
import core.data.launchables.storedKeys
import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.reorderedPinned
import core.data.retryWithExponentialBackoff
import core.data.room.DatabaseTransaction
import core.data.room.TransactionEffects
import core.data.room.dao.PopupDao
import core.data.runCleanup
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutResolution
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
            val ownerKeys: List<String> = (hiddenOwnerKeys + itemRecords.map { record -> record.ownerKey } + widgetOwnerKeys).distinct()
            state.staleKeys(ownerKeys)
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
                shortcutSource.resolve(shortcutKeys)
            ) { state, resolution -> resolveItems(records = records, state = state, resolution = resolution) }
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
            withItemLock { reapplyAllPins() }
        }
        scope.launch {
            launchableRepository.launchableState
                .filter { state -> state.isLoaded }
                .map { state -> state.enumeratedKeys }
                .distinctUntilChanged()
                .collect { tokens -> reapplyEnumeratedPins(tokens = tokens) }
        }
    }

    /** Re-applies pins once a Profile that was Locked lists its Apps again. */
    private suspend fun reapplyEnumeratedPins(tokens: Set<ProfileToken>) {
        if (tokens.isEmpty()) {
            return
        }
        withItemLock { reapplyPinsForProfiles(tokens = tokens) }
    }

    private suspend fun reapplyPinsForProfiles(tokens: Set<ProfileToken>) {
        val pins: Map<AppProfile, Set<String>> = pinSets(popupDao.getAllItems())
            .filterKeys { pin -> pin.profile.toProfileToken() in tokens }
        for ((pin: AppProfile, ids: Set<String>) in pins) {
            shortcutSource.pin(packageName = pin.packageName, profile = pin.profile, ids = ids)
        }
    }

    override fun hiddenShortcuts(ownerKey: String): Flow<Set<String>> = launchableRepository.launchableState
        .map { state -> state.storedKeys(key = ownerKey) }
        .distinctUntilChanged()
        .flatMapLatest { ownerKeys -> popupDao.observeHiddenShortcuts(ownerKeys = ownerKeys) }
        .map { shortcutIds -> shortcutIds.toSet() }
        .distinctUntilChanged()

    override suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean) {
        val resolvedOwnerKey: String = canonicalOwnerKey(ownerKey = ownerKey)
        if (hidden) {
            popupDao.setShortcutHidden(ownerKey = resolvedOwnerKey, shortcutId = shortcutId, hidden = true)
        } else {
            popupDao.deleteHiddenShortcut(ownerKeys = storedKeys(key = resolvedOwnerKey), shortcutId = shortcutId)
        }
    }

    override fun items(ownerKey: String): Flow<List<PopupItem>> = itemResolution
        .map { resolution -> resolution.itemsByOwner[canonicalOwnerKey(ownerKey = ownerKey)].orEmpty() }
        .distinctUntilChanged()

    override val itemCounts: Flow<Map<String, Int>> = itemResolution
        .map { resolution -> resolution.itemsByOwner.mapValues { (_, items) -> items.size } }
        .distinctUntilChanged()

    override suspend fun setItems(ownerKey: String, add: List<String>, remove: Set<String>) {
        val resolvedOwnerKey: String = canonicalOwnerKey(ownerKey = ownerKey)
        val additions: List<String> = add
            .filterNot { itemKey -> itemKey == resolvedOwnerKey || itemKey in remove }
            .distinct()
        withItemLock {
            mergeStoredOwnerKeys(ownerKey = resolvedOwnerKey)
            val affectedPins: Set<AppProfile> = pinsOf(additions + remove)
            if (remove.isNotEmpty()) {
                val ownerKeys: List<String> = storedKeys(key = resolvedOwnerKey)
                val removableKeys: List<String> = remove.flatMap { key -> storedKeys(key = key) }.distinct()
                ownerKeys.forEach { storedOwnerKey ->
                    popupDao.removeItems(ownerKey = storedOwnerKey, itemKeys = removableKeys)
                }
            }
            if (additions.isNotEmpty()) {
                popupDao.appendItems(ownerKey = resolvedOwnerKey, itemKeys = additions)
            }
            reapplyPins(affectedPins = affectedPins)
        }
    }

    override suspend fun removeItem(ownerKey: String, itemKey: String) {
        withItemLock {
            val resolvedOwnerKey: String = canonicalOwnerKey(ownerKey = ownerKey)
            val itemKeys: List<String> = storedKeys(key = itemKey)
            storedKeys(key = resolvedOwnerKey).forEach { storedOwnerKey ->
                popupDao.removeItems(ownerKey = storedOwnerKey, itemKeys = itemKeys)
            }
            reapplyPins(affectedPins = pinsOf(itemKeys))
        }
    }

    override suspend fun setItemOrder(ownerKey: String, orderedItemKeys: List<String>) {
        withItemLock {
            val resolvedOwnerKey: String = canonicalOwnerKey(ownerKey = ownerKey)
            mergeStoredOwnerKeys(ownerKey = resolvedOwnerKey)
            val records: List<PopupItemRecord> = popupDao.getItems(resolvedOwnerKey)
                .sortedBy { item -> item.position }
            val state: LaunchableState = launchableRepository.launchableState.value
            val reordered: List<PopupItemRecord> = records.reorderedPinned(
                orderedKeys = orderedItemKeys.flatMap { key -> storedKeys(key = key) },
                keyOf = { record -> record.itemKey },
                isPinned = { record -> state.isKeptKey(record.itemKey) }
            )
            if (reordered != records) {
                popupDao.updatePositions(reordered.mapIndexed { index, record -> record.copy(position = index) })
            }
        }
    }

    override suspend fun deleteOwner(ownerKey: String): List<AppWidgetId> = withItemLock {
        val resolvedOwnerKey: String = canonicalOwnerKey(ownerKey = ownerKey)
        val ownerKeys: List<String> = storedKeys(key = resolvedOwnerKey)
        val removedKeys: List<String> = ownerKeys.flatMap { storedOwnerKey ->
            popupDao.getItems(storedOwnerKey).map { item -> item.itemKey }
        }
        ownerKeys.forEach { storedOwnerKey ->
            popupDao.deleteHiddenForOwner(storedOwnerKey)
            popupDao.deleteItemsForOwner(storedOwnerKey)
        }
        val effects: TransactionEffects? = coroutineContext[TransactionEffects.Key]
        val freedIds: List<AppWidgetId> = if (effects == null) {
            reapplyPins(affectedPins = pinsOf(removedKeys))
            removeOwnerWidgets(resolvedOwnerKey).also { ownedIds ->
                ownedIds.forEach { appWidgetId -> removedWidgets.tryEmit(appWidgetId) }
            }
        } else {
            widgetAreaRepository.removeOwnerPlaces(resolvedOwnerKey).also { ownedIds ->
                // Releasing system Widget ids and pins cannot be undone, so they wait for the commit.
                effects.afterCommit {
                    removeOwnerWidgets.release(appWidgetIds = ownedIds)
                    ownedIds.forEach { appWidgetId -> removedWidgets.tryEmit(appWidgetId) }
                    withItemLock { reapplyPins(affectedPins = pinsOf(removedKeys)) }
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
            val storedItemKeys: List<String> = itemKeys.flatMap { key -> storedKeys(key = key) }.distinct()
            popupDao.deleteItemsByKey(storedItemKeys)
            val affectedPins: Set<AppProfile> = pinsOf(storedItemKeys)
            val effects: TransactionEffects? = coroutineContext[TransactionEffects.Key]
            if (effects == null) {
                reapplyPins(affectedPins = affectedPins)
            } else {
                // Pinning cannot be undone, so it waits for a caller's commit like the row deletion.
                effects.afterCommit { withItemLock { reapplyPins(affectedPins = affectedPins) } }
            }
        }
    }

    override val notificationsEnabled: Flow<Boolean> = popupDao.observeSettings()
        .map { settings -> settings?.notificationsEnabled ?: false }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        popupDao.upsertSettings(settings = PopupSettingsRecord(notificationsEnabled = enabled))
    }

    private fun storedKeys(key: String): List<String> = launchableRepository.launchableState.value.storedKeys(key = key)

    private fun canonicalOwnerKey(ownerKey: String): String =
        launchableRepository.launchableState.value.resolveKey(key = ownerKey) ?: ownerKey

    /** Moves a Legacy owner's rows to its current key and renumbers them so positions stay unique. */
    private suspend fun mergeStoredOwnerKeys(ownerKey: String) {
        val canonicalKey: String = canonicalOwnerKey(ownerKey = ownerKey)
        val aliasKeys: List<String> = storedKeys(key = canonicalKey).filterNot { key -> key == canonicalKey }
        if (aliasKeys.isEmpty()) {
            return
        }
        for (aliasKey: String in aliasKeys) {
            popupDao.replaceOwnerKey(oldKey = aliasKey, newKey = canonicalKey)
            popupDao.replaceHiddenShortcutOwnerKey(oldKey = aliasKey, newKey = canonicalKey)
        }
        normalizeItemPositions(popupDao = popupDao, ownerKey = canonicalKey)
    }

    private suspend fun deleteStaleOwners(ownerKeys: List<String>) {
        for (ownerKey in ownerKeys) {
            runCleanup(message = "Failed to remove Pop-up preferences for $ownerKey") { deleteOwner(ownerKey) }
        }
    }

    private suspend fun deleteStaleItems(staleKeys: List<String>) {
        if (staleKeys.isEmpty()) {
            return
        }
        runCleanup(message = "Failed to remove ${staleKeys.size} stale Pop-up items") { deleteItemsByKey(staleKeys) }
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

    private suspend fun reapplyPins(affectedPins: Set<AppProfile>) {
        if (affectedPins.isEmpty()) {
            return
        }
        val currentPinSets: Map<AppProfile, Set<String>> = pinSets(popupDao.getAllItems())
        for (pin in affectedPins) {
            shortcutSource.pin(packageName = pin.packageName, profile = pin.profile, ids = currentPinSets[pin].orEmpty())
        }
    }

    private suspend fun reapplyAllPins() {
        val state: LaunchableState = launchableRepository.launchableState.filter { current -> current.isLoaded }.first()
        val livePins: Map<AppProfile, Set<String>> = pinSets(
            popupDao.observeAllItems().first()
        ).withoutKeptProfiles(keptKeys = state.keptKeys)
        for ((pin: AppProfile, ids: Set<String>) in livePins) {
            shortcutSource.pin(packageName = pin.packageName, profile = pin.profile, ids = ids)
        }
    }

    /** A Locked Profile's pins are left as they are until the Profile is removed. */
    private fun Map<AppProfile, Set<String>>.withoutKeptProfiles(keptKeys: Set<ProfileToken>): Map<AppProfile, Set<String>> =
        filterKeys { pin -> pin.profile.toProfileToken() !in keptKeys }
}

private fun pinsOf(itemKeys: Iterable<String>): Set<AppProfile> = itemKeys
    .mapNotNull { itemKey -> ShortcutKey.parse(itemKey) }
    .mapTo(mutableSetOf()) { key -> AppProfile(packageName = key.packageName, profile = key.profile) }

private fun pinSets(records: List<PopupItemRecord>): Map<AppProfile, Set<String>> = records
    .mapNotNull { record -> ShortcutKey.parse(record.itemKey) }
    .groupBy({ key -> AppProfile(packageName = key.packageName, profile = key.profile) }, { key -> key.id })
    .mapValues { (_, ids) -> ids.toSet() }

private fun resolveItems(records: List<PopupItemRecord>, state: LaunchableState, resolution: ShortcutResolution): ItemResolution {
    if (!state.isLoaded) {
        return UNLOADED_ITEMS
    }
    val installedApps: Set<AppProfile> = state.launchables
        .filterIsInstance<App>()
        .mapTo(mutableSetOf()) { app -> AppProfile(packageName = app.packageName, profile = app.profile) }
    val itemsByOwner: MutableMap<String, MutableList<PopupItem>> = mutableMapOf()
    val staleKeys: MutableList<String> = mutableListOf()
    val lookup: ItemLookup = ItemLookup(
        state = state,
        installedApps = installedApps,
        resolution = resolution
    )
    records.sortedBy { record -> record.position }.forEach { record ->
        when (val recordResolution: RecordResolution = resolveRecord(record = record, lookup = lookup)) {
            is RecordResolution.Resolved -> {
                val ownerKey: String = lookup.state.resolveKey(record.ownerKey) ?: record.ownerKey
                itemsByOwner.getOrPut(ownerKey) { mutableListOf() }.add(recordResolution.item)
            }

            RecordResolution.Stale -> staleKeys.add(record.itemKey)

            RecordResolution.Kept, RecordResolution.Unavailable -> Unit
        }
    }
    return ItemResolution(
        itemsByOwner = itemsByOwner.mapValues { (_, items) -> items.distinctBy { item -> item.launchable.key } },
        staleKeys = staleKeys.distinct()
    )
}

private data class ItemLookup(val state: LaunchableState, val installedApps: Set<AppProfile>, val resolution: ShortcutResolution)

private sealed interface RecordResolution {
    data class Resolved(val item: PopupItem) : RecordResolution
    data object Stale : RecordResolution
    data object Kept : RecordResolution
    data object Unavailable : RecordResolution
}

private fun resolveRecord(record: PopupItemRecord, lookup: ItemLookup): RecordResolution {
    if (lookup.state.isKeptKey(record.itemKey)) {
        return RecordResolution.Kept
    }
    if (ShortcutKey.hasPrefix(record.itemKey)) {
        val key: ShortcutKey = ShortcutKey.parse(record.itemKey) ?: return RecordResolution.Stale
        if (AppProfile(packageName = key.packageName, profile = key.profile) !in lookup.installedApps) {
            return RecordResolution.Stale
        }
        if (key in lookup.resolution.goneKeys) {
            return RecordResolution.Stale
        }
        val shortcut: ShortcutLaunchable = lookup.resolution.shortcuts[key] ?: return RecordResolution.Unavailable
        return RecordResolution.Resolved(PopupItem.Shortcut(launchable = shortcut, isEnabled = shortcut.isEnabled))
    }
    val launchable: Launchable = lookup.state.launchableFor(record.itemKey) ?: return RecordResolution.Stale
    return RecordResolution.Resolved(PopupItem.Launchable(launchable = launchable))
}
