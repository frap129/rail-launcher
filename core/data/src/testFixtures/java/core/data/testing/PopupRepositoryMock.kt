package core.data.testing

import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.widgets.AppWidgetId
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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

/** A MockK [IPopupRepository] reporting [notificationsEnabled], with successful writes and no freed App Widgets. */
fun createPopupRepositoryMock(notificationsEnabled: Boolean = false): IPopupRepository = createPopupRepositoryMock(
    state = PopupRepositoryState(notificationsEnabled = MutableStateFlow(notificationsEnabled))
)

/** A MockK [IPopupRepository] over [state], with successful writes and no freed App Widgets. */
fun createPopupRepositoryMock(state: PopupRepositoryState): IPopupRepository {
    val mockPopupRepository: IPopupRepository = mockk()
    every { mockPopupRepository.notificationsEnabled } returns state.notificationsEnabled
    coEvery { mockPopupRepository.setNotificationsEnabled(any()) } just Runs
    every { mockPopupRepository.hiddenShortcuts(any()) } answers {
        val ownerKey: String = firstArg()
        state.hiddenIdsByOwner.map { hiddenIds -> hiddenIds[ownerKey] ?: emptySet() }
    }
    coEvery { mockPopupRepository.setShortcutHidden(any(), any(), any()) } just Runs
    every { mockPopupRepository.items(any()) } answers {
        val ownerKey: String = firstArg()
        state.itemsByOwner.map { itemsByOwner -> itemsByOwner[ownerKey] ?: emptyList() }
    }
    every { mockPopupRepository.itemCounts } answers {
        state.itemsByOwner.map { itemsByOwner -> itemsByOwner.mapValues { (_, ownerItems) -> ownerItems.size } }
    }
    coEvery { mockPopupRepository.setItems(any(), any(), any()) } just Runs
    coEvery { mockPopupRepository.removeItem(any(), any()) } just Runs
    coEvery { mockPopupRepository.setItemOrder(any(), any()) } just Runs
    coEvery { mockPopupRepository.deleteItemsByKey(any()) } just Runs
    coEvery { mockPopupRepository.deleteOwner(any()) } returns emptyList()
    every { mockPopupRepository.removedWidgetIds } returns state.removedWidgetIds
    return mockPopupRepository
}
