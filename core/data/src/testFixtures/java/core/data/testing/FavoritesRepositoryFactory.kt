package core.data.testing

import core.data.favorites.IFavoritesRepositoryImpl
import core.data.launchables.ILaunchableRepository
import core.data.room.dao.FavoriteDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** The production Favorites repository over [favoriteDao], resolved on the test's background scope. */
fun TestScope.createFavoritesRepository(favoriteDao: FavoriteDao, launchableRepository: ILaunchableRepository): IFavoritesRepositoryImpl =
    IFavoritesRepositoryImpl(
        favoriteDao = favoriteDao,
        launchableRepository = launchableRepository,
        scope = backgroundScope
    )

/** The production Favorites repository resolved eagerly, so ViewModel tests need no scheduler advance. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.createEagerFavoritesRepository(
    favoriteDao: FavoriteDao,
    launchableRepository: ILaunchableRepository
): IFavoritesRepositoryImpl = IFavoritesRepositoryImpl(
    favoriteDao = favoriteDao,
    launchableRepository = launchableRepository,
    scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
)
