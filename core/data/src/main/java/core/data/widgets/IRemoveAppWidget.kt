package core.data.widgets

/** Removes a placed Widget and frees its system App Widget id. */
interface IRemoveAppWidget {
    suspend operator fun invoke(widgetId: Long): AppWidgetId?
}
