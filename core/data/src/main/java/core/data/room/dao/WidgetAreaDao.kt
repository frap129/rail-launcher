package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import core.data.widgets.WidgetKind
import core.data.widgets.WidgetPlaceRecord
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

    @Insert
    abstract suspend fun insertPlace(place: WidgetPlaceRecord): Long

    @Insert
    abstract suspend fun insertWidget(widget: WidgetRecord): Long

    @Query("DELETE FROM widgets WHERE id IN (:ids)")
    abstract suspend fun deleteWidgets(ids: List<Long>)

    @Query("DELETE FROM widget_places WHERE id NOT IN (SELECT DISTINCT place_id FROM widgets)")
    abstract suspend fun deleteEmptyPlaces()

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
}
