package core.data.widgets

import core.data.room.dao.WidgetAreaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class IWidgetAreaRepositoryImpl(
    private val widgetAreaDao: WidgetAreaDao,
    private val appWidgetHost: IAppWidgetHostSource,
    private val lifecycleReconciler: IWidgetLifecycleReconciler,
    private val settingsRepository: IWidgetSettingsRepository
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
            val resolution: WidgetAreaResolution = projectPlaces(ownedPlaces, ownedWidgets) { widget ->
                widget.availability(host = appWidgetHost)
            }
            if (ownerKey == null) {
                deleteDuplicateHeaders(ids = resolution.duplicateHeaderIds)
            }
            resolution.places
        }
    }.flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override suspend fun removeHeader(): HeaderRemoval? = widgetAreaDao.inTransaction {
        removeHeaderRows(widgetId = null)
    }

    override suspend fun removeHeaderPage(widgetId: Long): HeaderRemoval? = widgetAreaDao.inTransaction {
        removeHeaderRows(widgetId = widgetId)
    }

    private suspend fun removeHeaderRows(widgetId: Long?): HeaderRemoval? {
        val header: WidgetRecord = findHeader(widgetId = widgetId) ?: return null
        val place: WidgetPlaceRecord = widgetAreaDao.getPlace(placeId = header.placeId) ?: return null
        widgetAreaDao.deleteWidgets(listOf(header.id))
        applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = place.id)
        return HeaderRemoval(position = place.position, placeId = place.id, pagePosition = header.pagePosition)
    }

    private suspend fun findHeader(widgetId: Long?): WidgetRecord? = if (widgetId == null) {
        widgetAreaDao.getHeaders().firstOrNull()
    } else {
        widgetAreaDao.getWidget(widgetId)?.takeIf { widget -> widget.kind == WidgetKind.HEADER }
    }

    override suspend fun replaceHeader(widgetId: Long): ReplaceHeaderUndo? = widgetAreaDao.inTransaction {
        replaceHeaderRows(widgetId = widgetId)?.also { undo ->
            applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = undo.headerPlaceId)
            applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = undo.widgetPlaceId)
        }
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
        // Undo restores the exact stored heights, so it must not run the layout clamp.
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

    override suspend fun restoreHeader(position: Int, placeId: Long?, pagePosition: Int) {
        widgetAreaDao.inTransaction {
            restoreHeaderRows(position = position, placeId = placeId, pagePosition = pagePosition)?.let { affectedPlaceId ->
                applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = affectedPlaceId)
            }
        }
    }

    private suspend fun restoreHeaderRows(position: Int, placeId: Long?, pagePosition: Int): Long? {
        if (widgetAreaDao.getHeaders().isNotEmpty()) {
            return null
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
            return targetPlace.id
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
        return newPlaceId
    }

    override suspend fun canGrowStack(placeId: Long, additionalPages: Int): Boolean =
        canGrowStack(pageCount = widgetAreaDao.getWidgetsForPlace(placeId).size, additionalPages = additionalPages)

    override suspend fun canGrowStack(pageCount: Int, additionalPages: Int): Boolean {
        val maxStackPages: Int = settingsRepository.widgetAreaSettings.first().maxStackPages
        return isWithinStackCap(pageCount = pageCount + additionalPages, maxStackPages = maxStackPages)
    }

    override suspend fun addAppWidget(request: AddAppWidget): AddedAppWidget {
        val maxStackPages: Int = settingsRepository.widgetAreaSettings.first().maxStackPages
        return widgetAreaDao.inTransaction {
            addAppWidgetRows(request = request, maxStackPages = maxStackPages)
        }
    }

    private suspend fun addAppWidgetRows(request: AddAppWidget, maxStackPages: Int): AddedAppWidget {
        val ownerKey: String? = request.ownerKey
        if (ownerKey != null) {
            return addOwnedAppWidget(ownerKey = ownerKey, request = request, maxStackPages = maxStackPages)
        }
        val stackPlaceId: Long? = (request.insertAt as? InsertPosition.IntoStack)?.placeId
        if (stackPlaceId != null) {
            val pages: List<WidgetRecord> = widgetAreaDao.getWidgetsForPlace(stackPlaceId)
            if (!canGrowStack(pageCount = pages.size, maxStackPages = maxStackPages)) {
                throw WidgetStackFullException.forMaxPages(maxStackPages = maxStackPages)
            }
            val pagePosition: Int = (pages.maxOfOrNull { page -> page.pagePosition } ?: -1) + 1
            val widgetId: Long = insertAppWidget(placeId = stackPlaceId, pagePosition = pagePosition, request = request)
            widgetAreaDao.setCurrentPage(placeId = stackPlaceId, page = pagePosition)
            applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = stackPlaceId)
            return AddedAppWidget(widgetId = widgetId, placeId = stackPlaceId)
        }
        val belowPosition: Int? = (request.insertAt as? InsertPosition.Below)?.placeId?.let { placeId ->
            widgetAreaDao.getPlacePosition(placeId)
        }
        val position: Int = belowPosition?.plus(1) ?: (widgetAreaDao.getMaxPlacePosition() + 1)
        widgetAreaDao.shiftPositionsFrom(position)
        val placeId: Long = widgetAreaDao.insertPlace(WidgetPlaceRecord(position = position))
        val widgetId: Long = insertAppWidget(placeId = placeId, pagePosition = 0, request = request)
        applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = placeId)
        return AddedAppWidget(widgetId = widgetId, placeId = placeId)
    }

    private suspend fun addOwnedAppWidget(ownerKey: String, request: AddAppWidget, maxStackPages: Int): AddedAppWidget {
        val existingPlace: WidgetPlaceRecord? = widgetAreaDao.getPlaceForOwner(ownerKey)
        val placeId: Long = existingPlace?.id ?: widgetAreaDao.insertPlace(WidgetPlaceRecord(position = 0, ownerKey = ownerKey))
        val pages: List<WidgetRecord> = widgetAreaDao.getWidgetsForPlace(placeId)
        if (!canGrowStack(pageCount = pages.size, maxStackPages = maxStackPages)) {
            throw WidgetStackFullException.forMaxPages(maxStackPages = maxStackPages)
        }
        val pagePosition: Int = (pages.maxOfOrNull { page -> page.pagePosition } ?: -1) + 1
        val widgetId: Long = insertAppWidget(placeId = placeId, pagePosition = pagePosition, request = request)
        widgetAreaDao.setCurrentPage(placeId = placeId, page = pagePosition)
        applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = placeId)
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

    override suspend fun removeWidget(widgetId: Long): AppWidgetId? = widgetAreaDao.inTransaction {
        val widget: WidgetRecord = widgetAreaDao.getWidget(widgetId) ?: return@inTransaction null
        val freedId: Int? = widget.appWidgetId
        widgetAreaDao.deleteWidgets(listOf(widgetId))
        applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = widget.placeId)
        freedId?.let(::AppWidgetId)
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
        widgetAreaDao.inTransaction {
            widgetAreaDao.setPlaceHeight(placeId = placeId, heightDp = heightDp)
            applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = placeId)
        }
    }

    override suspend fun setCurrentPage(placeId: Long, page: Int) {
        widgetAreaDao.setCurrentPage(placeId = placeId, page = page)
    }

    override suspend fun commitEdit(edit: WidgetAreaEdit): List<AppWidgetId> {
        val maxStackPages: Int = settingsRepository.widgetAreaSettings.first().maxStackPages
        return widgetAreaDao.inTransaction {
            commitEditRows(edit = edit, maxStackPages = maxStackPages).map(::AppWidgetId)
        }
    }

    private suspend fun commitEditRows(edit: WidgetAreaEdit, maxStackPages: Int): List<Int> {
        if (edit.places.any { draft -> !isWithinStackCap(pageCount = draft.widgetIds.size, maxStackPages = maxStackPages) }) {
            throw WidgetStackFullException.forMaxPages(maxStackPages = maxStackPages)
        }
        val removedWidgetIds: List<Long> = edit.removedWidgetIds.toList()
        val freedIds: MutableList<Int> = widgetAreaDao.appWidgetIdsForWidgets(removedWidgetIds).toMutableList()
        if (removedWidgetIds.isNotEmpty()) {
            widgetAreaDao.deleteWidgets(removedWidgetIds)
        }
        val insertedPlaceIds: MutableSet<Long> = mutableSetOf()
        edit.places.forEachIndexed { index, draft: PlaceDraft ->
            val placeId: Long = draft.placeId ?: widgetAreaDao.insertPlace(WidgetPlaceRecord(position = index)).also { inserted ->
                insertedPlaceIds.add(inserted)
            }
            widgetAreaDao.setPlacePosition(placeId = placeId, position = index)
            draft.widgetIds.forEachIndexed { pagePosition, widgetId ->
                widgetAreaDao.moveWidget(widgetId = widgetId, placeId = placeId, position = pagePosition)
            }
        }
        val retainedPlaceIds: Set<Long> = edit.places.mapNotNull { draft -> draft.placeId }.toSet() + insertedPlaceIds
        val orphanPlaceIds: List<Long> = widgetAreaDao.getWidgetAreaPlaceIds().filter { placeId -> placeId !in retainedPlaceIds }
        if (orphanPlaceIds.isNotEmpty()) {
            freedIds += widgetAreaDao.appWidgetIdsForPlaces(orphanPlaceIds)
            widgetAreaDao.deletePlaces(orphanPlaceIds)
        }
        widgetAreaDao.deleteEmptyPlaces()
        widgetAreaDao.getWidgetAreaPlaceIds().forEach { placeId ->
            applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = placeId)
        }
        return freedIds
    }

    private suspend fun deleteDuplicateHeaders(ids: List<Long>) {
        if (ids.isEmpty()) {
            return
        }
        widgetAreaDao.inTransaction {
            val placeIds: List<Long> = widgetAreaDao.getWidgets(ids).map { widget -> widget.placeId }.distinct()
            widgetAreaDao.deleteWidgets(ids)
            placeIds.forEach { placeId -> applyPlaceLayout(dao = widgetAreaDao, host = appWidgetHost, placeId = placeId) }
        }
    }
}
