package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IAddFlowPendingIds
import io.mockk.coEvery
import io.mockk.mockk

/** The mutable pending ids and recorded writes behind a [createAddFlowPendingIdsMock]. */
class AddFlowPendingIdsState {
    var ids: Set<AppWidgetId> = emptySet()
        private set
    val updates: MutableList<Set<AppWidgetId>> = mutableListOf()

    suspend fun set(ids: Set<AppWidgetId>) {
        this.ids = ids
        updates.add(ids)
    }
}

/** A MockK [IAddFlowPendingIds] over [state]. */
fun createAddFlowPendingIdsMock(state: AddFlowPendingIdsState = AddFlowPendingIdsState()): IAddFlowPendingIds {
    val mockAddFlowPendingIds: IAddFlowPendingIds = mockk()
    coEvery { mockAddFlowPendingIds.get() } answers { state.ids }
    coEvery { mockAddFlowPendingIds.set(any()) } coAnswers { state.set(ids = firstArg()) }
    return mockAddFlowPendingIds
}
