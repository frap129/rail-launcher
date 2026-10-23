package core.data.widgets

/** Removes a placed Widget and frees its system App Widget id. */
class IRemoveAppWidgetImpl(private val repository: IWidgetAreaRepository, private val host: IAppWidgetHostSource) : IRemoveAppWidget {
    override suspend operator fun invoke(widgetId: Long): AppWidgetId? {
        val appWidgetId: AppWidgetId = repository.removeWidget(widgetId = widgetId) ?: return null
        host.deleteId(id = appWidgetId)
        return appWidgetId
    }
}
