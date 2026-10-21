package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IAddFlowPendingIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAddFlowPendingIds(initialIds: Set<AppWidgetId> = emptySet()) : IAddFlowPendingIds {
    private val pending: MutableStateFlow<Set<AppWidgetId>> = MutableStateFlow(initialIds)

    override val ids: StateFlow<Set<AppWidgetId>> = pending

    val updates: MutableList<Set<AppWidgetId>> = mutableListOf()

    override fun set(ids: Set<AppWidgetId>) {
        pending.value = ids
        updates.add(ids)
    }
}
