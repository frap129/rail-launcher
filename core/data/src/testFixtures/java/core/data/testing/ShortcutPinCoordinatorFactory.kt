package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutPinCoordinatorImpl
import core.data.shortcuts.IShortcutPinSource
import core.data.shortcuts.IShortcutSource
import kotlinx.coroutines.CoroutineScope

/** The production Shortcut pin coordinator over [popupDao], with its background work on [scope]. */
fun createShortcutPinCoordinator(
    popupDao: PopupDao,
    launchableRepository: ILaunchableRepository,
    scope: CoroutineScope,
    shortcutSource: IShortcutSource,
    pinSources: List<IShortcutPinSource> = emptyList()
): IShortcutPinCoordinatorImpl = IShortcutPinCoordinatorImpl(
    shortcutSource = shortcutSource,
    launchableRepository = launchableRepository,
    popupDao = popupDao,
    pinSources = pinSources,
    scope = scope
)
