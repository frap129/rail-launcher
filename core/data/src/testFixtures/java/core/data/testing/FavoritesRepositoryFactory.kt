package core.data.testing

import core.data.cleanup.favoriteCleanupRow
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.launchables.ILaunchableRepository
import core.data.room.dao.FavoriteDao
import core.data.shortcuts.IShortcutSource
import kotlinx.coroutines.test.TestScope

/** The production Favorites repository over [favoriteDao], with its stale rows cleaned on the test's background scope. */
fun TestScope.createFavoritesRepository(
    favoriteDao: FavoriteDao,
    launchableRepository: ILaunchableRepository,
    shortcutSource: IShortcutSource = createShortcutSourceMock()
): IFavoritesRepositoryImpl {
    createStaleKeyCleanup(
        launchableRepository = launchableRepository,
        shortcutSource = shortcutSource,
        rows = listOf(favoriteCleanupRow(favoriteDao = favoriteDao)),
        scope = backgroundScope
    )
    return IFavoritesRepositoryImpl(
        favoriteDao = favoriteDao,
        launchableRepository = launchableRepository,
        scope = backgroundScope
    )
}
