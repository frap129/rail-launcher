package core.data.widgets

/** Removes every Widget owned by a Pop-up and frees their system App Widget ids. */
interface IRemoveOwnerWidgets {
    suspend operator fun invoke(ownerKey: String): List<AppWidgetId>

    /** Frees [appWidgetIds], whose database rows are already deleted. */
    fun release(appWidgetIds: List<AppWidgetId>)
}

/** Removes every Widget owned by a Pop-up and frees their system App Widget ids. */
class IRemoveOwnerWidgetsImpl(private val repository: IWidgetAreaRepository, private val host: IAppWidgetHostSource) : IRemoveOwnerWidgets {
    override suspend operator fun invoke(ownerKey: String): List<AppWidgetId> =
        repository.removeOwnerPlaces(ownerKey = ownerKey).also { appWidgetIds -> release(appWidgetIds = appWidgetIds) }

    override fun release(appWidgetIds: List<AppWidgetId>) {
        appWidgetIds.forEach { appWidgetId -> host.deleteId(id = appWidgetId) }
    }
}
