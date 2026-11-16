package core.data.search

import core.data.cleanup.searchHistoryCleanupRow
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.StaleKeyCleanupOutcome
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.createClockReading
import core.data.testing.createClockSourceMock
import core.data.testing.staleKeyCleanupOutcome
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIRST_KEY: String = "com.example.first/.MainActivity_UserHandle{0}"
private const val SECOND_KEY: String = "com.example.second/.MainActivity_UserHandle{0}"

@OptIn(ExperimentalCoroutinesApi::class)
class ISearchHistoryRepositoryImplTest : InMemoryDatabaseTest() {
    @Test
    fun `counts a launch and keeps its last launch time`() = databaseTest {
        val repository: ISearchHistoryRepository = createRepository()

        repository.recordLaunch(key = FIRST_KEY)
        repository.recordLaunch(key = FIRST_KEY)

        assertEquals(
            listOf(SearchHistoryRecord(key = FIRST_KEY, launchCount = 2, lastLaunchedAt = TEST_NOW_EPOCH_MS, isRemoved = false)),
            repository.history.first()
        )
    }

    @Test
    fun `removes a Launchable from suggestions that has no launch recorded`() = databaseTest {
        val repository: ISearchHistoryRepository = createRepository()

        repository.removeFromSuggestions(key = FIRST_KEY)

        assertEquals(
            listOf(SearchHistoryRecord(key = FIRST_KEY, launchCount = 0, lastLaunchedAt = 0, isRemoved = true)),
            repository.history.first()
        )
    }

    @Test
    fun `recording a Launchable removed from suggestions keeps it removed`() = databaseTest {
        val repository: ISearchHistoryRepository = createRepository()
        repository.removeFromSuggestions(key = FIRST_KEY)

        repository.recordLaunch(key = FIRST_KEY)

        val actualRecord: SearchHistoryRecord = repository.history.first().single()
        assertEquals(1, actualRecord.launchCount)
        assertEquals(TEST_NOW_EPOCH_MS, actualRecord.lastLaunchedAt)
        assertTrue(actualRecord.isRemoved)
    }

    @Test
    fun `stale-key cleanup removes history for gone Launchables and keeps Kept keys`() = databaseTest {
        val searchHistoryDao = database.getSearchHistoryDao()

        val actualOutcome: StaleKeyCleanupOutcome = staleKeyCleanupOutcome(
            record = { key -> searchHistoryDao.record(key = key, launchedAt = TEST_NOW_EPOCH_MS) },
            storedKeys = { searchHistoryDao.observeAll().first().mapTo(mutableSetOf()) { record -> record.key } },
            cleanupRow = searchHistoryCleanupRow(searchHistoryDao = searchHistoryDao)
        )

        assertEquals(setOf(actualOutcome.presentKey, actualOutcome.keptKey), actualOutcome.survivingKeys)
    }

    @Test
    fun `removing from suggestions keeps the launch count`() = databaseTest {
        val repository: ISearchHistoryRepository = createRepository()
        repository.recordLaunch(key = FIRST_KEY)
        repository.recordLaunch(key = SECOND_KEY)

        repository.removeFromSuggestions(key = FIRST_KEY)

        val actualRecords: List<SearchHistoryRecord> = repository.history.first()
        assertEquals(1, actualRecords.single { record -> record.key == FIRST_KEY }.launchCount)
        assertTrue(actualRecords.single { record -> record.key == FIRST_KEY }.isRemoved)
        assertEquals(1, actualRecords.single { record -> record.key == SECOND_KEY }.launchCount)
    }

    private fun createRepository(): ISearchHistoryRepository = ISearchHistoryRepositoryImpl(
        searchHistoryDao = database.getSearchHistoryDao(),
        clockSource = createClockSourceMock(reading = createClockReading(instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS))),
        dispatcher = databaseDispatcher
    )
}
