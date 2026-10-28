package core.data.favorites

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.room.dao.FavoriteDao
import core.data.testing.FIRST_POSITION
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createFavoritesRepository
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.failOnRequest
import core.data.testing.failOnce
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEFAULT_RES_ID: Int = 0
private const val STALE_KEY: String = "com.example.stale"
private const val SECOND_POSITION: Int = 1
private const val RESOLUTION_TIMEOUT_MS: Long = 500L
private const val INITIAL_RETRY_DELAY_MS: Long = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class IFavoritesRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputLaunchable: Launchable = createLaunchable(name = "App")

    @Test
    fun `drops repeated Favorites values`() = databaseTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val removalAttempted: CompletableDeferred<Unit> = CompletableDeferred()
        val mockFavoriteDao: FavoriteDao = database.getFavoriteDao().failOnce(
            failure = IllegalStateException("Favorite removal failed"),
            onFailure = { removalAttempted.complete(Unit) },
            stub = { dao -> dao.remove(any()) },
            delegate = { dao -> dao.remove(firstArg()) }
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable))
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable))

        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            // A stale record changes the resolution but not the Favorites list.
            mockFavoriteDao.add(STALE_KEY)
            removalAttempted.await()
            finishPendingWork()
            expectNoEvents()
        }
    }

    @Test
    fun `collecting Favorites does not remove stale records`() = databaseTest {
        val inputStaleLaunchable: Launchable = createLaunchable(name = "Stale")
        val inputRecords: List<FavoriteRecord> = listOf(
            FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION),
            FavoriteRecord(key = inputStaleLaunchable.key, position = SECOND_POSITION)
        )
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        seedFavorite(key = inputStaleLaunchable.key, position = SECOND_POSITION)
        val removalAttempted: CompletableDeferred<Unit> = CompletableDeferred()
        // The repository-owned cleanup removes stale rows regardless of collection;
        // fail it so the stale row survives and collection is observed in isolation.
        val mockFavoriteDao: FavoriteDao = database.getFavoriteDao().failOnce(
            failure = IllegalStateException("Favorite removal failed"),
            onFailure = { removalAttempted.complete(Unit) },
            stub = { dao -> dao.remove(any()) },
            delegate = { dao -> dao.remove(firstArg()) }
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable))
        )
        removalAttempted.await()

        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            finishPendingWork()
            expectNoEvents()
        }

        assertEquals(inputRecords, favoriteRecords())
    }

    @Test
    fun `removes a Favorite when its Launchable disappears`() = databaseTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputLaunchable)))
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(favoriteDao = database.getFavoriteDao(), mockLaunchableRepository = mockLaunchableRepository)
        finishPendingWork()

        launchableState.value = createLaunchableState(groups = emptyList())

        assertNoFavorites()
    }

    @Test
    fun `does not restore a removed Favorite when its Launchable reappears`() = databaseTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputLaunchable)))
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        // Signalling on the repository's own collector keeps its cached records in step with the purge.
        val recordsPurged: CompletableDeferred<Unit> = CompletableDeferred()
        val mockFavoriteDao: FavoriteDao = spyk(database.getFavoriteDao())
        every { mockFavoriteDao.observeAll() } returns database.getFavoriteDao().observeAll().onEach { records ->
            if (records.isEmpty()) recordsPurged.complete(Unit)
        }
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = mockLaunchableRepository
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable))

        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            launchableState.value = createLaunchableState(groups = emptyList())
            assertEquals(emptyList<Launchable>(), awaitItem())
            recordsPurged.await()
            launchableState.value = createLoadedLaunchableState(listOf(inputLaunchable))
            finishPendingWork()
            expectNoEvents()
        }

        assertNoFavorites()
    }

    @Test
    fun `does not remove Favorites while the Launchables are loading`() = databaseTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(favoriteDao = database.getFavoriteDao(), mockLaunchableRepository = mockLaunchableRepository)
        finishPendingWork()
        assertEquals(inputRecords, favoriteRecords())

        launchableState.value = createLoadedLaunchableState(listOf(inputLaunchable))
        finishPendingWork()
        assertEquals(inputRecords, favoriteRecords())
    }

    @Test
    fun `reports whether a Launchable is a Favorite`() = databaseTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = database.getFavoriteDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable, inputOtherLaunchable))
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable))

        assertTrue(repository.isFavorite(inputLaunchable).first())
        assertFalse(repository.isFavorite(inputOtherLaunchable).first())
    }

    @Test
    fun `reports a stale record as not a Favorite`() = databaseTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val removalAttempted: CompletableDeferred<Unit> = CompletableDeferred()
        // The repository-owned cleanup removes stale rows regardless of collection;
        // fail it so the stale row survives and isFavorite must ignore it.
        val mockFavoriteDao: FavoriteDao = database.getFavoriteDao().failOnce(
            failure = IllegalStateException("Favorite removal failed"),
            onFailure = { removalAttempted.complete(Unit) },
            stub = { dao -> dao.remove(any()) },
            delegate = { dao -> dao.remove(firstArg()) }
        )
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOtherLaunchable))
        )
        removalAttempted.await()

        assertFalse(repository.isFavorite(inputLaunchable).first())
        assertEquals(inputRecords, favoriteRecords())
    }

    @Test
    fun `reflects an added Favorite in isFavorite immediately`() = databaseTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        seedFavorite(key = inputOtherLaunchable.key, position = FIRST_POSITION)
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = database.getFavoriteDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable, inputOtherLaunchable))
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputOtherLaunchable))

        repository.add(inputLaunchable)

        assertTrue(repository.isFavorite(inputLaunchable).first())
    }

    @Test
    fun `reflects a removed Favorite in isFavorite immediately`() = databaseTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = database.getFavoriteDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable))
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable))

        repository.remove(inputLaunchable)

        assertFalse(repository.isFavorite(inputLaunchable).first())
    }

    @Test
    fun `appends an added Favorite after the stored ones and never twice`() = databaseTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        val repository: IFavoritesRepositoryImpl = seedFavorites(
            storedFavorites = listOf(inputLaunchable),
            launchables = listOf(inputLaunchable, inputOtherLaunchable)
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable))

        repository.add(inputOtherLaunchable)
        repository.add(inputOtherLaunchable)

        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable, inputOtherLaunchable))
        assertEquals(listOf(inputLaunchable.key, inputOtherLaunchable.key), favoriteKeys())
    }

    @Test
    fun `stores the Favorites in the reordered order`() = databaseTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        val repository: IFavoritesRepositoryImpl = seedFavorites(storedFavorites = listOf(inputLaunchable, inputOtherLaunchable))
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable, inputOtherLaunchable))

        repository.reorder(listOf(inputOtherLaunchable, inputLaunchable))

        assertResolvedFavorites(repository = repository, expected = listOf(inputOtherLaunchable, inputLaunchable))
        assertEquals(listOf(inputOtherLaunchable.key, inputLaunchable.key), favoriteKeys())
    }

    @Test
    fun `stops waiting for a Favorite to become visible after a timeout`() = databaseTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        seedFavorite(key = inputOtherLaunchable.key, position = FIRST_POSITION)
        val mockFavoriteDao: FavoriteDao = spyk(database.getFavoriteDao())
        coEvery { mockFavoriteDao.add(any()) } just Runs
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable, inputOtherLaunchable))
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputOtherLaunchable))

        val actualAddJob: Deferred<Unit> = async { repository.add(inputLaunchable) }
        runCurrent()
        advanceTimeBy(RESOLUTION_TIMEOUT_MS - 1)
        assertFalse(actualAddJob.isCompleted)
        advanceTimeBy(2)
        assertTrue(actualAddJob.isCompleted)
    }

    @Test
    fun `keeps removing stale Favorites after a failed remove`() = databaseTest {
        val inputStaleLaunchable: Launchable = createLaunchable(name = "Stale")
        val inputRemainingLaunchable: Launchable = createLaunchable(name = "Remaining")
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputStaleLaunchable.key, position = FIRST_POSITION))
        seedFavorite(key = inputStaleLaunchable.key, position = FIRST_POSITION)
        val firstRemovalAttempted: CompletableDeferred<Unit> = CompletableDeferred()
        val mockFavoriteDao: FavoriteDao = database.getFavoriteDao().failOnce(
            failure = IllegalStateException("Favorite removal failed"),
            onFailure = { firstRemovalAttempted.complete(Unit) },
            stub = { dao -> dao.remove(any()) },
            delegate = { dao -> dao.remove(firstArg()) }
        )
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(listOf(inputStaleLaunchable, inputRemainingLaunchable))
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(favoriteDao = mockFavoriteDao, mockLaunchableRepository = mockLaunchableRepository)
        launchableState.value = createLoadedLaunchableState(listOf(inputRemainingLaunchable))
        firstRemovalAttempted.await()
        finishPendingWork()
        assertEquals(inputRecords, favoriteRecords())

        mockFavoriteDao.add(inputRemainingLaunchable.key)
        finishPendingWork()
        assertEquals(
            listOf(FavoriteRecord(key = inputRemainingLaunchable.key, position = SECOND_POSITION)),
            favoriteRecords()
        )

        launchableState.value = createLaunchableState(groups = emptyList())

        assertNoFavorites()
    }

    @Test
    fun `restarts a failed upstream with exponential backoff and recovers`() = databaseTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val mockFavoriteDao: FavoriteDao = spyk(database.getFavoriteDao())
        val failureRequests: Channel<Unit> = Channel(Channel.UNLIMITED)
        val failureDelivered: CompletableDeferred<Unit> = CompletableDeferred()
        every { mockFavoriteDao.observeAll() } returns database.getFavoriteDao().observeAll()
            .failOnRequest(failureRequests = failureRequests, onFailure = { failureDelivered.complete(Unit) })
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable, inputOtherLaunchable))
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable))
        failureRequests.send(Unit)
        failureDelivered.await()
        runCurrent()
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
    fun `shares one Favorites subscription between readers and the cleanup job`() = databaseTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val mockFavoriteDao: FavoriteDao = spyk(database.getFavoriteDao())
        var actualSubscriptions: Int = 0
        every { mockFavoriteDao.observeAll() } returns database.getFavoriteDao().observeAll().onStart { actualSubscriptions++ }
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable))
        )
        assertResolvedFavorites(repository = repository, expected = listOf(inputLaunchable))
        repository.isFavorite(inputLaunchable).first()
        repository.isFavorite(inputLaunchable).first()
        finishPendingWork()
        assertEquals(1, actualSubscriptions)
    }

    private suspend fun TestScope.assertNoFavorites() {
        finishPendingWork()
        assertTrue(favoriteRecords().isEmpty())
    }

    private suspend fun TestScope.assertResolvedFavorites(repository: IFavoritesRepositoryImpl, expected: List<Launchable>) {
        finishPendingWork()
        assertEquals(expected, repository.favorites.first())
    }

    private fun TestScope.createRepository(
        favoriteDao: FavoriteDao,
        mockLaunchableRepository: ILaunchableRepository
    ): IFavoritesRepositoryImpl = createFavoritesRepository(
        favoriteDao = favoriteDao,
        launchableRepository = mockLaunchableRepository
    )

    /** Seeds [storedFavorites] as the stored Favorites and returns the repository over [launchables]. */
    private suspend fun TestScope.seedFavorites(
        storedFavorites: List<Launchable>,
        launchables: List<Launchable> = storedFavorites
    ): IFavoritesRepositoryImpl {
        storedFavorites.forEachIndexed { position, favorite -> seedFavorite(key = favorite.key, position = position) }
        return createRepository(
            favoriteDao = database.getFavoriteDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = launchables)
        )
    }

    /** The stored Favorite keys in their stored order. */
    private suspend fun favoriteKeys(): List<String> = favoriteRecords().sortedBy { record -> record.position }.map { record -> record.key }
}

private fun createLaunchable(name: String): Launchable = RailLaunchable(
    defaultName = name,
    defaultIcon = Icon.RailIcon(resId = DEFAULT_RES_ID),
    deeplink = "rail://$name"
)
