package core.data.search

import core.data.testing.InMemoryDatabaseTest
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

    private fun createRepository(): IStoppedSuggestionRepository = IStoppedSuggestionRepositoryImpl(
        stoppedSuggestionDao = database.getStoppedSuggestionDao(),
        dispatcher = databaseDispatcher
    )
}
