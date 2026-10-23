package core.data.widgets

import core.data.room.dao.PendingAppWidgetDao

/** The system ids allocated by an App Widget add flow that has not completed yet. */
interface IAddFlowPendingIds {
    /** Every id reserved by an add flow that has not finished. */
    suspend fun get(): Set<AppWidgetId>

    /** Replaces the reserved ids. */
    suspend fun set(ids: Set<AppWidgetId>)
}

/**
 * Stores the pending ids in Room so startup reconciliation does not free an id
 * whose configuration activity is returning after process recreation.
 */
class IAddFlowPendingIdsImpl(private val dao: PendingAppWidgetDao) : IAddFlowPendingIds {
    override suspend fun get(): Set<AppWidgetId> = dao.getIds()
        .mapTo(mutableSetOf(), ::AppWidgetId)

    override suspend fun set(ids: Set<AppWidgetId>) {
        dao.replace(ids.map { id -> id.value })
    }
}
