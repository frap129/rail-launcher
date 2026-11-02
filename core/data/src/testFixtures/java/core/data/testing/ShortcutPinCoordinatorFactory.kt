package core.data.testing

import core.data.launchables.ILaunchableRepository
import core.data.profiles.IPrivateSpaceStatus
import core.data.shortcuts.IShortcutPinCoordinator
import core.data.shortcuts.IShortcutPinCoordinatorImpl
import core.data.shortcuts.IShortcutPinSource
import core.data.shortcuts.IShortcutSource
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope

/** The production Shortcut pin coordinator over [pinSources], with its background work on [scope]. */
fun createShortcutPinCoordinator(
    launchableRepository: ILaunchableRepository,
    privateSpaceStatus: IPrivateSpaceStatus,
    scope: CoroutineScope,
    shortcutSource: IShortcutSource,
    pinSources: List<IShortcutPinSource>
): IShortcutPinCoordinatorImpl = IShortcutPinCoordinatorImpl(
    shortcutSource = shortcutSource,
    launchableRepository = launchableRepository,
    privateSpaceStatus = privateSpaceStatus,
    pinSources = pinSources,
    scope = scope
)

/** A relaxed [IShortcutPinCoordinator] test double that records reapply calls. */
fun createShortcutPinCoordinatorMock(): IShortcutPinCoordinator = mockk(relaxed = true)
