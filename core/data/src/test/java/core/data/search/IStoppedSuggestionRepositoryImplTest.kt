package core.data.search

import core.data.apps.model.App
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.createApp
import core.data.testing.createLaunchCountRecord
import core.data.testing.createLoadedLaunchableState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test

private const val FIRST_KEY: String = "com.example.first/.MainActivity_UserHandle{0}"
private const val SECOND_KEY: String = "com.example.second/.MainActivity_UserHandle{0}"

@OptIn(ExperimentalCoroutinesApi::class)
class IStoppedSuggestionRepositoryImplTest : InMemoryDatabaseTest() {
    @Test
    fun `stores no key before any suggestion is stopped`() = databaseTest {
        val repository: IStoppedSuggestionRepository = createRepository()

        assertEquals(emptySet<String>(), repository.keys.first())
    }

    @Test
    fun `stores each stopped key once`() = databaseTest {
        val repository: IStoppedSuggestionRepository = createRepository()

        repository.stop(key = FIRST_KEY)
        repository.stop(key = FIRST_KEY)
        repository.stop(key = SECOND_KEY)

        assertEquals(setOf(FIRST_KEY, SECOND_KEY), repository.keys.first())
    }

    @Test
    fun `reset clears every stopped key`() = databaseTest {
        val repository: IStoppedSuggestionRepository = createRepository()
        repository.stop(key = FIRST_KEY)
        repository.stop(key = SECOND_KEY)

        repository.reset()

        assertEquals(emptySet<String>(), repository.keys.first())
    }

    @Test
    fun `suggests a stopped Launchable again after reset`() = databaseTest {
        val repository: IStoppedSuggestionRepository = createRepository()
        val inputApp: App = createApp(name = "Maps")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        repository.stop(key = inputApp.key)

        repository.reset()
        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = listOf(
                createLaunchCountRecord(key = inputApp.key, launchCount = HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT, recentUseScore = 1.0)
            ),
            folderMembers = emptyMap(),
            stoppedKeys = repository.keys.first(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputApp), actualSuggestions)
    }

    private fun createRepository(): IStoppedSuggestionRepository = IStoppedSuggestionRepositoryImpl(
        stoppedSuggestionDao = database.getStoppedSuggestionDao(),
        dispatcher = databaseDispatcher
    )
}
