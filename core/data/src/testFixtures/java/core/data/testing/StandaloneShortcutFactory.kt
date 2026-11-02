package core.data.testing

import core.data.profiles.IProfileSource
import core.data.room.dao.FavoriteDao
import core.data.room.dao.StandaloneShortcutDao
import core.data.shortcuts.IShortcutPinCoordinator
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.IStandaloneShortcutRepositoryImpl
import core.data.shortcuts.IStandaloneShortcutSource
import core.data.shortcuts.IStandaloneShortcutSourceImpl

/** The production Standalone shortcut source over [standaloneShortcutDao] and [shortcutSource]. */
fun createStandaloneShortcutSource(
    standaloneShortcutDao: StandaloneShortcutDao,
    shortcutSource: IShortcutSource,
    profileSource: IProfileSource = createProfileSourceMock()
): IStandaloneShortcutSource = IStandaloneShortcutSourceImpl(
    standaloneShortcutDao = standaloneShortcutDao,
    shortcutSource = shortcutSource,
    profileSource = profileSource
)

/** The production Standalone shortcut repository over [standaloneShortcutDao] and [favoriteDao]. */
fun createStandaloneShortcutRepository(
    standaloneShortcutDao: StandaloneShortcutDao,
    favoriteDao: FavoriteDao,
    shortcutSource: IShortcutSource,
    shortcutPinCoordinator: IShortcutPinCoordinator
): IStandaloneShortcutRepository = IStandaloneShortcutRepositoryImpl(
    source = createStandaloneShortcutSource(standaloneShortcutDao = standaloneShortcutDao, shortcutSource = shortcutSource),
    standaloneShortcutDao = standaloneShortcutDao,
    favoriteDao = favoriteDao,
    shortcutPinCoordinator = shortcutPinCoordinator
)
