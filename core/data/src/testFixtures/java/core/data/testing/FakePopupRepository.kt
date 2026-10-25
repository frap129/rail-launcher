package core.data.testing

import core.data.popups.IPopupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A settleable [IPopupRepository] over hidden shortcut ids per owner. */
class FakePopupRepository : IPopupRepository {
    private val hiddenIdsByOwner: MutableStateFlow<Map<String, Set<String>>> = MutableStateFlow(emptyMap())

    /** Every setShortcutHidden call, in order. */
    val setShortcutHiddenCalls: MutableList<Triple<String, String, Boolean>> = mutableListOf()

    /** Every deleteOwner call, in order. */
    val deletedOwners: MutableList<String> = mutableListOf()

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

    override suspend fun deleteOwner(ownerKey: String) {
        deletedOwners += ownerKey
        hiddenIdsByOwner.update { current -> current - ownerKey }
    }

    fun setHiddenShortcuts(ownerKey: String, shortcutIds: Set<String>) {
        hiddenIdsByOwner.update { current -> current + (ownerKey to shortcutIds) }
    }
}
