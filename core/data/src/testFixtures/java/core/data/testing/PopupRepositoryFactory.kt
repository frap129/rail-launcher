package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.popups.IPopupRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutSource
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveOwnerWidgetsImpl
import core.data.widgets.IWidgetAreaRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

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
fun createPopupRepositoryMock(notificationsEnabled: Boolean = false): IPopupRepository {
    val notificationsEnabledState: MutableStateFlow<Boolean> = MutableStateFlow(notificationsEnabled)
    return mockk<IPopupRepository>().apply {
        every { this@apply.notificationsEnabled } returns notificationsEnabledState
        coEvery { setNotificationsEnabled(any()) } coAnswers { notificationsEnabledState.value = firstArg() }
        coEvery { setItems(any(), any(), any()) } just Runs
        coEvery { deleteItemsByKey(any()) } just Runs
        coEvery { deleteOwner(any()) } returns emptyList()
    }
}
