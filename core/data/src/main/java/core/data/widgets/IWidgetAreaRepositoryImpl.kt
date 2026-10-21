package core.data.widgets

import core.data.room.dao.WidgetAreaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart

class IWidgetAreaRepositoryImpl(
    private val widgetAreaDao: WidgetAreaDao,
    private val appWidgetHost: IAppWidgetHostSource,
    private val lifecycleReconciler: WidgetLifecycleReconciler
) : IWidgetAreaRepository {
    override val places: Flow<List<WidgetPlace>> = combine(
        widgetAreaDao.observePlaces(),
        widgetAreaDao.observeWidgets(),
        appWidgetHost.providerChanges.onStart { emit(Unit) },
        lifecycleReconciler.isStartupReconciled
    ) { places, widgets, _, startupReconciled ->
        if (!startupReconciled) {
            emptyList()
        } else {
            val resolution: WidgetAreaResolution = resolveWidgetArea(places, widgets) { widget ->
                widget.availability(host = appWidgetHost)
            }
            widgetAreaDao.deleteDuplicateHeaders(resolution.duplicateHeaderIds)
            resolution.places
        }
    }.flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override suspend fun removeHeader(): Int? = widgetAreaDao.removeHeader()

    override suspend fun restoreHeader(position: Int) {
        widgetAreaDao.restoreHeader(position)
    }

    override suspend fun addAppWidget(request: AddAppWidget): Long = widgetAreaDao.addAppWidget(
        appWidgetId = request.appWidgetId.value,
        provider = request.provider,
        profileSerial = request.profileSerial,
        belowPlaceId = (request.insertAt as? InsertPosition.Below)?.placeId,
        defaultHeightDp = request.defaultHeightDp
    )

    override suspend fun removeWidget(widgetId: Long): AppWidgetId? = widgetAreaDao.removeWidget(widgetId)?.let(::AppWidgetId)

    override suspend fun setPlaceHeight(placeId: Long, heightDp: Int?) {
        widgetAreaDao.setPlaceHeight(placeId = placeId, heightDp = heightDp)
    }
}

internal data class WidgetAreaResolution(val places: List<WidgetPlace>, val duplicateHeaderIds: List<Long>)

/** Resolves DAO rows into the Widget Area's display model, keeping only the first Header. */
internal fun resolveWidgetArea(
    places: List<WidgetPlaceRecord>,
    widgets: List<WidgetRecord>,
    availability: (WidgetRecord) -> WidgetAvailability = { WidgetAvailability.Available }
): WidgetAreaResolution {
    val widgetsByPlace: Map<Long, List<WidgetRecord>> = widgets.groupBy { widget -> widget.placeId }
    var hasHeader: Boolean = false
    val duplicateHeaderIds: MutableList<Long> = mutableListOf()
    val resolvedPlaces: List<WidgetPlace> = places
        .sortedWith(compareBy({ place -> place.position }, { place -> place.id }))
        .mapNotNull { place ->
            val placedWidgets: List<PlacedWidget> = widgetsByPlace[place.id].orEmpty()
                .sortedWith(compareBy({ widget -> widget.pagePosition }, { widget -> widget.id }))
                .mapNotNull { widget ->
                    if (widget.kind == WidgetKind.HEADER) {
                        if (hasHeader) {
                            duplicateHeaderIds.add(widget.id)
                            return@mapNotNull null
                        }
                        hasHeader = true
                    }
                    widget.toPlacedWidget(availability = availability)
                }
            if (placedWidgets.isEmpty()) {
                null
            } else {
                WidgetPlace(
                    id = place.id,
                    heightDp = place.heightDp,
                    currentPage = place.currentPage,
                    widgets = placedWidgets
                )
            }
        }
    return WidgetAreaResolution(places = resolvedPlaces, duplicateHeaderIds = duplicateHeaderIds)
}

private fun WidgetRecord.toPlacedWidget(availability: (WidgetRecord) -> WidgetAvailability): PlacedWidget? = when (kind) {
    WidgetKind.HEADER -> PlacedWidget.Header(id = id)

    WidgetKind.APP_WIDGET -> {
        val storedAppWidgetId: Int? = appWidgetId
        val storedProvider: WidgetProvider? = provider?.let(WidgetProvider::fromKey)
        val storedProfileSerial: Long? = profileSerial
        if (storedAppWidgetId == null || storedProvider == null || storedProfileSerial == null) {
            null
        } else {
            PlacedWidget.AppWidget(
                id = id,
                appWidgetId = storedAppWidgetId,
                provider = storedProvider,
                profileSerial = storedProfileSerial,
                extendsUnderRail = extendsUnderRail,
                availability = availability(this),
                defaultHeightDp = defaultHeightDp
            )
        }
    }

    WidgetKind.UNKNOWN -> null
}
