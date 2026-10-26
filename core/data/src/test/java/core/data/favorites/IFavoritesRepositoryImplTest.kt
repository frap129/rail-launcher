package core.data.favorites

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.testing.FIRST_POSITION
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeLaunchableRepository
import core.data.testing.createFavoritesRepository
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
private const val INITIAL_RETRY_DELAY_MS: Long = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class IFavoritesRepositoryImplTest {
    private val inputLaunchable: Launchable = createLaunchable(name = "App")

    @Test
    fun `drops repeated Favorites values`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(inputRecords)
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
            )
        )
        runCurrent()
        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            mockFavoriteDao.reemit(inputRecords)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `collecting Favorites does not remove stale records`() = runTest {
        val inputStaleLaunchable: Launchable = createLaunchable(name = "Stale")
        val inputRecords: List<FavoriteRecord> = listOf(
            FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION),
            FavoriteRecord(key = inputStaleLaunchable.key, position = SECOND_POSITION)
        )
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        // The repository-owned cleanup removes stale rows regardless of collection;
        // fail it so the stale row survives and collection is observed in isolation.
        mockFavoriteDao.failNextRemove.failWith(IllegalStateException("Favorite removal failed"))
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
            )
        )
        runCurrent()
        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            expectNoEvents()
        }
        assertEquals(inputRecords, mockFavoriteDao.records)
    }

    @Test
    fun `removes a Favorite when its Launchable disappears`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
        )
        createRepository(mockFavoriteDao = mockFavoriteDao, mockLaunchableRepository = mockLaunchableRepository)
        mockLaunchableRepository.setGroups(groups = emptyList())
        runCurrent()
        assertTrue(mockFavoriteDao.records.isEmpty())
    }

    @Test
    fun `does not restore a removed Favorite when its Launchable reappears`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = mockLaunchableRepository
        )
        runCurrent()
        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            mockLaunchableRepository.setGroups(groups = emptyList())
            assertEquals(emptyList<Launchable>(), awaitItem())
            runCurrent()
            mockLaunchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable))))
            runCurrent()
            expectNoEvents()
        }
        assertTrue(mockFavoriteDao.records.isEmpty())
    }

    @Test
    fun `does not remove Favorites while the Launchables are loading`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        createRepository(mockFavoriteDao = mockFavoriteDao, mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()
        assertEquals(inputRecords, mockFavoriteDao.records)
        mockLaunchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable))))
        runCurrent()
        assertEquals(inputRecords, mockFavoriteDao.records)
    }

    @Test
    fun `reports whether a Launchable is a Favorite`() = runTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable, inputOtherLaunchable)))
            )
        )
        runCurrent()
        assertTrue(repository.isFavorite(inputLaunchable).first())
        assertFalse(repository.isFavorite(inputOtherLaunchable).first())
    }

    @Test
    fun `reports a stale record as not a Favorite`() = runTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        )
        // The repository-owned cleanup removes stale rows regardless of collection;
        // fail it so the stale row survives and isFavorite must ignore it.
        mockFavoriteDao.failNextRemove.failWith(IllegalStateException("Favorite removal failed"))
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputOtherLaunchable)))
            )
        )
        runCurrent()
        assertEquals(1, mockFavoriteDao.records.size)
        assertFalse(repository.isFavorite(inputLaunchable).first())
    }

    @Test
    fun `reflects an added Favorite in isFavorite immediately`() = runTest {
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
            )
        )
        runCurrent()
        repository.add(inputLaunchable)
        assertTrue(repository.isFavorite(inputLaunchable).first())
    }

    @Test
    fun `reflects a removed Favorite in isFavorite immediately`() = runTest {
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
            )
        )
        runCurrent()
        repository.remove(inputLaunchable)
        assertFalse(repository.isFavorite(inputLaunchable).first())
    }

    @Test
    fun `stops waiting for a Favorite to become visible after a timeout`() = runTest {
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        mockFavoriteDao.isAddEnabled = false
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
            )
        )
        runCurrent()
        val actualAddJob: Deferred<Unit> = async { repository.add(inputLaunchable) }
        runCurrent()
        advanceTimeBy(RESOLUTION_TIMEOUT_MS - 1)
        assertFalse(actualAddJob.isCompleted)
        advanceTimeBy(2)
        assertTrue(actualAddJob.isCompleted)
    }

    @Test
    fun `keeps removing stale Favorites after a failed remove`() = runTest {
        val inputStaleLaunchable: Launchable = createLaunchable(name = "Stale")
        val inputRemainingLaunchable: Launchable = createLaunchable(name = "Remaining")
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputStaleLaunchable.key, position = FIRST_POSITION))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        mockFavoriteDao.failNextRemove.failWith(IllegalStateException("Favorite removal failed"))
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputStaleLaunchable, inputRemainingLaunchable)))
        )
        createRepository(mockFavoriteDao = mockFavoriteDao, mockLaunchableRepository = mockLaunchableRepository)
        mockLaunchableRepository.setGroups(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputRemainingLaunchable)))
        )
        runCurrent()
        assertEquals(inputRecords, mockFavoriteDao.records)
        mockFavoriteDao.add(inputRemainingLaunchable.key)
        runCurrent()
        assertEquals(
            listOf(FavoriteRecord(key = inputRemainingLaunchable.key, position = SECOND_POSITION)),
            mockFavoriteDao.records
        )
        mockLaunchableRepository.setGroups(groups = emptyList())
        runCurrent()
        assertTrue(mockFavoriteDao.records.isEmpty())
    }

    @Test
    fun `restarts a failed upstream with exponential backoff and recovers`() = runTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(initialRecords = inputRecords)
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable, inputOtherLaunchable)))
            )
        )
        runCurrent()
        assertEquals(listOf(inputLaunchable), repository.favorites.first())
        mockFavoriteDao.failNextObserve(IllegalStateException("Favorites upstream failure"))
        mockFavoriteDao.add(inputOtherLaunchable.key)
        runCurrent()
        assertEquals(listOf(inputLaunchable), repository.favorites.first())
        advanceTimeBy(INITIAL_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(listOf(inputLaunchable), repository.favorites.first())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(inputLaunchable, inputOtherLaunchable), repository.favorites.first())
    }

    @Test
    fun `shares one Favorites subscription between readers and the cleanup job`() = runTest {
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            mockFavoriteDao = mockFavoriteDao,
            mockLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputLaunchable)))
            )
        )
        runCurrent()
        repository.favorites.first()
        repository.isFavorite(inputLaunchable).first()
        repository.isFavorite(inputLaunchable).first()
        runCurrent()
        assertEquals(1, mockFavoriteDao.observeAllSubscriptionCount)
    }
}

private fun TestScope.createRepository(
    mockFavoriteDao: FakeFavoriteDao,
    mockLaunchableRepository: FakeLaunchableRepository
): IFavoritesRepositoryImpl = createFavoritesRepository(
    favoriteDao = mockFavoriteDao,
    launchableRepository = mockLaunchableRepository
)

private fun createLaunchable(name: String): Launchable = RailLaunchable(
    defaultName = name,
    defaultIcon = Icon.RailIcon(resId = DEFAULT_RES_ID),
    deeplink = "rail://$name"
)
