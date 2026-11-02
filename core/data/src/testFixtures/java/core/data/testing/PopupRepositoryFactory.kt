package core.data.testing

import core.data.cleanup.PopupItemCleanupRow
import core.data.cleanup.PopupOwnerCleanupRow
import core.data.launchables.ILaunchableRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.profiles.IPrivateSpaceStatus
import core.data.room.dao.PopupDao
import core.data.shortcuts.IPopupShortcutPinSourceImpl
import core.data.shortcuts.IShortcutPinCoordinator
import core.data.shortcuts.IShortcutSource
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveOwnerWidgetsImpl
import core.data.widgets.IWidgetAreaRepository
import kotlinx.coroutines.CoroutineScope

/** The production Pop-up repository over [popupDao], with its background work and stale-key cleanup on [scope]. */
fun createPopupRepository(
    popupDao: PopupDao,
    launchableRepository: ILaunchableRepository,
    scope: CoroutineScope,
    shortcutSource: IShortcutSource,
    widgetAreaRepository: IWidgetAreaRepository,
    appWidgetHost: IAppWidgetHostSource,
    privateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatusMock(),
    shortcutPinCoordinator: IShortcutPinCoordinator = createShortcutPinCoordinator(
        launchableRepository = launchableRepository,
        privateSpaceStatus = privateSpaceStatus,
        scope = scope,
        shortcutSource = shortcutSource,
        pinSources = listOf(IPopupShortcutPinSourceImpl(popupDao = popupDao))
    )
): IPopupRepositoryImpl {
    val storedKeyClassifier = createStoredKeyClassifier(launchableRepository = launchableRepository, shortcutSource = shortcutSource)
    val popupRepository: IPopupRepositoryImpl = IPopupRepositoryImpl(
        popupDao = popupDao,
        launchableRepository = launchableRepository,
        storedKeyClassifier = storedKeyClassifier,
        shortcutPinCoordinator = shortcutPinCoordinator,
        scope = scope,
        widgetAreaRepository = widgetAreaRepository,
        removeOwnerWidgets = IRemoveOwnerWidgetsImpl(repository = widgetAreaRepository, host = appWidgetHost)
    )
    createStaleKeyCleanup(
        classifier = storedKeyClassifier,
        rows = listOf(
            PopupOwnerCleanupRow(popupDao = popupDao, widgetAreaRepository = widgetAreaRepository, popupRepository = popupRepository),
            PopupItemCleanupRow(popupDao = popupDao, popupRepository = popupRepository)
        ),
        scope = scope
    )
    return popupRepository
}
