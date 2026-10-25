package core.data.testing

import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A settleable [IPopupRepository] over hidden shortcut ids and added items per owner. */
class FakePopupRepository(private val resolveLaunchable: (String) -> Launchable? = { null }) : IPopupRepository {
    private val hiddenIdsByOwner: MutableStateFlow<Map<String, Set<String>>> = MutableStateFlow(emptyMap())
    private val itemsByOwner: MutableStateFlow<Map<String, List<Launchable>>> = MutableStateFlow(emptyMap())

    /** Every setShortcutHidden call, in order. */
    val setShortcutHiddenCalls: MutableList<Triple<String, String, Boolean>> = mutableListOf()

    /** Every deleteOwner call, in order. */
    val deletedOwners: MutableList<String> = mutableListOf()

    /** Every setItems call, in order. */
    val setItemsCalls: MutableList<Triple<String, List<String>, Set<String>>> = mutableListOf()

    /** Every removeItem call, in order. */
    val removedItems: MutableList<Pair<String, String>> = mutableListOf()

    /** Every moveItem call, in order. */
    val movedItems: MutableList<Triple<String, Int, Int>> = mutableListOf()

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

    override fun items(ownerKey: String): Flow<List<Launchable>> = itemsByOwner.map { items ->
        items[ownerKey] ?: emptyList()
    }

    override suspend fun setItems(ownerKey: String, add: List<String>, remove: Set<String>) {
        setItemsCalls += Triple(ownerKey, add, remove)
        itemsByOwner.update { current ->
            val remaining: List<Launchable> = current[ownerKey].orEmpty().filterNot { item -> item.key in remove }
            val additions: List<Launchable> = add.mapNotNull(resolveLaunchable)
            current + (ownerKey to remaining + additions)
        }
    }

    override suspend fun removeItem(ownerKey: String, itemKey: String) {
        removedItems += ownerKey to itemKey
        itemsByOwner.update { current ->
            current + (ownerKey to current[ownerKey].orEmpty().filterNot { item -> item.key == itemKey })
        }
    }

    override suspend fun moveItem(ownerKey: String, from: Int, to: Int) {
        movedItems += Triple(ownerKey, from, to)
        itemsByOwner.update { current ->
            val items: MutableList<Launchable> = current[ownerKey].orEmpty().toMutableList()
            if (from in items.indices && to in items.indices) {
                items.add(to, items.removeAt(from))
            }
            current + (ownerKey to items)
        }
    }

    override suspend fun deleteOwner(ownerKey: String) {
        deletedOwners += ownerKey
        hiddenIdsByOwner.update { current -> current - ownerKey }
        itemsByOwner.update { current -> current - ownerKey }
    }

    fun setHiddenShortcuts(ownerKey: String, shortcutIds: Set<String>) {
        hiddenIdsByOwner.update { current -> current + (ownerKey to shortcutIds) }
    }

    fun setItems(ownerKey: String, items: List<Launchable>) {
        itemsByOwner.update { current -> current + (ownerKey to items) }
    }
}
