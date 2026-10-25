package core.data.testing

import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.widgets.AppWidgetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A settleable [IPopupRepository] over hidden shortcut ids and added items per owner. */
class FakePopupRepository(private val resolveLaunchable: (String) -> Launchable? = { null }) : IPopupRepository {
    private val hiddenIdsByOwner: MutableStateFlow<Map<String, Set<String>>> = MutableStateFlow(emptyMap())
    private val itemsByOwner: MutableStateFlow<Map<String, List<PopupItem>>> = MutableStateFlow(emptyMap())
    private val notificationsEnabledState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val removedWidgets: MutableSharedFlow<AppWidgetId> = MutableSharedFlow(extraBufferCapacity = 8)

    override val removedWidgetIds: SharedFlow<AppWidgetId> = removedWidgets.asSharedFlow()

    override val notificationsEnabled: Flow<Boolean> = notificationsEnabledState

    /** Every setShortcutHidden call, in order. */
    val setShortcutHiddenCalls: MutableList<Triple<String, String, Boolean>> = mutableListOf()

    /** Every deleteOwner call, in order. */
    val deletedOwners: MutableList<String> = mutableListOf()

    /** Every setItems call, in order. */
    val setItemsCalls: MutableList<Triple<String, List<String>, Set<String>>> = mutableListOf()

    /** Every removeItem call, in order. */
    val removedItems: MutableList<Pair<String, String>> = mutableListOf()

    /** Every setItemOrder call, in order. */
    val itemOrders: MutableList<Pair<String, List<String>>> = mutableListOf()

    /** Every deleteItemsByKey call, in order. */
    val deletedItemKeys: MutableList<List<String>> = mutableListOf()

    override fun hiddenShortcuts(ownerKey: String): Flow<Set<String>> = hiddenIdsByOwner.map { hiddenIds ->
        hiddenIds[ownerKey] ?: emptySet()
    }

    override suspend fun setShortcutHidden(ownerKey: String, shortcutId: String, hidden: Boolean) {
        setShortcutHiddenCalls += Triple(ownerKey, shortcutId, hidden)
        hiddenIdsByOwner.update { current ->
            val ownerIds: Set<String> = current[ownerKey] ?: emptySet()
            current + (ownerKey to if (hidden) ownerIds + shortcutId else ownerIds - shortcutId)
        }
    }

    override fun items(ownerKey: String): Flow<List<PopupItem>> = itemsByOwner.map { items ->
        items[ownerKey] ?: emptyList()
    }

    override val itemCounts: Flow<Map<String, Int>> = itemsByOwner.map { items ->
        items.mapValues { (_, ownerItems) -> ownerItems.size }
    }

    override suspend fun setItems(ownerKey: String, add: List<String>, remove: Set<String>) {
        setItemsCalls += Triple(ownerKey, add, remove)
        itemsByOwner.update { current ->
            val remaining: List<PopupItem> = current[ownerKey].orEmpty().filterNot { item -> item.launchable.key in remove }
            val additions: List<PopupItem> = add.mapNotNull(resolveLaunchable).map { launchable -> PopupItem.Launchable(launchable) }
            current + (ownerKey to remaining + additions)
        }
    }

    override suspend fun removeItem(ownerKey: String, itemKey: String) {
        removedItems += ownerKey to itemKey
        itemsByOwner.update { current ->
            current + (ownerKey to current[ownerKey].orEmpty().filterNot { item -> item.launchable.key == itemKey })
        }
    }

    override suspend fun setItemOrder(ownerKey: String, orderedItemKeys: List<String>) {
        itemOrders += ownerKey to orderedItemKeys
        itemsByOwner.update { current ->
            val items: List<PopupItem> = current[ownerKey].orEmpty()
            val itemsByKey: Map<String, PopupItem> = items.associateBy { item -> item.launchable.key }
            val orderedItems: List<PopupItem> = orderedItemKeys.mapNotNull { key -> itemsByKey[key] }
            val orderedKeys: Set<String> = orderedItems.mapTo(mutableSetOf()) { item -> item.launchable.key }
            var nextIndex: Int = 0
            val reordered: List<PopupItem> = items.map { item ->
                if (item.launchable.key in orderedKeys) orderedItems[nextIndex++] else item
            }
            current + (ownerKey to reordered)
        }
    }

    override suspend fun deleteItemsByKey(itemKeys: List<String>) {
        deletedItemKeys += itemKeys
        itemsByOwner.update { current ->
            current.mapValues { (_, items) -> items.filterNot { item -> item.launchable.key in itemKeys } }
        }
    }

    override suspend fun deleteOwner(ownerKey: String): List<AppWidgetId> {
        deletedOwners += ownerKey
        hiddenIdsByOwner.update { current -> current - ownerKey }
        itemsByOwner.update { current -> current - ownerKey }
        return emptyList()
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabledState.value = enabled
    }

    fun setHiddenShortcuts(ownerKey: String, shortcutIds: Set<String>) {
        hiddenIdsByOwner.update { current -> current + (ownerKey to shortcutIds) }
    }

    fun setItems(ownerKey: String, items: List<Launchable>) {
        setPopupItems(ownerKey = ownerKey, items = items.map { launchable -> PopupItem.Launchable(launchable) })
    }

    fun setPopupItems(ownerKey: String, items: List<PopupItem>) {
        itemsByOwner.update { current -> current + (ownerKey to items) }
    }

    fun emitRemovedWidget(appWidgetId: AppWidgetId) {
        removedWidgets.tryEmit(appWidgetId)
    }
}
