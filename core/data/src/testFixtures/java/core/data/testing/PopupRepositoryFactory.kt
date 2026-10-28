package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.popups.IPopupRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.popups.PopupItem
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveOwnerWidgetsImpl
import core.data.widgets.IWidgetAreaRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The Pop-up state a [createPopupRepositoryMock] repository reports. */
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

/** A MockK [IPopupRepository] reporting [notificationsEnabled], with successful writes and no freed App Widgets. */
fun createPopupRepositoryMock(notificationsEnabled: Boolean = false): IPopupRepository = createPopupRepositoryMock(
    state = PopupRepositoryState(notificationsEnabled = MutableStateFlow(notificationsEnabled))
)

/** A MockK [IPopupRepository] over [state], with successful writes and no freed App Widgets. */
fun createPopupRepositoryMock(state: PopupRepositoryState): IPopupRepository = mockk<IPopupRepository>().apply {
    every { notificationsEnabled } returns state.notificationsEnabled
    coEvery { setNotificationsEnabled(any()) } just Runs
    every { hiddenShortcuts(any()) } answers {
        val ownerKey: String = firstArg()
        state.hiddenIdsByOwner.map { hiddenIds -> hiddenIds[ownerKey] ?: emptySet() }
    }
    coEvery { setShortcutHidden(any(), any(), any()) } just Runs
    every { items(any()) } answers {
        val ownerKey: String = firstArg()
        state.itemsByOwner.map { itemsByOwner -> itemsByOwner[ownerKey] ?: emptyList() }
    }
    every { itemCounts } answers {
        state.itemsByOwner.map { itemsByOwner -> itemsByOwner.mapValues { (_, ownerItems) -> ownerItems.size } }
    }
    coEvery { setItems(any(), any(), any()) } just Runs
    coEvery { removeItem(any(), any()) } just Runs
    coEvery { setItemOrder(any(), any()) } just Runs
    coEvery { deleteItemsByKey(any()) } just Runs
    coEvery { deleteOwner(any()) } returns emptyList()
    every { removedWidgetIds } returns state.removedWidgetIds
}
