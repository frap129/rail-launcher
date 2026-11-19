package core.data.testing

import core.data.search.IStoppedSuggestionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A MockK [IStoppedSuggestionRepository] that reads [keys] and adds each key it is asked to stop to them. */
fun createStoppedSuggestionRepositoryMock(
    keys: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
): IStoppedSuggestionRepository {
    val mockStoppedSuggestionRepository: IStoppedSuggestionRepository = mockk()
    every { mockStoppedSuggestionRepository.keys } returns keys
    coEvery { mockStoppedSuggestionRepository.stop(any()) } coAnswers { keys.update { current -> current + firstArg<String>() } }
    return mockStoppedSuggestionRepository
}
