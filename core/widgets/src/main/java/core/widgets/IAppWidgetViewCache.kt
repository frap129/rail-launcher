package core.widgets

import core.data.widgets.AppWidgetId

/** Process-lifetime cache of the host views Rail shows for each App Widget id. */
interface IAppWidgetViewCache {
    /** Returns the cached view, which may still be attached to its current holder. */
    fun getOrCreate(id: AppWidgetId): RailAppWidgetHostView?

    /** Returns the cached view detached from its previous holder, ready to attach to a new one. */
    fun takeForAttach(id: AppWidgetId): RailAppWidgetHostView?

    fun remove(id: AppWidgetId)

    /** Reports [widthDp] × [heightDp] unless the same size was already sent for this widget. */
    fun reportSizeIfChanged(id: AppWidgetId, widthDp: Int, heightDp: Int)

    /** Reports the persisted Widget Area size to the providers of the resized place. */
    fun updateSize(ids: List<AppWidgetId>, widthDp: Int, heightDp: Int)
}
