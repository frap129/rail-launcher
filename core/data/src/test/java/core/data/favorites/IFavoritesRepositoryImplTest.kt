package core.data.favorites

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.testing.FIRST_POSITION
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeLaunchableRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEFAULT_RES_ID: Int = 0
private const val SECOND_POSITION: Int = 1
private const val RESOLUTION_TIMEOUT_MS: Long = 500L

@OptIn(ExperimentalCoroutinesApi::class)
class IFavoritesRepositoryImplTest {
    private val launchable: Launchable = createLaunchable(name = "App")

    @Test
    fun `drops repeated Favorites values`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(inputRecords)
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
            )
        )
        runCurrent()
        repository.favorites.test {
            assertEquals(listOf(launchable), awaitItem())
            favoriteDao.reemit(inputRecords)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `collecting Favorites does not remove stale records`() = runTest {
        val staleLaunchable: Launchable = createLaunchable(name = "Stale")
        val inputRecords: List<FavoriteRecord> = listOf(
            FavoriteRecord(key = launchable.key, position = FIRST_POSITION),
            FavoriteRecord(key = staleLaunchable.key, position = SECOND_POSITION)
        )
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        // The repository-owned cleanup removes stale rows regardless of collection;
        // fail it so the stale row survives and collection is observed in isolation.
        favoriteDao.failNextRemove = IllegalStateException("Favorite removal failed")
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
            )
        )
        runCurrent()
        repository.favorites.test {
            assertEquals(listOf(launchable), awaitItem())
            expectNoEvents()
        }
        assertEquals(inputRecords, favoriteDao.records)
    }

    @Test
    fun `removes a Favorite when its Launchable disappears`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
        )
        createRepository(favoriteDao = favoriteDao, launchableRepository = launchableRepository)
        launchableRepository.setGroups(groups = emptyList())
        runCurrent()
        assertTrue(favoriteDao.records.isEmpty())
    }

    @Test
    fun `does not restore a removed Favorite when its Launchable reappears`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = launchableRepository
        )
        runCurrent()
        repository.favorites.test {
            assertEquals(listOf(launchable), awaitItem())
            launchableRepository.setGroups(groups = emptyList())
            assertEquals(emptyList<Launchable>(), awaitItem())
            runCurrent()
            launchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable))))
            runCurrent()
            expectNoEvents()
        }
        assertTrue(favoriteDao.records.isEmpty())
    }

    @Test
    fun `does not remove Favorites while the Launchables are loading`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        createRepository(favoriteDao = favoriteDao, launchableRepository = launchableRepository)
        runCurrent()
        assertEquals(inputRecords, favoriteDao.records)
        launchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable))))
        runCurrent()
        assertEquals(inputRecords, favoriteDao.records)
    }

    @Test
    fun `reports whether a Launchable is a Favorite`() = runTest {
        val otherLaunchable: Launchable = createLaunchable(name = "Other")
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable, otherLaunchable)))
            )
        )
        runCurrent()
        assertTrue(repository.isFavorite(launchable).first())
        assertFalse(repository.isFavorite(otherLaunchable).first())
    }

    @Test
    fun `reports a stale record as not a Favorite`() = runTest {
        val otherLaunchable: Launchable = createLaunchable(name = "Other")
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        )
        // The repository-owned cleanup removes stale rows regardless of collection;
        // fail it so the stale row survives and isFavorite must ignore it.
        favoriteDao.failNextRemove = IllegalStateException("Favorite removal failed")
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(otherLaunchable)))
            )
        )
        runCurrent()
        assertEquals(1, favoriteDao.records.size)
        assertFalse(repository.isFavorite(launchable).first())
    }

    @Test
    fun `reflects an added Favorite in isFavorite immediately`() = runTest {
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
            )
        )
        runCurrent()
        repository.add(launchable)
        assertTrue(repository.isFavorite(launchable).first())
    }

    @Test
    fun `reflects a removed Favorite in isFavorite immediately`() = runTest {
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
            )
        )
        runCurrent()
        repository.remove(launchable)
        assertFalse(repository.isFavorite(launchable).first())
    }

    @Test
    fun `stops waiting for a Favorite to become visible after a timeout`() = runTest {
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        favoriteDao.isAddEnabled = false
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
            )
        )
        runCurrent()
        val addJob: Deferred<Unit> = async { repository.add(launchable) }
        runCurrent()
        advanceTimeBy(RESOLUTION_TIMEOUT_MS - 1)
        assertFalse(addJob.isCompleted)
        advanceTimeBy(2)
        assertTrue(addJob.isCompleted)
    }

    @Test
    fun `keeps removing stale Favorites after a failed remove`() = runTest {
        val staleLaunchable: Launchable = createLaunchable(name = "Stale")
        val remainingLaunchable: Launchable = createLaunchable(name = "Remaining")
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = staleLaunchable.key, position = FIRST_POSITION))
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        favoriteDao.failNextRemove = IllegalStateException("Favorite removal failed")
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(staleLaunchable, remainingLaunchable)))
        )
        createRepository(favoriteDao = favoriteDao, launchableRepository = launchableRepository)
        launchableRepository.setGroups(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(remainingLaunchable)))
        )
        runCurrent()
        assertEquals(inputRecords, favoriteDao.records)
        favoriteDao.add(remainingLaunchable.key)
        runCurrent()
        assertEquals(
            listOf(FavoriteRecord(key = remainingLaunchable.key, position = SECOND_POSITION)),
            favoriteDao.records
        )
        launchableRepository.setGroups(groups = emptyList())
        runCurrent()
        assertTrue(favoriteDao.records.isEmpty())
    }

    @Test
    fun `shares one Favorites subscription between readers and the cleanup job`() = runTest {
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = launchable.key, position = FIRST_POSITION))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = favoriteDao,
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(launchable)))
            )
        )
        runCurrent()
        repository.favorites.first()
        repository.isFavorite(launchable).first()
        repository.isFavorite(launchable).first()
        runCurrent()
        assertEquals(1, favoriteDao.observeAllSubscriptionCount)
    }
}

private fun TestScope.createRepository(
    favoriteDao: FakeFavoriteDao,
    launchableRepository: FakeLaunchableRepository
): IFavoritesRepositoryImpl = IFavoritesRepositoryImpl(
    favoriteDao = favoriteDao,
    launchableRepository = launchableRepository,
    scope = backgroundScope
)

private fun createLaunchable(name: String): Launchable = RailLaunchable(
    defaultName = name,
    defaultIcon = Icon.RailIcon(resId = DEFAULT_RES_ID),
    deeplink = "rail://$name"
)
