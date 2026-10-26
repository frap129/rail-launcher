package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutSource
import core.data.widgets.IRemoveOwnerWidgetsImpl
import core.data.widgets.IWidgetAreaRepository
import kotlinx.coroutines.CoroutineScope

/** The production Pop-up repository over [popupDao], with its background work on [scope]. */
fun createPopupRepository(
    popupDao: PopupDao,
    launchableRepository: ILaunchableRepository,
    scope: CoroutineScope,
    shortcutSource: IShortcutSource = FakeShortcutSource(),
    widgetAreaRepository: IWidgetAreaRepository = FakeWidgetAreaRepository(),
    appWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
): IPopupRepositoryImpl = IPopupRepositoryImpl(
    popupDao = popupDao,
    launchableRepository = launchableRepository,
    shortcutSource = shortcutSource,
    scope = scope,
    widgetAreaRepository = widgetAreaRepository,
    removeOwnerWidgets = IRemoveOwnerWidgetsImpl(repository = widgetAreaRepository, host = appWidgetHost)
)
