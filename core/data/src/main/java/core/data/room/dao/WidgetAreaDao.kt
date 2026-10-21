package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import core.data.widgets.HeaderRemoval
import core.data.widgets.PlaceDraft
import core.data.widgets.WidgetAreaEdit
import core.data.widgets.WidgetKind
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetRecord
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WidgetAreaDao {
    @Query("SELECT * FROM widget_places ORDER BY position")
    abstract fun observePlaces(): Flow<List<WidgetPlaceRecord>>

    @Query("SELECT * FROM widgets ORDER BY place_id, page_position")
    abstract fun observeWidgets(): Flow<List<WidgetRecord>>

    @Query(
        """
        SELECT widgets.* FROM widgets
        INNER JOIN widget_places ON widgets.place_id = widget_places.id
        WHERE widgets.kind = 'HEADER'
        ORDER BY widget_places.position, widgets.page_position, widgets.id
        """
    )
    abstract suspend fun getHeaders(): List<WidgetRecord>

    @Query("SELECT position FROM widget_places WHERE id = :placeId")
    abstract suspend fun getPlacePosition(placeId: Long): Int?

    @Query("SELECT * FROM widget_places WHERE id = :placeId")
    abstract suspend fun getPlace(placeId: Long): WidgetPlaceRecord?

    @Query("UPDATE widget_places SET height_dp = :heightDp WHERE id = :placeId")
    abstract suspend fun setPlaceHeight(placeId: Long, heightDp: Int?)

    @Query("UPDATE widget_places SET current_page = :page WHERE id = :placeId")
    abstract suspend fun setCurrentPage(placeId: Long, page: Int)

    @Query("SELECT * FROM widgets WHERE kind = 'APP_WIDGET'")
    abstract suspend fun getAppWidgets(): List<WidgetRecord>

    @Query("SELECT app_widget_id FROM widgets WHERE app_widget_id IS NOT NULL")
    abstract suspend fun allAppWidgetIds(): List<Int>

    @Query("SELECT * FROM widgets WHERE id = :widgetId")
    abstract suspend fun getWidget(widgetId: Long): WidgetRecord?

    @Query("SELECT * FROM widgets WHERE id IN (:ids)")
    abstract suspend fun getWidgets(ids: List<Long>): List<WidgetRecord>

    @Query("SELECT * FROM widgets WHERE place_id = :placeId ORDER BY page_position, id")
    abstract suspend fun getWidgetsForPlace(placeId: Long): List<WidgetRecord>

    @Insert
    abstract suspend fun insertPlace(place: WidgetPlaceRecord): Long

    @Insert
    abstract suspend fun insertWidget(widget: WidgetRecord): Long

    @Query("DELETE FROM widgets WHERE id IN (:ids)")
    abstract suspend fun deleteWidgets(ids: List<Long>)

    @Query("DELETE FROM widget_places WHERE id NOT IN (SELECT DISTINCT place_id FROM widgets)")
    abstract suspend fun deleteEmptyPlaces()

    @Query("DELETE FROM widgets WHERE app_widget_id IN (:ids)")
    abstract suspend fun deleteWidgetsByAppWidgetIds(ids: List<Int>)

    @Transaction
    open suspend fun deleteByAppWidgetIds(ids: List<Int>) {
        if (ids.isEmpty()) {
            return
        }
        deleteWidgetsByAppWidgetIds(ids)
        deleteEmptyPlaces()
    }

    @Query("UPDATE widget_places SET position = position + 1 WHERE position >= :position")
    abstract suspend fun shiftPositionsFrom(position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM widget_places")
    abstract suspend fun getMaxPlacePosition(): Int

    @Transaction
    open suspend fun removeHeader(widgetId: Long? = null): HeaderRemoval? {
        val header: WidgetRecord? = if (widgetId == null) {
            getHeaders().firstOrNull()
        } else {
            getWidget(widgetId)?.takeIf { widget -> widget.kind == WidgetKind.HEADER }
        }
        if (header == null) {
            return null
        }
        val place: WidgetPlaceRecord = getPlace(header.placeId) ?: return null
        deleteWidgets(listOf(header.id))
        normalizePlace(placeId = place.id)
        return HeaderRemoval(position = place.position, placeId = place.id, pagePosition = header.pagePosition)
    }

    @Transaction
    open suspend fun restoreHeader(position: Int, placeId: Long? = null, pagePosition: Int = 0) {
        if (getHeaders().isNotEmpty()) {
            return
        }
        val targetPlace: WidgetPlaceRecord? = placeId?.let { id -> getPlace(id) }
        if (targetPlace != null) {
            val pages: List<WidgetRecord> = getWidgetsForPlace(targetPlace.id)
            val targetPage: Int = pagePosition.coerceIn(0, pages.size)
            pages.filter { page -> page.pagePosition >= targetPage }.forEach { page ->
                moveWidget(widgetId = page.id, placeId = targetPlace.id, position = page.pagePosition + 1)
            }
            insertWidget(
                WidgetRecord(
                    placeId = targetPlace.id,
                    pagePosition = targetPage,
                    kind = WidgetKind.HEADER
                )
            )
            setCurrentPage(placeId = targetPlace.id, page = targetPage)
            return
        }
        val targetPosition: Int = position.coerceIn(0, getMaxPlacePosition() + 1)
        shiftPositionsFrom(targetPosition)
        val newPlaceId: Long = insertPlace(WidgetPlaceRecord(position = targetPosition))
        insertWidget(
            WidgetRecord(
                placeId = newPlaceId,
                pagePosition = 0,
                kind = WidgetKind.HEADER
            )
        )
    }

    @Transaction
    open suspend fun deleteDuplicateHeaders(ids: List<Long>) {
        if (ids.isEmpty()) {
            return
        }
        deleteWidgets(ids)
        deleteEmptyPlaces()
    }

    @Transaction
    open suspend fun addAppWidget(
        appWidgetId: Int,
        provider: WidgetProvider,
        profileSerial: Long,
        belowPlaceId: Long?,
        stackPlaceId: Long?,
        defaultHeightDp: Int
    ): Long {
        if (stackPlaceId != null) {
            val pages: List<WidgetRecord> = getWidgetsForPlace(stackPlaceId)
            val pagePosition: Int = (pages.maxOfOrNull { page -> page.pagePosition } ?: -1) + 1
            val widgetId: Long = insertWidget(
                WidgetRecord(
                    placeId = stackPlaceId,
                    pagePosition = pagePosition,
                    kind = WidgetKind.APP_WIDGET,
                    appWidgetId = appWidgetId,
                    provider = provider.key,
                    profileSerial = profileSerial,
                    defaultHeightDp = defaultHeightDp
                )
            )
            setCurrentPage(placeId = stackPlaceId, page = pagePosition)
            return widgetId
        }
        val belowPosition: Int? = belowPlaceId?.let { placeId -> getPlacePosition(placeId) }
        val position: Int = belowPosition?.plus(1) ?: (getMaxPlacePosition() + 1)
        shiftPositionsFrom(position)
        val placeId: Long = insertPlace(WidgetPlaceRecord(position = position))
        return insertWidget(
            WidgetRecord(
                placeId = placeId,
                pagePosition = 0,
                kind = WidgetKind.APP_WIDGET,
                appWidgetId = appWidgetId,
                provider = provider.key,
                profileSerial = profileSerial,
                defaultHeightDp = defaultHeightDp
            )
        )
    }

    @Transaction
    open suspend fun removeWidget(widgetId: Long): Int? {
        val widget: WidgetRecord = getWidget(widgetId) ?: return null
        deleteWidgets(listOf(widgetId))
        normalizePlace(placeId = widget.placeId)
        return widget.appWidgetId
    }

    @Query("SELECT app_widget_id FROM widgets WHERE place_id IN (:placeIds) AND app_widget_id IS NOT NULL")
    abstract suspend fun appWidgetIdsForPlaces(placeIds: List<Long>): List<Int>

    @Query("SELECT app_widget_id FROM widgets WHERE id IN (:widgetIds) AND app_widget_id IS NOT NULL")
    abstract suspend fun appWidgetIdsForWidgets(widgetIds: List<Long>): List<Int>

    @Query("DELETE FROM widget_places WHERE id IN (:placeIds)")
    abstract suspend fun deletePlaces(placeIds: List<Long>)

    @Query("SELECT id FROM widget_places ORDER BY position")
    abstract suspend fun getPlaceIds(): List<Long>

    @Query("UPDATE widget_places SET position = :position WHERE id = :placeId")
    abstract suspend fun setPlacePosition(placeId: Long, position: Int)

    @Query("UPDATE widgets SET place_id = :placeId, page_position = :position WHERE id = :widgetId")
    abstract suspend fun moveWidget(widgetId: Long, placeId: Long, position: Int)

    @Transaction
    open suspend fun commitEdit(edit: WidgetAreaEdit): List<Int> {
        val removedWidgetIds: List<Long> = edit.removedWidgetIds.toList()
        val freedIds: MutableList<Int> = appWidgetIdsForWidgets(removedWidgetIds).toMutableList()
        if (removedWidgetIds.isNotEmpty()) {
            deleteWidgets(removedWidgetIds)
        }
        val insertedPlaceIds: MutableSet<Long> = mutableSetOf()
        edit.places.forEachIndexed { index, draft: PlaceDraft ->
            val placeId: Long = draft.placeId ?: insertPlace(WidgetPlaceRecord(position = index)).also { inserted ->
                insertedPlaceIds.add(inserted)
            }
            setPlacePosition(placeId = placeId, position = index)
            draft.widgetIds.forEachIndexed { pagePosition, widgetId ->
                moveWidget(widgetId = widgetId, placeId = placeId, position = pagePosition)
            }
        }
        val retainedPlaceIds: Set<Long> = edit.places.mapNotNull { draft -> draft.placeId }.toSet() + insertedPlaceIds
        val orphanPlaceIds: List<Long> = getPlaceIds().filter { placeId -> placeId !in retainedPlaceIds }
        if (orphanPlaceIds.isNotEmpty()) {
            freedIds += appWidgetIdsForPlaces(orphanPlaceIds)
            deletePlaces(orphanPlaceIds)
        }
        deleteEmptyPlaces()
        getPlaceIds().forEach { placeId -> normalizePlace(placeId = placeId) }
        return freedIds
    }

    /** Dissolves a one-page place, clamps a stack's page and keeps a lone Header's height unset. */
    @Transaction
    open suspend fun normalizePlace(placeId: Long) {
        val pages: List<WidgetRecord> = getWidgetsForPlace(placeId)
        if (pages.isEmpty()) {
            deleteEmptyPlaces()
            return
        }
        if (pages.size == 1) {
            setCurrentPage(placeId = placeId, page = 0)
            val widget: WidgetRecord = pages.first()
            if (widget.kind == WidgetKind.HEADER) {
                setPlaceHeight(placeId = placeId, heightDp = null)
            } else {
                val minimum: Int? = widget.defaultHeightDp
                val height: Int? = getPlace(placeId)?.heightDp
                if (minimum != null && height != null) {
                    setPlaceHeight(placeId = placeId, heightDp = maxOf(height, minimum))
                }
            }
            return
        }
        val currentPage: Int = getPlace(placeId)?.currentPage ?: 0
        setCurrentPage(placeId = placeId, page = currentPage.coerceIn(0, pages.lastIndex))
    }
}
