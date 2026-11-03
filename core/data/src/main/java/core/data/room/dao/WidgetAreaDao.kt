package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetRecord
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WidgetAreaDao : TransactionalDao {
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

    @Query("SELECT * FROM widget_places WHERE owner_key = :ownerKey LIMIT 1")
    abstract suspend fun getPlaceForOwner(ownerKey: String): WidgetPlaceRecord?

    @Query("SELECT owner_key FROM widget_places WHERE owner_key IS NOT NULL")
    abstract fun observePopupOwnerKeys(): Flow<List<String>>

    @Query("SELECT id FROM widget_places WHERE owner_key = :ownerKey")
    abstract suspend fun placeIdsForOwner(ownerKey: String): List<Long>

    @Query("DELETE FROM widget_places WHERE owner_key = :ownerKey")
    abstract suspend fun deletePlacesForOwner(ownerKey: String)

    @Query("SELECT * FROM widget_places WHERE position = :position AND owner_key IS NULL LIMIT 1")
    abstract suspend fun getPlaceAtPosition(position: Int): WidgetPlaceRecord?

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

    @Query("UPDATE widget_places SET position = position + 1 WHERE position >= :position AND owner_key IS NULL")
    abstract suspend fun shiftPositionsFrom(position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM widget_places WHERE owner_key IS NULL")
    abstract suspend fun getMaxPlacePosition(): Int

    @Query("UPDATE widgets SET extends_under_rail = :extendsUnderRail WHERE id = :widgetId AND kind = 'APP_WIDGET'")
    abstract suspend fun setExtendsUnderRail(widgetId: Long, extendsUnderRail: Boolean)

    @Query("SELECT app_widget_id FROM widgets WHERE place_id IN (:placeIds) AND app_widget_id IS NOT NULL")
    abstract suspend fun appWidgetIdsForPlaces(placeIds: List<Long>): List<Int>

    @Query("SELECT app_widget_id FROM widgets WHERE id IN (:widgetIds) AND app_widget_id IS NOT NULL")
    abstract suspend fun appWidgetIdsForWidgets(widgetIds: List<Long>): List<Int>

    @Query("DELETE FROM widget_places WHERE id IN (:placeIds)")
    abstract suspend fun deletePlaces(placeIds: List<Long>)

    @Query("SELECT id FROM widget_places ORDER BY position")
    abstract suspend fun getPlaceIds(): List<Long>

    @Query("SELECT id FROM widget_places WHERE owner_key IS NULL ORDER BY position")
    abstract suspend fun getWidgetAreaPlaceIds(): List<Long>

    @Query("UPDATE widget_places SET position = :position WHERE id = :placeId")
    abstract suspend fun setPlacePosition(placeId: Long, position: Int)

    @Query("UPDATE widgets SET place_id = :placeId, page_position = :position WHERE id = :widgetId")
    abstract suspend fun moveWidget(widgetId: Long, placeId: Long, position: Int)
}
