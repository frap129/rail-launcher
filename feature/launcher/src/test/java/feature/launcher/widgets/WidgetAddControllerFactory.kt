package feature.launcher.widgets

import androidx.lifecycle.SavedStateHandle
import core.data.testing.createAddFlowPendingIdsMock
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.headerPlace
import core.data.widgets.IAddFlowPendingIds
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.ui.layout.HomeEditState

/** The production App Widget add controller over the given test doubles. */
fun createAddController(
    mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(places = listOf(headerPlace)),
    mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock(),
    homeEditState: HomeEditState = HomeEditState(),
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
    mockPendingIds: IAddFlowPendingIds = createAddFlowPendingIdsMock()
): IWidgetAddController = IWidgetAddControllerImpl(
    repository = mockRepository,
    appWidgetHost = mockAppWidgetHost,
    homeEditState = homeEditState,
    savedStateHandle = savedStateHandle,
    pendingIds = mockPendingIds
)
