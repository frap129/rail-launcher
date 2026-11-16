package core.data.launches

import core.data.cleanup.launchCountCleanupRow
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.StaleKeyCleanupOutcome
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.createClockReading
import core.data.testing.createClockSourceMock
import core.data.testing.staleKeyCleanupOutcome
import core.util.MILLIS_PER_WEEK
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SCORE_DELTA: Double = 1e-9
private const val FIRST_KEY: String = "com.example.first/.MainActivity_UserHandle{0}"
private const val SECOND_KEY: String = "com.example.second/.MainActivity_UserHandle{0}"

@OptIn(ExperimentalCoroutinesApi::class)
class ILaunchCountRepositoryImplTest : InMemoryDatabaseTest() {
    @Test
    fun `counts a launch and stores its Recent use score`() = databaseTest {
        val repository: ILaunchCountRepository = createRepository()

        repository.recordLaunch(key = FIRST_KEY)
        repository.recordLaunch(key = FIRST_KEY)

        assertEquals(
            listOf(LaunchCountRecord(key = FIRST_KEY, launchCount = 2, recentUseScore = 2.0, scoreUpdatedAt = TEST_NOW_EPOCH_MS)),
            repository.counts.first()
        )
    }

    @Test
    fun `decays the stored score to the new launch before adding it`() = databaseTest {
        var inputNow: Instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS)
        val repository: ILaunchCountRepository = createRepository(now = { inputNow })
        val inputNextLaunchAt: Long = TEST_NOW_EPOCH_MS + MILLIS_PER_WEEK
        repository.recordLaunch(key = FIRST_KEY)

        inputNow = Instant.ofEpochMilli(inputNextLaunchAt)
        repository.recordLaunch(key = FIRST_KEY)

        val actualRecord: LaunchCountRecord = repository.counts.first().single()
        assertEquals(2, actualRecord.launchCount)
        assertEquals(1.5, actualRecord.recentUseScore, SCORE_DELTA)
        assertEquals(inputNextLaunchAt, actualRecord.scoreUpdatedAt)
    }

    @Test
    fun `counts each key apart`() = databaseTest {
        val repository: ILaunchCountRepository = createRepository()

        repository.recordLaunch(key = FIRST_KEY)
        repository.recordLaunch(key = SECOND_KEY)
        repository.recordLaunch(key = SECOND_KEY)

        val actualCounts: Map<String, Int> = repository.counts.first().associate { record -> record.key to record.launchCount }
        assertEquals(mapOf(FIRST_KEY to 1, SECOND_KEY to 2), actualCounts)
    }

    @Test
    fun `stale-key cleanup removes counts for gone Launchables and keeps Kept keys`() = databaseTest {
        val launchCountDao = database.getLaunchCountDao()

        val actualOutcome: StaleKeyCleanupOutcome = staleKeyCleanupOutcome(
            record = { key -> launchCountDao.record(key = key, launchedAt = TEST_NOW_EPOCH_MS) },
            storedKeys = { launchCountDao.observeAll().first().mapTo(mutableSetOf()) { record -> record.key } },
            cleanupRow = launchCountCleanupRow(launchCountDao = launchCountDao)
        )

        assertEquals(setOf(actualOutcome.presentKey, actualOutcome.keptKey), actualOutcome.survivingKeys)
    }

    private fun createRepository(now: () -> Instant = { Instant.ofEpochMilli(TEST_NOW_EPOCH_MS) }): ILaunchCountRepository =
        ILaunchCountRepositoryImpl(
            launchCountDao = database.getLaunchCountDao(),
            clockSource = createClockSourceMock(
                readings = MutableStateFlow(createClockReading(instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS))),
                now = now
            ),
            dispatcher = databaseDispatcher
        )
}
