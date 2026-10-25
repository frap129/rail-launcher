package core.data.widgets

/** Removes every Widget owned by a Pop-up and frees their system App Widget ids. */
interface IRemoveOwnerWidgets {
    suspend operator fun invoke(ownerKey: String): List<AppWidgetId>
}

/** Removes every Widget owned by a Pop-up and frees their system App Widget ids. */
class IRemoveOwnerWidgetsImpl(private val repository: IWidgetAreaRepository, private val host: IAppWidgetHostSource) : IRemoveOwnerWidgets {
    override suspend operator fun invoke(ownerKey: String): List<AppWidgetId> {
        val appWidgetIds: List<AppWidgetId> = repository.removeOwnerPlaces(ownerKey = ownerKey)
        appWidgetIds.forEach { appWidgetId -> host.deleteId(id = appWidgetId) }
        return appWidgetIds
    }
}
