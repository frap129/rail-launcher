package core.data.widgets

import core.data.room.dao.WidgetAreaDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class IWidgetAreaRepositoryImpl(private val widgetAreaDao: WidgetAreaDao) : IWidgetAreaRepository {
    override val places: Flow<List<WidgetPlace>> = combine(
        widgetAreaDao.observePlaces(),
        widgetAreaDao.observeWidgets()
    ) { places, widgets ->
        val resolution: WidgetAreaResolution = resolveWidgetArea(places, widgets)
        widgetAreaDao.deleteDuplicateHeaders(resolution.duplicateHeaderIds)
        resolution.places
    }.distinctUntilChanged()

    override suspend fun removeHeader(): Int? = widgetAreaDao.removeHeader()

    override suspend fun restoreHeader(position: Int) {
        widgetAreaDao.restoreHeader(position)
    }
}

internal data class WidgetAreaResolution(val places: List<WidgetPlace>, val duplicateHeaderIds: List<Long>)

/** Resolves DAO rows into the Widget Area's display model, keeping only the first Header. */
internal fun resolveWidgetArea(places: List<WidgetPlaceRecord>, widgets: List<WidgetRecord>): WidgetAreaResolution {
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
                    widget.toPlacedWidget()
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

private fun WidgetRecord.toPlacedWidget(): PlacedWidget? = when (kind) {
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
                extendsUnderRail = extendsUnderRail
            )
        }
    }

    WidgetKind.UNKNOWN -> null
}
