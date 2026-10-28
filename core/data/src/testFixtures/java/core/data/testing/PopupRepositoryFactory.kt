package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.popups.PopupItem
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveOwnerWidgetsImpl
import core.data.widgets.IWidgetAreaRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The mutable state behind a [createPopupRepositoryMock] Pop-up repository. */
class PopupRepositoryState(
    val itemsByOwner: MutableStateFlow<Map<String, List<PopupItem>>> = MutableStateFlow(emptyMap()),
    val notificationsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
) {
    val hiddenIdsByOwner: MutableStateFlow<Map<String, Set<String>>> = MutableStateFlow(emptyMap())
    val removedWidgetIds: MutableSharedFlow<AppWidgetId> = MutableSharedFlow(extraBufferCapacity = 8)
}

/** The production Pop-up repository over [popupDao], with its background work on [scope]. */
fun createPopupRepository(
    popupDao: PopupDao,
    launchableRepository: ILaunchableRepository,
    scope: CoroutineScope,
    shortcutSource: IShortcutSource,
    widgetAreaRepository: IWidgetAreaRepository,
    appWidgetHost: IAppWidgetHostSource
): IPopupRepositoryImpl = IPopupRepositoryImpl(
    popupDao = popupDao,
    launchableRepository = launchableRepository,
    shortcutSource = shortcutSource,
    scope = scope,
    widgetAreaRepository = widgetAreaRepository,
    removeOwnerWidgets = IRemoveOwnerWidgetsImpl(repository = widgetAreaRepository, host = appWidgetHost)
)

/** A MockK [IPopupRepository] over [notificationsEnabled], with successful writes and no freed App Widgets. */
fun createPopupRepositoryMock(notificationsEnabled: Boolean = false): IPopupRepository = createPopupRepositoryMock(
    state = PopupRepositoryState(notificationsEnabled = MutableStateFlow(notificationsEnabled))
)

/** A MockK [IPopupRepository] over [state], resolving added keys through [resolveLaunchable]. */
fun createPopupRepositoryMock(state: PopupRepositoryState, resolveLaunchable: (String) -> Launchable? = { null }): IPopupRepository =
    mockk<IPopupRepository>().apply {
        every { this@apply.notificationsEnabled } returns state.notificationsEnabled
        coEvery { setNotificationsEnabled(any()) } coAnswers { state.notificationsEnabled.value = firstArg() }
        every { hiddenShortcuts(any()) } answers {
            val ownerKey: String = firstArg()
            state.hiddenIdsByOwner.map { hiddenIds -> hiddenIds[ownerKey] ?: emptySet() }
        }
        coEvery { setShortcutHidden(any(), any(), any()) } coAnswers {
            val ownerKey: String = firstArg()
            val shortcutId: String = arg(1)
            val hidden: Boolean = arg(2)
            state.hiddenIdsByOwner.update { current ->
                val ownerIds: Set<String> = current[ownerKey] ?: emptySet()
                current + (ownerKey to if (hidden) ownerIds + shortcutId else ownerIds - shortcutId)
            }
        }
        every { items(any()) } answers {
            val ownerKey: String = firstArg()
            state.itemsByOwner.map { itemsByOwner -> itemsByOwner[ownerKey] ?: emptyList() }
        }
        every { itemCounts } returns state.itemsByOwner.map { itemsByOwner ->
            itemsByOwner.mapValues { (_, ownerItems) -> ownerItems.size }
        }
        coEvery { setItems(any(), any(), any()) } coAnswers {
            val ownerKey: String = firstArg()
            val add: List<String> = arg(1)
            val remove: Set<String> = arg(2)
            state.itemsByOwner.update { current ->
                val remaining: List<PopupItem> = current[ownerKey].orEmpty().filterNot { item -> item.launchable.key in remove }
                val additions: List<PopupItem> = add.mapNotNull(resolveLaunchable).map { launchable -> PopupItem.Launchable(launchable) }
                current + (ownerKey to remaining + additions)
            }
        }
        coEvery { removeItem(any(), any()) } coAnswers {
            val ownerKey: String = firstArg()
            val itemKey: String = arg(1)
            state.itemsByOwner.update { current ->
                current + (ownerKey to current[ownerKey].orEmpty().filterNot { item -> item.launchable.key == itemKey })
            }
        }
        coEvery { setItemOrder(any(), any()) } coAnswers {
            val ownerKey: String = firstArg()
            val orderedItemKeys: List<String> = arg(1)
            state.itemsByOwner.update { current ->
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
        coEvery { deleteItemsByKey(any()) } coAnswers {
            val itemKeys: List<String> = firstArg()
            state.itemsByOwner.update { current ->
                current.mapValues { (_, items) -> items.filterNot { item -> item.launchable.key in itemKeys } }
            }
        }
        coEvery { deleteOwner(any()) } coAnswers {
            val ownerKey: String = firstArg()
            state.hiddenIdsByOwner.update { current -> current - ownerKey }
            state.itemsByOwner.update { current -> current - ownerKey }
            emptyList()
        }
        every { removedWidgetIds } returns state.removedWidgetIds
    }
