package feature.launcher.widgets

import core.ui.widgets.MediaPlayerUiState
import core.ui.widgets.ResizeState
import core.ui.widgets.WidgetAreaUi
import core.ui.widgets.WidgetEditDraft
import feature.launcher.createTestWidgetAreaUi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A Widget Area ViewModel with the flows Home collects and no behavior beyond them. */
fun createWidgetAreaViewModelMock(inputWidgetAreaUi: WidgetAreaUi = createTestWidgetAreaUi()): WidgetAreaViewModel {
    val mockWidgetAreaViewModel: WidgetAreaViewModel = mockk(relaxed = true)
    every { mockWidgetAreaViewModel.widgetAreaState } returns MutableStateFlow(inputWidgetAreaUi.state)
    every { mockWidgetAreaViewModel.resizeState } returns MutableStateFlow<ResizeState?>(null)
    every { mockWidgetAreaViewModel.editState } returns MutableStateFlow<WidgetEditDraft?>(null)
    every { mockWidgetAreaViewModel.mediaPlayerState } returns MutableStateFlow<MediaPlayerUiState>(inputWidgetAreaUi.mediaPlayer)
    every { mockWidgetAreaViewModel.scrollToPlaceId } returns MutableStateFlow<Long?>(null)
    return mockWidgetAreaViewModel
}
