package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IAddFlowPendingIds

class FakeAddFlowPendingIds(initialIds: Set<AppWidgetId> = emptySet()) : IAddFlowPendingIds {
    var ids: Set<AppWidgetId> = initialIds
        private set

    val updates: MutableList<Set<AppWidgetId>> = mutableListOf()

    override suspend fun get(): Set<AppWidgetId> = ids

    override suspend fun set(ids: Set<AppWidgetId>) {
        this.ids = ids
        updates.add(ids)
    }
}
