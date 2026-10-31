package core.data.widgets

import core.data.room.dao.WidgetAreaDao
import core.data.widgets.WidgetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class IWidgetAreaRepositoryImpl(
    private val widgetAreaDao: WidgetAreaDao,
    private val appWidgetHost: IAppWidgetHostSource,
    private val lifecycleReconciler: IWidgetLifecycleReconciler
) : IWidgetAreaRepository {
    override val places: Flow<List<WidgetPlace>> = placesFor(ownerKey = null)

    override val popupOwnerKeys: Flow<List<String>> = widgetAreaDao.observePopupOwnerKeys()
        .map { ownerKeys -> ownerKeys.distinct() }
        .distinctUntilChanged()

    override fun placesFor(ownerKey: String?): Flow<List<WidgetPlace>> = combine(
        widgetAreaDao.observePlaces(),
        widgetAreaDao.observeWidgets(),
        appWidgetHost.providerChanges.onStart { emit(Unit) },
        lifecycleReconciler.isStartupReconciled
    ) { places, widgets, _, startupReconciled ->
        if (!startupReconciled) {
            emptyList()
        } else {
            val ownedPlaces: List<WidgetPlaceRecord> = places.filter { place -> place.ownerKey == ownerKey }
            val ownedPlaceIds: Set<Long> = ownedPlaces.mapTo(mutableSetOf()) { place -> place.id }
            val ownedWidgets: List<WidgetRecord> = widgets.filter { widget -> widget.placeId in ownedPlaceIds }
            val resolution: WidgetAreaResolution = resolveWidgetArea(ownedPlaces, ownedWidgets) { widget ->
                widget.availability(host = appWidgetHost)
            }
            if (ownerKey == null) {
                widgetAreaDao.deleteDuplicateHeaders(resolution.duplicateHeaderIds)
            }
            resolution.places
        }
    }.flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override suspend fun removeHeader(): HeaderRemoval? = widgetAreaDao.removeHeader()

    override suspend fun removeHeaderPage(widgetId: Long): HeaderRemoval? = widgetAreaDao.removeHeader(widgetId = widgetId)

    override suspend fun replaceHeader(widgetId: Long): ReplaceHeaderUndo? {
        val undo: ReplaceHeaderUndo? = widgetAreaDao.inTransaction { replaceHeaderRows(widgetId = widgetId) }
        if (undo == null) {
            return null
        }
        clampStackPlace(placeId = undo.headerPlaceId)
        clampDissolvedPlace(dao = widgetAreaDao, host = appWidgetHost, placeId = undo.widgetPlaceId)
        return undo
    }

    private suspend fun replaceHeaderRows(widgetId: Long): ReplaceHeaderUndo? {
        val widget: WidgetRecord = widgetAreaDao.getWidget(widgetId)?.takeIf { widget -> widget.kind == WidgetKind.APP_WIDGET }
            ?: return null
        val header: WidgetRecord = widgetAreaDao.getHeaders().firstOrNull() ?: return null
        if (header.placeId == widget.placeId) {
            return null
        }
        val headerPlace: WidgetPlaceRecord = widgetAreaDao.getPlace(header.placeId) ?: return null
        val widgetPlace: WidgetPlaceRecord = widgetAreaDao.getPlace(widget.placeId) ?: return null
        val targetIsStack: Boolean = widgetAreaDao.getWidgetsForPlace(header.placeId).any { page -> page.id != header.id }
        widgetAreaDao.deleteWidgets(listOf(header.id))
        widgetAreaDao.moveWidget(widgetId = widget.id, placeId = header.placeId, position = header.pagePosition)
        widgetAreaDao.setCurrentPage(placeId = header.placeId, page = header.pagePosition)
        if (!targetIsStack) {
            widgetAreaDao.setPlaceHeight(placeId = header.placeId, heightDp = widgetPlace.heightDp)
        }
        widgetAreaDao.normalizePlace(placeId = widget.placeId)
        return ReplaceHeaderUndo(
            widgetId = widget.id,
            headerPlaceId = header.placeId,
            headerPosition = headerPlace.position,
            headerPagePosition = header.pagePosition,
            headerHeightDp = headerPlace.heightDp,
            headerCurrentPage = headerPlace.currentPage,
            widgetPlaceId = widget.placeId,
            widgetPosition = widgetPlace.position,
            widgetPagePosition = widget.pagePosition,
            widgetHeightDp = widgetPlace.heightDp,
            widgetCurrentPage = widgetPlace.currentPage
        )
    }

    override suspend fun undoReplaceHeader(undo: ReplaceHeaderUndo) {
        widgetAreaDao.inTransaction { undoReplaceHeaderRows(undo = undo) }
    }

    private suspend fun undoReplaceHeaderRows(undo: ReplaceHeaderUndo) {
        widgetAreaDao.getWidget(undo.widgetId)?.let { widget ->
            val widgetPlaceId: Long = ensurePlaceForUndo(
                placeId = undo.widgetPlaceId,
                position = undo.widgetPosition,
                heightDp = undo.widgetHeightDp,
                currentPage = undo.widgetCurrentPage
            )
            widgetAreaDao.moveWidget(widgetId = widget.id, placeId = widgetPlaceId, position = undo.widgetPagePosition)
            widgetAreaDao.setPlaceHeight(placeId = widgetPlaceId, heightDp = undo.widgetHeightDp)
            widgetAreaDao.setCurrentPage(placeId = widgetPlaceId, page = undo.widgetCurrentPage)
        }
        if (widgetAreaDao.getHeaders().isNotEmpty()) {
            return
        }
        val headerPlaceId: Long = ensurePlaceForUndo(
            placeId = undo.headerPlaceId,
            position = undo.headerPosition,
            heightDp = undo.headerHeightDp,
            currentPage = undo.headerCurrentPage
        )
        widgetAreaDao.insertWidget(
            WidgetRecord(
                placeId = headerPlaceId,
                pagePosition = undo.headerPagePosition,
                kind = WidgetKind.HEADER
            )
        )
        widgetAreaDao.setPlaceHeight(placeId = headerPlaceId, heightDp = undo.headerHeightDp)
        widgetAreaDao.setCurrentPage(placeId = headerPlaceId, page = undo.headerCurrentPage)
    }

    private suspend fun ensurePlaceForUndo(placeId: Long, position: Int, heightDp: Int?, currentPage: Int): Long {
        widgetAreaDao.getPlace(placeId)?.let { return placeId }
        if (widgetAreaDao.getPlaceAtPosition(position) != null) {
            widgetAreaDao.shiftPositionsFrom(position)
        }
        widgetAreaDao.insertPlace(
            WidgetPlaceRecord(
                id = placeId,
                position = position,
                heightDp = heightDp,
                currentPage = currentPage
            )
        )
        return placeId
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
        widgetAreaDao.inTransaction {
            restoreHeaderRows(position = position, placeId = placeId, pagePosition = pagePosition)
        }
    }

    private suspend fun restoreHeaderRows(position: Int, placeId: Long?, pagePosition: Int) {
        if (widgetAreaDao.getHeaders().isNotEmpty()) {
            return
        }
        val targetPlace: WidgetPlaceRecord? = placeId?.let { id -> widgetAreaDao.getPlace(id) }
        if (targetPlace != null) {
            val pages: List<WidgetRecord> = widgetAreaDao.getWidgetsForPlace(targetPlace.id)
            val targetPage: Int = pagePosition.coerceIn(0, pages.size)
            pages.filter { page -> page.pagePosition >= targetPage }.forEach { page ->
                widgetAreaDao.moveWidget(widgetId = page.id, placeId = targetPlace.id, position = page.pagePosition + 1)
            }
            widgetAreaDao.insertWidget(
                WidgetRecord(
                    placeId = targetPlace.id,
                    pagePosition = targetPage,
                    kind = WidgetKind.HEADER
                )
            )
            widgetAreaDao.setCurrentPage(placeId = targetPlace.id, page = targetPage)
            return
        }
        val targetPosition: Int = position.coerceIn(0, widgetAreaDao.getMaxPlacePosition() + 1)
        widgetAreaDao.shiftPositionsFrom(targetPosition)
        val newPlaceId: Long = widgetAreaDao.insertPlace(WidgetPlaceRecord(position = targetPosition))
        widgetAreaDao.insertWidget(
            WidgetRecord(
                placeId = newPlaceId,
                pagePosition = 0,
                kind = WidgetKind.HEADER
            )
        )
    }

    override suspend fun addAppWidget(request: AddAppWidget): AddedAppWidget = widgetAreaDao.inTransaction {
        addAppWidgetRows(request = request)
    }

    private suspend fun addAppWidgetRows(request: AddAppWidget): AddedAppWidget {
        val ownerKey: String? = request.ownerKey
        if (ownerKey != null) {
            return addOwnedAppWidget(ownerKey = ownerKey, request = request)
        }
        val stackPlaceId: Long? = (request.insertAt as? InsertPosition.IntoStack)?.placeId
        if (stackPlaceId != null) {
            val pages: List<WidgetRecord> = widgetAreaDao.getWidgetsForPlace(stackPlaceId)
            val pagePosition: Int = (pages.maxOfOrNull { page -> page.pagePosition } ?: -1) + 1
            val widgetId: Long = insertAppWidget(placeId = stackPlaceId, pagePosition = pagePosition, request = request)
            widgetAreaDao.setCurrentPage(placeId = stackPlaceId, page = pagePosition)
            return AddedAppWidget(widgetId = widgetId, placeId = stackPlaceId)
        }
        val belowPosition: Int? = (request.insertAt as? InsertPosition.Below)?.placeId?.let { placeId ->
            widgetAreaDao.getPlacePosition(placeId)
        }
        val position: Int = belowPosition?.plus(1) ?: (widgetAreaDao.getMaxPlacePosition() + 1)
        widgetAreaDao.shiftPositionsFrom(position)
        val placeId: Long = widgetAreaDao.insertPlace(WidgetPlaceRecord(position = position))
        val widgetId: Long = insertAppWidget(placeId = placeId, pagePosition = 0, request = request)
        return AddedAppWidget(widgetId = widgetId, placeId = placeId)
    }

    private suspend fun addOwnedAppWidget(ownerKey: String, request: AddAppWidget): AddedAppWidget {
        val existingPlace: WidgetPlaceRecord? = widgetAreaDao.getPlaceForOwner(ownerKey)
        val placeId: Long = existingPlace?.id ?: widgetAreaDao.insertPlace(WidgetPlaceRecord(position = 0, ownerKey = ownerKey))
        val pagePosition: Int = widgetAreaDao.getWidgetsForPlace(placeId).size
        val widgetId: Long = insertAppWidget(placeId = placeId, pagePosition = pagePosition, request = request)
        widgetAreaDao.setCurrentPage(placeId = placeId, page = pagePosition)
        return AddedAppWidget(widgetId = widgetId, placeId = placeId)
    }

    private suspend fun insertAppWidget(placeId: Long, pagePosition: Int, request: AddAppWidget): Long = widgetAreaDao.insertWidget(
        WidgetRecord(
            placeId = placeId,
            pagePosition = pagePosition,
            kind = WidgetKind.APP_WIDGET,
            appWidgetId = request.appWidgetId.value,
            provider = request.provider.key,
            profileSerial = request.profileSerial,
            defaultHeightDp = request.defaultHeightDp
        )
    )

    override suspend fun removeWidget(widgetId: Long): AppWidgetId? {
        val widget: WidgetRecord = widgetAreaDao.getWidget(widgetId) ?: return null
        val freedId: Int = widgetAreaDao.removeWidget(widgetId) ?: return null
        clampDissolvedPlace(dao = widgetAreaDao, host = appWidgetHost, placeId = widget.placeId)
        return AppWidgetId(freedId)
    }

    override suspend fun removeOwnerPlaces(ownerKey: String): List<AppWidgetId> = widgetAreaDao.inTransaction {
        val placeIds: List<Long> = widgetAreaDao.placeIdsForOwner(ownerKey)
        if (placeIds.isEmpty()) {
            return@inTransaction emptyList()
        }
        val freedIds: List<AppWidgetId> = widgetAreaDao.appWidgetIdsForPlaces(placeIds).map(::AppWidgetId)
        widgetAreaDao.deletePlacesForOwner(ownerKey)
        freedIds
    }

    override suspend fun setPlaceHeight(placeId: Long, heightDp: Int?) {
        widgetAreaDao.setPlaceHeight(placeId = placeId, heightDp = heightDp)
    }

    override suspend fun setCurrentPage(placeId: Long, page: Int) {
        widgetAreaDao.setCurrentPage(placeId = placeId, page = page)
    }

    override suspend fun commitEdit(edit: WidgetAreaEdit): List<AppWidgetId> {
        val freedIds: List<AppWidgetId> = widgetAreaDao.commitEdit(edit = edit).map(::AppWidgetId)
        widgetAreaDao.getWidgetAreaPlaceIds().forEach { placeId ->
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
                    widgets = placedWidgets,
                    ownerKey = place.ownerKey
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
