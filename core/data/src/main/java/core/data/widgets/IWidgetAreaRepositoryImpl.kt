package core.data.widgets

import core.data.room.dao.WidgetAreaDao
import core.data.widgets.WidgetRecord
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

    override suspend fun removeHeader(): HeaderRemoval? = widgetAreaDao.removeHeader()

    override suspend fun removeHeaderPage(widgetId: Long): HeaderRemoval? = widgetAreaDao.removeHeader(widgetId = widgetId)

    override suspend fun replaceHeader(widgetId: Long): ReplaceHeaderUndo? {
        val undo: ReplaceHeaderUndo = widgetAreaDao.replaceHeader(widgetId = widgetId) ?: return null
        clampStackPlace(placeId = undo.headerPlaceId)
        clampDissolvedPlace(dao = widgetAreaDao, host = appWidgetHost, placeId = undo.widgetPlaceId)
        return undo
    }

    override suspend fun undoReplaceHeader(undo: ReplaceHeaderUndo) {
        widgetAreaDao.undoReplaceHeader(undo = undo)
    }

    override suspend fun setExtendsUnderRail(widgetId: Long, extendsUnderRail: Boolean) {
        val widget: WidgetRecord = widgetAreaDao.getWidget(widgetId) ?: return
        if (widgetAreaDao.getWidgetsForPlace(widget.placeId).size > 1) {
            return
        }
        widgetAreaDao.setExtendsUnderRail(widgetId = widgetId, extendsUnderRail = extendsUnderRail)
    }

    /** Clamps a stack to the intersection of the resize bounds of every page. */
    private suspend fun clampStackPlace(placeId: Long) {
        val pages: List<WidgetRecord> = widgetAreaDao.getWidgetsForPlace(placeId)
        if (pages.size < 2) {
            return
        }
        val heightDp: Int = widgetAreaDao.getPlace(placeId)?.heightDp ?: return
        val bounds: ResizeBounds = ResizeBounds.stackBounds(pages.map { page -> page.resizeBounds() })
        widgetAreaDao.setPlaceHeight(placeId = placeId, heightDp = bounds.clamp(heightDp = heightDp, homeHeightDp = Int.MAX_VALUE))
    }

    private fun WidgetRecord.resizeBounds(): ResizeBounds {
        val info: WidgetProviderInfo? = appWidgetId?.let { id -> appWidgetHost.providerInfo(id = AppWidgetId(id)) }
        return appWidgetResizeBounds(providerInfo = info, defaultHeightDp = defaultHeightDp)
    }

    override suspend fun restoreHeader(position: Int, placeId: Long?, pagePosition: Int) {
        widgetAreaDao.restoreHeader(position = position, placeId = placeId, pagePosition = pagePosition)
    }

    override suspend fun addAppWidget(request: AddAppWidget): Long = widgetAreaDao.addAppWidget(
        appWidgetId = request.appWidgetId.value,
        provider = request.provider,
        profileSerial = request.profileSerial,
        belowPlaceId = (request.insertAt as? InsertPosition.Below)?.placeId,
        stackPlaceId = (request.insertAt as? InsertPosition.IntoStack)?.placeId,
        defaultHeightDp = request.defaultHeightDp
    )

    override suspend fun removeWidget(widgetId: Long): AppWidgetId? {
        val widget: WidgetRecord = widgetAreaDao.getWidget(widgetId) ?: return null
        val freedId: Int = widgetAreaDao.removeWidget(widgetId) ?: return null
        clampDissolvedPlace(dao = widgetAreaDao, host = appWidgetHost, placeId = widget.placeId)
        return AppWidgetId(freedId)
    }

    override suspend fun setPlaceHeight(placeId: Long, heightDp: Int?) {
        widgetAreaDao.setPlaceHeight(placeId = placeId, heightDp = heightDp)
    }

    override suspend fun setCurrentPage(placeId: Long, page: Int) {
        widgetAreaDao.setCurrentPage(placeId = placeId, page = page)
    }

    override suspend fun commitEdit(edit: WidgetAreaEdit): List<AppWidgetId> {
        val freedIds: List<AppWidgetId> = widgetAreaDao.commitEdit(edit = edit).map(::AppWidgetId)
        widgetAreaDao.getPlaceIds().forEach { placeId ->
            clampDissolvedPlace(dao = widgetAreaDao, host = appWidgetHost, placeId = placeId)
        }
        return freedIds
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
                    currentPage = place.currentPage.coerceIn(0, placedWidgets.lastIndex),
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
