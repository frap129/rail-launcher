package core.data.search

import core.data.apps.model.App
import core.data.cleanup.SearchHistoryCleanupRow
import core.data.launchables.LaunchableState
import core.data.profiles.ProfileKind
import core.data.profiles.toProfileToken
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createClockReading
import core.data.testing.createClockSourceMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createProfileInfo
import core.data.testing.createShortcutSourceMock
import core.data.testing.createStaleKeyCleanup
import core.data.testing.createUserHandle
import core.data.testing.finishPendingWork
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW_EPOCH_MS: Long = 1_791_720_000_000L
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
            listOf(SearchHistoryRecord(key = FIRST_KEY, launchCount = 2, lastLaunchedAt = NOW_EPOCH_MS, isRemoved = false)),
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
        assertEquals(NOW_EPOCH_MS, actualRecord.lastLaunchedAt)
        assertTrue(actualRecord.isRemoved)
    }

    @Test
    fun `stale-key cleanup removes history for gone Launchables and keeps Kept keys`() = databaseTest {
        val inputPresent: App = createApp(name = "Present", packageName = "com.example.present")
        val inputGone: App = createApp(name = "Gone", packageName = "com.example.gone")
        val inputKept: App = createApp(
            name = "Kept",
            packageName = "com.example.kept",
            profileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        )
        val searchHistoryDao = database.getSearchHistoryDao()
        listOf(inputPresent, inputGone, inputKept).forEach { app -> searchHistoryDao.record(key = app.key, launchedAt = NOW_EPOCH_MS) }
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(
                launchables = listOf(inputPresent),
                keptKeys = setOf(createUserHandle(userId = WORK_USER_ID).toProfileToken())
            )
        )
        createStaleKeyCleanup(
            launchableRepository = createLaunchableRepositoryMock(state = inputState),
            shortcutSource = createShortcutSourceMock(),
            rows = listOf(SearchHistoryCleanupRow(searchHistoryDao = searchHistoryDao)),
            scope = backgroundScope
        )

        finishPendingWork()

        val actualKeys: Set<String> = searchHistoryDao.observeAll().first().mapTo(mutableSetOf()) { record -> record.key }
        assertEquals(setOf(inputPresent.key, inputKept.key), actualKeys)
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
        clockSource = createClockSourceMock(reading = createClockReading(instant = Instant.ofEpochMilli(NOW_EPOCH_MS))),
        dispatcher = databaseDispatcher
    )
}
