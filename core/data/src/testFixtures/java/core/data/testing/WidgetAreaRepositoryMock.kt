package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IWidgetAreaRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IWidgetAreaRepository] exposing [popupOwnerKeysFlow] and freeing [removeOwnerPlacesAnswer]'s ids. */
fun createWidgetAreaRepositoryMock(
    popupOwnerKeysFlow: Flow<List<String>> = MutableStateFlow(emptyList()),
    removeOwnerPlacesAnswer: suspend (String) -> List<AppWidgetId> = { emptyList() }
): IWidgetAreaRepository {
    val mockWidgetAreaRepository: IWidgetAreaRepository = mockk()
    every { mockWidgetAreaRepository.popupOwnerKeys } returns popupOwnerKeysFlow
    coEvery { mockWidgetAreaRepository.removeOwnerPlaces(any()) } coAnswers { removeOwnerPlacesAnswer(firstArg()) }
    return mockWidgetAreaRepository
}
