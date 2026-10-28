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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
    fun `drops repeated Favorites values`() = runTest {
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
        repository.favorites.first { favorites -> favorites.isNotEmpty() }

        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            // A stale record changes the resolution but not the Favorites list.
            mockFavoriteDao.add(STALE_KEY)
            removalAttempted.await()
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
            runCurrent()
            expectNoEvents()
        }

        assertEquals(inputRecords, favoriteRecords())
    }

    @Test
    fun `removes a Favorite when its Launchable disappears`() = runTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputLaunchable)))
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(favoriteDao = database.getFavoriteDao(), mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()

        launchableState.value = createLaunchableState(groups = emptyList())

        assertTrue(database.getFavoriteDao().observeAll().first { records -> records.isEmpty() }.isEmpty())
    }

    @Test
    fun `does not restore a removed Favorite when its Launchable reappears`() = runTest {
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
        repository.favorites.first { favorites -> favorites.isNotEmpty() }

        repository.favorites.test {
            assertEquals(listOf(inputLaunchable), awaitItem())
            launchableState.value = createLaunchableState(groups = emptyList())
            assertEquals(emptyList<Launchable>(), awaitItem())
            recordsPurged.await()
            launchableState.value = createLoadedLaunchableState(listOf(inputLaunchable))
            runCurrent()
            expectNoEvents()
        }

        assertTrue(database.getFavoriteDao().observeAll().first { records -> records.isEmpty() }.isEmpty())
    }

    @Test
    fun `does not remove Favorites while the Launchables are loading`() = runTest {
        val inputRecords: List<FavoriteRecord> = listOf(FavoriteRecord(key = inputLaunchable.key, position = FIRST_POSITION))
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(favoriteDao = database.getFavoriteDao(), mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()
        assertEquals(inputRecords, favoriteRecords())

        launchableState.value = createLoadedLaunchableState(listOf(inputLaunchable))
        runCurrent()
        assertEquals(inputRecords, favoriteRecords())
    }

    @Test
    fun `reports whether a Launchable is a Favorite`() = runTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = database.getFavoriteDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable, inputOtherLaunchable))
        )
        repository.favorites.first { favorites -> favorites.isNotEmpty() }

        assertTrue(repository.isFavorite(inputLaunchable).first())
        assertFalse(repository.isFavorite(inputOtherLaunchable).first())
    }

    @Test
    fun `reports a stale record as not a Favorite`() = runTest {
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
    fun `reflects an added Favorite in isFavorite immediately`() = runTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        seedFavorite(key = inputOtherLaunchable.key, position = FIRST_POSITION)
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = database.getFavoriteDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable, inputOtherLaunchable))
        )
        repository.favorites.first { favorites -> favorites.isNotEmpty() }

        writeAwaitingResolution { repository.add(inputLaunchable) }

        assertTrue(repository.isFavorite(inputLaunchable).first())
    }

    @Test
    fun `reflects a removed Favorite in isFavorite immediately`() = runTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = database.getFavoriteDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable))
        )
        repository.favorites.first { favorites -> favorites.isNotEmpty() }

        writeAwaitingResolution { repository.remove(inputLaunchable) }

        assertFalse(repository.isFavorite(inputLaunchable).first())
    }

    @Test
    fun `stops waiting for a Favorite to become visible after a timeout`() = runTest {
        val inputOtherLaunchable: Launchable = createLaunchable(name = "Other")
        seedFavorite(key = inputOtherLaunchable.key, position = FIRST_POSITION)
        val mockFavoriteDao: FavoriteDao = spyk(database.getFavoriteDao())
        coEvery { mockFavoriteDao.add(any()) } just Runs
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable, inputOtherLaunchable))
        )
        repository.favorites.first { favorites -> favorites.isNotEmpty() }

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
        runCurrent()
        assertEquals(inputRecords, favoriteRecords())

        mockFavoriteDao.add(inputRemainingLaunchable.key)
        runCurrent()
        assertEquals(
            listOf(FavoriteRecord(key = inputRemainingLaunchable.key, position = SECOND_POSITION)),
            database.getFavoriteDao().observeAll().first { records ->
                records.size == 1 &&
                    records.none { record -> record.key == inputStaleLaunchable.key }
            }
        )

        launchableState.value = createLaunchableState(groups = emptyList())
        runCurrent()
        assertTrue(database.getFavoriteDao().observeAll().first { records -> records.isEmpty() }.isEmpty())
    }

    @Test
    fun `restarts a failed upstream with exponential backoff and recovers`() = runTest {
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
        repository.favorites.first { favorites -> favorites.isNotEmpty() }
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
        assertEquals(listOf(inputLaunchable, inputOtherLaunchable), repository.favorites.first { favorites -> favorites.size == 2 })
    }

    @Test
    fun `shares one Favorites subscription between readers and the cleanup job`() = runTest {
        seedFavorite(key = inputLaunchable.key, position = FIRST_POSITION)
        val mockFavoriteDao: FavoriteDao = spyk(database.getFavoriteDao())
        var actualSubscriptions: Int = 0
        every { mockFavoriteDao.observeAll() } returns database.getFavoriteDao().observeAll().onStart { actualSubscriptions++ }
        val repository: IFavoritesRepositoryImpl = createRepository(
            favoriteDao = mockFavoriteDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputLaunchable))
        )
        repository.favorites.first { favorites -> favorites.isNotEmpty() }
        repository.isFavorite(inputLaunchable).first()
        repository.isFavorite(inputLaunchable).first()
        runCurrent()
        assertEquals(1, actualSubscriptions)
    }

    /**
     * Runs [write] on a real dispatcher: the repository waits for the resolution on a real
     * timeout, which the virtual clock would otherwise expire before Room delivers the write.
     */
    private suspend fun <T> writeAwaitingResolution(write: suspend () -> T): T = withContext(Dispatchers.Default) { write() }

    private fun TestScope.createRepository(
        favoriteDao: FavoriteDao,
        mockLaunchableRepository: ILaunchableRepository
    ): IFavoritesRepositoryImpl = createFavoritesRepository(
        favoriteDao = favoriteDao,
        launchableRepository = mockLaunchableRepository
    )
}

private fun createLaunchable(name: String): Launchable = RailLaunchable(
    defaultName = name,
    defaultIcon = Icon.RailIcon(resId = DEFAULT_RES_ID),
    deeplink = "rail://$name"
)
