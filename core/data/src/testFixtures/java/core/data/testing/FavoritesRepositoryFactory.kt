package core.data.testing

import core.data.favorites.IFavoritesRepositoryImpl
import core.data.launchables.ILaunchableRepository
import core.data.room.dao.FavoriteDao
import kotlinx.coroutines.test.TestScope

/** The production Favorites repository over [favoriteDao], resolved on the test's background scope. */
fun TestScope.createFavoritesRepository(favoriteDao: FavoriteDao, launchableRepository: ILaunchableRepository): IFavoritesRepositoryImpl =
    IFavoritesRepositoryImpl(
        favoriteDao = favoriteDao,
        launchableRepository = launchableRepository,
        scope = backgroundScope
    )
