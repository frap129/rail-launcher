package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("UPDATE widget_places SET height_dp = :heightDp WHERE id = :placeId")
    abstract suspend fun setPlaceHeight(placeId: Long, heightDp: Int?)

    @Query("SELECT * FROM widgets WHERE kind = 'APP_WIDGET'")
    abstract suspend fun getAppWidgets(): List<WidgetRecord>

    @Query("SELECT app_widget_id FROM widgets WHERE app_widget_id IS NOT NULL")
    abstract suspend fun allAppWidgetIds(): List<Int>

    @Query("SELECT * FROM widgets WHERE id = :widgetId")
    abstract suspend fun getWidget(widgetId: Long): WidgetRecord?

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
    open suspend fun removeHeader(): Int? {
        val headers: List<WidgetRecord> = getHeaders()
        if (headers.isEmpty()) {
            return null
        }
        val position: Int = getPlacePosition(headers.first().placeId) ?: 0
        deleteWidgets(headers.map { header -> header.id })
        deleteEmptyPlaces()
        return position
    }

    @Transaction
    open suspend fun restoreHeader(position: Int) {
        if (getHeaders().isNotEmpty()) {
            return
        }
        val targetPosition: Int = position.coerceIn(0, getMaxPlacePosition() + 1)
        shiftPositionsFrom(targetPosition)
        val placeId: Long = insertPlace(WidgetPlaceRecord(position = targetPosition))
        insertWidget(
            WidgetRecord(
                placeId = placeId,
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
        defaultHeightDp: Int
    ): Long {
        val belowPosition: Int? = belowPlaceId?.let { placeId -> getPlacePosition(placeId) }
        val position: Int = belowPosition?.plus(1) ?: (getMaxPlacePosition() + 1)
        shiftPositionsFrom(position)
        val placeId: Long = insertPlace(WidgetPlaceRecord(position = position))
        insertWidget(
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
        return placeId
    }

    @Transaction
    open suspend fun removeWidget(widgetId: Long): Int? {
        val widget: WidgetRecord = getWidget(widgetId) ?: return null
        deleteWidgets(listOf(widgetId))
        deleteEmptyPlaces()
        return widget.appWidgetId
    }

    @Query("SELECT app_widget_id FROM widgets WHERE place_id IN (:placeIds) AND app_widget_id IS NOT NULL")
    abstract suspend fun appWidgetIdsForPlaces(placeIds: List<Long>): List<Int>

    @Query("DELETE FROM widget_places WHERE id IN (:placeIds)")
    abstract suspend fun deletePlaces(placeIds: List<Long>)

    @Query("SELECT id FROM widget_places ORDER BY position")
    abstract suspend fun getPlaceIds(): List<Long>

    @Query("UPDATE widget_places SET position = :position WHERE id = :placeId")
    abstract suspend fun setPlacePosition(placeId: Long, position: Int)

    @Transaction
    open suspend fun commitEdit(order: List<Long>, removed: Set<Long>): List<Int> {
        val removedIds: List<Long> = removed.toList()
        val freedIds: List<Int> = if (removedIds.isEmpty()) emptyList() else appWidgetIdsForPlaces(removedIds)
        if (removedIds.isNotEmpty()) {
            deletePlaces(removedIds)
        }
        val remainingIds: List<Long> = getPlaceIds()
        val orderedIds: List<Long> = order.filter { placeId -> placeId in remainingIds } +
            remainingIds.filter { placeId -> placeId !in order }
        orderedIds.forEachIndexed { index, placeId ->
            setPlacePosition(placeId = placeId, position = index)
        }
        return freedIds
    }
}
