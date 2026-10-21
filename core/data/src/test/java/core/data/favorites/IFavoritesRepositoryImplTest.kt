package core.data.favorites

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.testing.FIRST_POSITION
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeLaunchableRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DEFAULT_RES_ID: Int = 0

@OptIn(ExperimentalCoroutinesApi::class)
class IFavoritesRepositoryImplTest {
    @Test
    fun `drops repeated Favorites values`() = runTest {
        val launchable: Launchable = RailLaunchable(
            defaultName = "App",
            defaultIcon = Icon.RailIcon(resId = DEFAULT_RES_ID),
            deeplink = "rail://app"
        )
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(inputRecords)
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
        )
        val repository: IFavoritesRepositoryImpl = IFavoritesRepositoryImpl(
            favoriteDao = favoriteDao,
            launchableRepository = launchableRepository
        )

        repository.favorites.test {
            assertEquals(listOf(launchable), awaitItem())
            favoriteDao.reemit(inputRecords)
            runCurrent()
            expectNoEvents()
        }
    }
}
